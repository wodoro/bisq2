/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.content.mu_sig.create_offer.amount_and_price.amount;

import bisq.account.payment_method.PaymentMethod;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.chat.bisq_easy.offerbook.BisqEasyOfferbookChannel;
import bisq.chat.bisq_easy.offerbook.BisqEasyOfferbookChannelService;
import bisq.common.market.Market;
import bisq.common.data.Pair;
import bisq.common.monetary.Fiat;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.PriceQuote;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.Browser;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.utils.KeyHandlerUtil;
import bisq.desktop.common.view.Controller;
import bisq.desktop.components.overlay.Popup;
import bisq.desktop.main.content.bisq_easy.components.amount_selection.AmountSelectionController;
import bisq.desktop.navigation.NavigationTarget;
import bisq.i18n.Res;
import bisq.mu_sig.MuSigTradeAmountLimits;
import bisq.offer.Direction;
import bisq.offer.Offer;
import bisq.offer.amount.OfferAmountUtil;
import bisq.offer.amount.spec.AmountSpecUtil;
import bisq.offer.amount.spec.BaseSideAmountSpec;
import bisq.offer.amount.spec.BaseSideFixedAmountSpec;
import bisq.offer.amount.spec.BaseSideRangeAmountSpec;
import bisq.offer.bisq_easy.BisqEasyOffer;
import bisq.account.payment_method.PaymentMethodSpec;
import bisq.account.payment_method.PaymentMethodSpecUtil;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.MarketPriceSpec;
import bisq.offer.price.spec.PriceSpec;
import bisq.settings.CookieKey;
import bisq.settings.SettingsService;
import bisq.user.identity.UserIdentityService;
import bisq.user.profile.UserProfile;
import bisq.user.profile.UserProfileService;
import bisq.user.reputation.ReputationService;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static bisq.mu_sig.MuSigTradeAmountLimits.DEFAULT_MIN_USD_TRADE_AMOUNT;
import static bisq.mu_sig.MuSigTradeAmountLimits.MAX_USD_TRADE_AMOUNT;
import static bisq.mu_sig.MuSigTradeAmountLimits.MAX_USD_TRADE_AMOUNT_WITHOUT_REPUTATION;
import static bisq.mu_sig.MuSigTradeAmountLimits.Result;
import static bisq.mu_sig.MuSigTradeAmountLimits.getLowestAndHighestAmountInAvailableOffers;
import static bisq.mu_sig.MuSigTradeAmountLimits.withTolerance;
import static bisq.presentation.formatters.AmountFormatter.formatQuoteAmountWithCode;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MuSigCreateOfferAmountController implements Controller {
    private static final PriceSpec MARKET_PRICE_SPEC = new MarketPriceSpec();

    private final MuSigCreateOfferAmountModel model;
    @Getter
    private final MuSigCreateOfferAmountView view;
    private final AmountSelectionController amountSelectionController;
    private final SettingsService settingsService;
    private final MarketPriceService marketPriceService;
    private final BisqEasyOfferbookChannelService bisqEasyOfferbookChannelService;
    private final Region owner;
    private final UserProfileService userProfileService;
    private final ReputationService reputationService;
    private final UserIdentityService userIdentityService;
    private final Consumer<Boolean> navigationButtonsVisibleHandler;
    private final Consumer<NavigationTarget> closeAndNavigateToHandler;
    private Subscription isRangeAmountEnabledPin, maxOrFixAmountCompBaseSideAmountPin, minAmountCompBaseSideAmountPin,
            maxAmountCompQuoteSideAmountPin, minAmountCompQuoteSideAmountPin, priceTooltipPin,
            areBaseAndQuoteCurrenciesInvertedPin;

    public MuSigCreateOfferAmountController(ServiceProvider serviceProvider,
                                            Region owner,
                                            Consumer<Boolean> navigationButtonsVisibleHandler,
                                            Consumer<NavigationTarget> closeAndNavigateToHandler) {
        settingsService = serviceProvider.getSettingsService();
        marketPriceService = serviceProvider.getBondedRolesService().getMarketPriceService();
        userProfileService = serviceProvider.getUserService().getUserProfileService();
        userIdentityService = serviceProvider.getUserService().getUserIdentityService();
        reputationService = serviceProvider.getUserService().getReputationService();
        bisqEasyOfferbookChannelService = serviceProvider.getChatService().getBisqEasyOfferbookChannelService();
        this.owner = owner;
        this.navigationButtonsVisibleHandler = navigationButtonsVisibleHandler;
        this.closeAndNavigateToHandler = closeAndNavigateToHandler;
        model = new MuSigCreateOfferAmountModel();

        amountSelectionController = new AmountSelectionController(serviceProvider);
        view = new MuSigCreateOfferAmountView(model, this, amountSelectionController.getView().getRoot());
    }

    public void setDirection(Direction direction) {
        if (direction == null) {
            return;
        }
        model.setDirection(direction);
        amountSelectionController.setDirection(direction);
    }

    public void setMarket(Market market) {
        if (market == null) {
            return;
        }
        amountSelectionController.setMarket(market);
        model.setMarket(market);
        applyQuoteSideMinMaxRange();
    }

    public void setPaymentMethods(List<PaymentMethod<?>> paymentMethods) {
        if (paymentMethods == null) {
            return;
        }
        model.getPaymentMethods().clear();
        model.getPaymentMethods().addAll(paymentMethods);
    }

    public boolean validate() {
        // No errorMessage set yet... We reset the amount to a valid value in case input is invalid
        if (model.getErrorMessage().get() == null) {
            return true;
        } else {
            new Popup().invalid(model.getErrorMessage().get())
                    .owner(owner)
                    .show();
            return false;
        }
    }

    public void updateBaseSideAmountSpecWithPriceSpec(PriceSpec priceSpec) {
        if (priceSpec == null) {
            return;
        }

        BaseSideAmountSpec amountSpec = model.getBaseSideAmountSpec().get();
        if (amountSpec == null) {
            return;
        }
        Market market = model.getMarket();
        if (market == null) {
            log.warn("market is null at updateBaseSideAmountSpecWithPriceSpec");
            return;
        }
        Optional<PriceQuote> priceQuote = PriceUtil.findQuote(marketPriceService, priceSpec, market);
        if (priceQuote.isEmpty()) {
            log.warn("priceQuote is empty at updateBaseSideAmountSpecWithPriceSpec");
            return;
        }
        model.getPriceQuote().set(priceQuote.get());
        amountSelectionController.setQuote(priceQuote.get());

        OfferAmountUtil.updateBaseSideAmountSpecWithPriceSpec(marketPriceService, amountSpec, priceSpec, market)
                .ifPresent(baseSideAmountSpec -> model.getBaseSideAmountSpec().set(baseSideAmountSpec));
    }

    public void reset() {
        amountSelectionController.reset();
        model.reset();
    }

    public ReadOnlyObjectProperty<BaseSideAmountSpec> getBaseSideAmountSpec() {
        return model.getBaseSideAmountSpec();
    }

    public ReadOnlyBooleanProperty getIsOverlayVisible() {
        return model.getIsOverlayVisible();
    }

    @Override
    public void onActivate() {
        amountSelectionController.setAllowInvertingBaseAndQuoteCurrencies(true);
        amountSelectionController.setBaseAsInputCurrency(true);
        model.getShouldShowWarningIcon().set(false);
        applyQuoteSideMinMaxRange();

        if (model.getPriceQuote().get() == null && amountSelectionController.getQuote().get() != null) {
            model.getPriceQuote().set(amountSelectionController.getQuote().get());
        }

        Boolean cookieValue = settingsService.getCookie().asBoolean(CookieKey.CREATE_MU_SIG_OFFER_IS_MIN_AMOUNT_ENABLED).orElse(false);
        model.getIsRangeAmountEnabled().set(cookieValue);
        model.getShouldShowHowToBuildReputationButton().set(model.getDirection().isSell());

        minAmountCompBaseSideAmountPin = EasyBind.subscribe(amountSelectionController.getMinBaseSideAmount(),
                value -> {
                    if (model.getIsRangeAmountEnabled().get()) {
                        if (value != null && amountSelectionController.getMaxOrFixedBaseSideAmount().get() != null &&
                                value.getValue() > amountSelectionController.getMaxOrFixedBaseSideAmount().get().getValue()) {
                            amountSelectionController.setMaxOrFixedBaseSideAmount(value);
                        }
                    }
                });
        maxOrFixAmountCompBaseSideAmountPin = EasyBind.subscribe(amountSelectionController.getMaxOrFixedBaseSideAmount(),
                value -> {
                    if (value != null &&
                            model.getIsRangeAmountEnabled().get() &&
                            amountSelectionController.getMinBaseSideAmount().get() != null &&
                            value.getValue() < amountSelectionController.getMinBaseSideAmount().get().getValue()) {
                        amountSelectionController.setMinBaseSideAmount(value);
                    }
                });

        minAmountCompQuoteSideAmountPin = EasyBind.subscribe(amountSelectionController.getMinQuoteSideAmount(),
                value -> {
                    if (value != null) {
                        if (model.getIsRangeAmountEnabled().get() &&
                                amountSelectionController.getMaxOrFixedQuoteSideAmount().get() != null &&
                                value.getValue() > amountSelectionController.getMaxOrFixedQuoteSideAmount().get().getValue()) {
                            amountSelectionController.setMaxOrFixedQuoteSideAmount(value);
                        }
                        applyAmountSpec();
                        quoteSideAmountsChanged(false);
                    }
                });
        maxAmountCompQuoteSideAmountPin = EasyBind.subscribe(amountSelectionController.getMaxOrFixedQuoteSideAmount(),
                value -> {
                    if (value != null) {
                        if (model.getIsRangeAmountEnabled().get() &&
                                amountSelectionController.getMinQuoteSideAmount().get() != null &&
                                value.getValue() < amountSelectionController.getMinQuoteSideAmount().get().getValue()) {
                            amountSelectionController.setMinQuoteSideAmount(value);
                        }
                        applyAmountSpec();
                        quoteSideAmountsChanged(true);
                    }
                });

        isRangeAmountEnabledPin = EasyBind.subscribe(model.getIsRangeAmountEnabled(), isRangeAmountEnabled -> {
            applyAmountSpec();
            amountSelectionController.setIsRangeAmountEnabled(isRangeAmountEnabled);
        });
        applyAmountSpec();

        areBaseAndQuoteCurrenciesInvertedPin = EasyBind.subscribe(amountSelectionController.getAreBaseAndQuoteCurrenciesInverted(), areInverted -> {
            String quoteCode = model.getPriceQuote().get().getMarket().getQuoteCurrencyCode();
            model.getPriceTooltip().set(amountSelectionController.isUsingInvertedBaseAndQuoteCurrencies()
                    ? Res.get("bisqEasy.component.amount.quoteSide.tooltip.fiatAmount.selectedPrice", quoteCode)
                    : Res.get("bisqEasy.component.amount.baseSide.tooltip.btcAmount.selectedPrice"));
        });

        priceTooltipPin = EasyBind.subscribe(model.getPriceTooltip(), priceTooltip -> {
            if (priceTooltip != null) {
                amountSelectionController.setTooltip(priceTooltip);
            }
        });
    }

    @Override
    public void onDeactivate() {
        isRangeAmountEnabledPin.unsubscribe();
        maxOrFixAmountCompBaseSideAmountPin.unsubscribe();
        maxAmountCompQuoteSideAmountPin.unsubscribe();
        minAmountCompBaseSideAmountPin.unsubscribe();
        minAmountCompQuoteSideAmountPin.unsubscribe();
        areBaseAndQuoteCurrenciesInvertedPin.unsubscribe();
        priceTooltipPin.unsubscribe();
        navigationButtonsVisibleHandler.accept(true);
        model.getIsOverlayVisible().set(false);
    }

    void onKeyPressedWhileShowingOverlay(KeyEvent keyEvent) {
        KeyHandlerUtil.handleEnterKeyEvent(keyEvent, () -> {
        });
        KeyHandlerUtil.handleEscapeKeyEvent(keyEvent, this::onCloseOverlay);
    }

    void onShowOverlay() {
        if (!model.getIsOverlayVisible().get()) {
            navigationButtonsVisibleHandler.accept(false);
            model.getIsOverlayVisible().set(true);
        }
    }

    void onCloseOverlay() {
        if (model.getIsOverlayVisible().get()) {
            navigationButtonsVisibleHandler.accept(true);
            model.getIsOverlayVisible().set(false);
        }
    }

    void onLearnHowToBuildReputation() {
        closeAndNavigateToHandler.accept(NavigationTarget.BUILD_REPUTATION);
    }

    void onOpenWiki(String url) {
        Browser.open(url);
    }

    void onSelectFixedAmount() {
        updateIsRangeAmountEnabled(false);
    }

    void onSelectRangeAmount() {
        updateIsRangeAmountEnabled(true);
    }

    private void updateIsRangeAmountEnabled(boolean useRangeAmount) {
        model.getIsRangeAmountEnabled().set(useRangeAmount);
        quoteSideAmountsChanged(!useRangeAmount);
        settingsService.setCookie(CookieKey.CREATE_MU_SIG_OFFER_IS_MIN_AMOUNT_ENABLED, useRangeAmount);
    }

    private void applyAmountSpec() {
        Long maxOrFixAmount = getAmountValue(amountSelectionController.getMaxOrFixedBaseSideAmount());
        if (maxOrFixAmount == null) {
            return;
        }

        if (model.getIsRangeAmountEnabled().get()) {
            Long minAmount = getAmountValue(amountSelectionController.getMinBaseSideAmount());
            checkNotNull(minAmount);
            if (maxOrFixAmount.compareTo(minAmount) < 0) {
                amountSelectionController.setMinBaseSideAmount(amountSelectionController.getMaxOrFixedBaseSideAmount().get());
                minAmount = getAmountValue(amountSelectionController.getMinBaseSideAmount());
            }
            applyRangeOrFixedAmountSpec(minAmount, maxOrFixAmount);
        } else {
            applyFixedAmountSpec(maxOrFixAmount);
        }
    }

    private Long getAmountValue(ReadOnlyObjectProperty<Monetary> amountProperty) {
        if (amountProperty.get() == null) {
            return null;
        }
        return amountProperty.get().getValue();
    }

    private void applyRangeOrFixedAmountSpec(Long minAmount, long maxOrFixAmount) {
        if (minAmount != null) {
            if (minAmount.equals(maxOrFixAmount)) {
                applyFixedAmountSpec(maxOrFixAmount);
            } else {
                applyRangeAmountSpec(minAmount, maxOrFixAmount);
            }
        }
    }

    private void applyFixedAmountSpec(long maxOrFixAmount) {
        if (maxOrFixAmount > 0) {
            model.getBaseSideAmountSpec().set(new BaseSideFixedAmountSpec(maxOrFixAmount));
        }
    }

    private void applyRangeAmountSpec(long minAmount, long maxOrFixAmount) {
        if (minAmount > 0 && maxOrFixAmount > 0) {
            model.getBaseSideAmountSpec().set(new BaseSideRangeAmountSpec(minAmount, maxOrFixAmount));
        }
    }

    private Optional<PriceQuote> findBestOfferQuote() {
        // Only used in wizard mode where we do not show min/max amounts
        Optional<BisqEasyOfferbookChannel> optionalChannel = bisqEasyOfferbookChannelService.findChannel(model.getMarket());
        if (optionalChannel.isEmpty() || model.getMarket() == null) {
            return Optional.empty();
        }

        List<BisqEasyOffer> bisqEasyOffers = optionalChannel.get().getChatMessages().stream()
                .filter(chatMessage -> chatMessage.getBisqEasyOffer().isPresent())
                .map(chatMessage -> chatMessage.getBisqEasyOffer().get())
                .filter(this::filterOffers)
                .toList();
        boolean isSellOffer = bisqEasyOffers.stream()
                .map(Offer::getDirection)
                .map(Direction::isSell)
                .findAny()
                .orElse(false);
        Stream<PriceQuote> priceQuoteStream = bisqEasyOffers.stream()
                .map(Offer::getPriceSpec)
                .flatMap(priceSpec -> PriceUtil.findQuote(marketPriceService, priceSpec, model.getMarket()).or(this::getMarketPriceQuote).stream());
        Optional<PriceQuote> bestOffersPrice = isSellOffer
                ? priceQuoteStream.min(Comparator.comparing(PriceQuote::getValue))
                : priceQuoteStream.max(Comparator.comparing(PriceQuote::getValue));
        if (bestOffersPrice.isPresent()) {
            amountSelectionController.setQuote(bestOffersPrice.get());
        } else {
            getMarketPriceQuote().ifPresent(amountSelectionController::setQuote);
        }
        AmountSpecUtil.findQuoteSideFixedAmountFromSpec(model.getBaseSideAmountSpec().get(), model.getMarket().getQuoteCurrencyCode())
                .ifPresent(amount -> UIThread.runOnNextRenderFrame(() -> amountSelectionController.setMaxOrFixedQuoteSideAmount(amount)));

        return bestOffersPrice;
    }

    private Optional<PriceQuote> getMarketPriceQuote() {
        return marketPriceService.findMarketPriceQuote(model.getMarket());
    }

    private void quoteSideAmountsChanged(boolean maxAmountChanged) {
        boolean isSeller = model.getDirection().isSell();
        if (isSeller) {
            return;
        }

        Monetary minQuoteSideAmount = amountSelectionController.getMinQuoteSideAmount().get();
        Monetary maxOrFixedQuoteSideAmount = amountSelectionController.getMaxOrFixedQuoteSideAmount().get();
        // Prevent NPE: nothing to calculate until both ends of the range are set
        if (minQuoteSideAmount == null || maxOrFixedQuoteSideAmount == null) {
            return;
        }

        Market market = model.getMarket();
        long requiredReputationScoreForMaxOrFixedAmount = MuSigTradeAmountLimits.findRequiredReputationScoreByFiatAmount(marketPriceService, market, maxOrFixedQuoteSideAmount).orElse(0L);
        long requiredReputationScoreForMinAmount = MuSigTradeAmountLimits.findRequiredReputationScoreByFiatAmount(marketPriceService, market, minQuoteSideAmount).orElse(0L);
        long numPotentialTakersForMaxOrFixedAmount = reputationService.getScoreByUserProfileId().entrySet().stream()
                .filter(e -> userIdentityService.findUserIdentity(e.getKey()).isEmpty())
                .filter(e -> withTolerance(e.getValue()) >= requiredReputationScoreForMaxOrFixedAmount)
                .count();
        long numPotentialTakersForMinAmount = reputationService.getScoreByUserProfileId().entrySet().stream()
                .filter(e -> userIdentityService.findUserIdentity(e.getKey()).isEmpty())
                .filter(e -> withTolerance(e.getValue()) >= requiredReputationScoreForMinAmount)
                .count();
        String formattedMaxOrFixedAmount = formatQuoteAmountWithCode(maxOrFixedQuoteSideAmount);
        model.getShouldShowWarningIcon().set(false);
        model.getLearnMoreVisible().set(true);
        if (model.getIsRangeAmountEnabled().get()) {
            // At range amount we use the min amount
            String numSellers = Res.getPluralization("bisqEasy.tradeWizard.amount.buyer.numSellers", numPotentialTakersForMinAmount);
            model.getAmountLimitInfo().set(Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo", numSellers));
            model.setAmountLimitInfoLink(Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.learnMore"));

            String formattedMinAmount = formatQuoteAmountWithCode(minQuoteSideAmount);
            String firstPart = Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.firstPart", formattedMinAmount, requiredReputationScoreForMinAmount);
            String secondPart;
            if (numPotentialTakersForMinAmount == 0) {
                model.getShouldShowWarningIcon().set(true);
                secondPart = Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.secondPart.noSellers");
            } else {
                secondPart = numPotentialTakersForMinAmount == 1
                        ? Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.secondPart.singular", numSellers)
                        : Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.secondPart.plural", numSellers);
            }
            model.getAmountLimitInfoOverlayInfo().set(firstPart + "\n\n" + secondPart + "\n\n");
        } else {
            // Fixed amount
            String numSellers = Res.getPluralization("bisqEasy.tradeWizard.amount.buyer.numSellers", numPotentialTakersForMaxOrFixedAmount);
            model.getAmountLimitInfo().set(Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo", numSellers));
            model.setAmountLimitInfoLink(Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.learnMore"));
            String firstPart = Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.firstPart", formattedMaxOrFixedAmount, requiredReputationScoreForMaxOrFixedAmount);
            String secondPart;
            if (numPotentialTakersForMaxOrFixedAmount == 0) {
                model.getShouldShowWarningIcon().set(true);
                secondPart = Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.secondPart.noSellers");
            } else {
                secondPart = numPotentialTakersForMaxOrFixedAmount == 1
                        ? Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.secondPart.singular", numSellers)
                        : Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.info.secondPart.plural", numSellers);
            }
            model.getAmountLimitInfoOverlayInfo().set(firstPart + "\n\n" + secondPart + "\n\n");
        }
    }

    private void applyQuoteSideMinMaxRange() {
        Market market = model.getMarket();
        Monetary maxRangeValue = MuSigTradeAmountLimits.usdToFiat(marketPriceService, market, MAX_USD_TRADE_AMOUNT)
                .orElseThrow().round(0);
        Monetary minRangeValue = MuSigTradeAmountLimits.usdToFiat(marketPriceService, market, DEFAULT_MIN_USD_TRADE_AMOUNT)
                .orElseThrow().round(0);

        applyMaxAmountBasedOnReputation();

        Fiat defaultUsdAmount = MAX_USD_TRADE_AMOUNT_WITHOUT_REPUTATION.multiply(2);
        Monetary defaultFiatAmount = MuSigTradeAmountLimits.usdToFiat(marketPriceService, market, defaultUsdAmount)
                .orElseThrow().round(0);
        boolean isBuyer = model.getDirection().isBuy();
        Monetary reputationBasedMaxAmount = model.getReputationBasedMaxAmount().round(0);
        amountSelectionController.setMaxAllowedLimitation(maxRangeValue);
        model.getLearnMoreVisible().set(true);
        if (isBuyer) {
            model.getShouldShowAmountLimitInfo().set(true);
            amountSelectionController.setMinMaxRange(minRangeValue, maxRangeValue);
        } else {
            boolean hasNotReachedAmountLimit = reputationBasedMaxAmount.getValue() < maxRangeValue.getValue();
            model.getShouldShowAmountLimitInfo().set(hasNotReachedAmountLimit);
            if (reputationBasedMaxAmount.getValue() < minRangeValue.getValue()) {
                minRangeValue = reputationBasedMaxAmount;
            }
            if (reputationBasedMaxAmount.getValue() < maxRangeValue.getValue()) {
                maxRangeValue = reputationBasedMaxAmount;
            }
            amountSelectionController.setMinMaxRange(minRangeValue, maxRangeValue);
        }

        if (isBuyer) {
            // Buyer case
            model.setLinkToWikiText(Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.overlay.linkToWikiText"));
            model.setAmountLimitInfoLink(Res.get("bisqEasy.tradeWizard.amount.buyer.limitInfo.learnMore"));

            long highestScore = reputationService.getScoreByUserProfileId().entrySet().stream()
                    .filter(e -> userIdentityService.findUserIdentity(e.getKey()).isEmpty())
                    .mapToLong(Map.Entry::getValue)
                    .max()
                    .orElse(0L);
            Monetary highestPossibleAmountFromSellers = MuSigTradeAmountLimits.getReputationBasedQuoteSideAmount(marketPriceService, market, highestScore)
                    .orElse(Fiat.from(0, market.getQuoteCurrencyCode()));
            amountSelectionController.setRightMarkerQuoteSideValue(highestPossibleAmountFromSellers);
            if (amountSelectionController.getMaxOrFixedQuoteSideAmount().get() == null) {
                amountSelectionController.setMaxOrFixedQuoteSideAmount(defaultFiatAmount);
            }
        } else {
            // Seller case
            model.setLinkToWikiText(Res.get("bisqEasy.tradeWizard.amount.seller.limitInfo.overlay.linkToWikiText"));
            model.setAmountLimitInfoLink(Res.get("bisqEasy.tradeWizard.amount.seller.limitInfo.link"));
            Monetary reputationBasedQuoteSideAmount = model.getReputationBasedMaxAmount();
            long myReputationScore = model.getMyReputationScore();
            String formattedAmount = formatQuoteAmountWithCode(reputationBasedQuoteSideAmount.round(0));
            model.getAmountLimitInfo().set(Res.get("bisqEasy.tradeWizard.amount.seller.limitInfo", formattedAmount));
            model.getAmountLimitInfoOverlayInfo().set(Res.get("bisqEasy.tradeWizard.amount.seller.limitInfo.overlay", myReputationScore, formattedAmount));
            amountSelectionController.setRightMarkerQuoteSideValue(reputationBasedQuoteSideAmount);
            applyReputationBasedQuoteSideAmount();
        }
    }

    private void applyMaxAmountBasedOnReputation() {
        String myProfileId = userIdentityService.getSelectedUserIdentity().getUserProfile().getId();
        long myReputationScore = reputationService.getReputationScore(myProfileId).getTotalScore();
        model.setMyReputationScore(myReputationScore);
        model.setReputationBasedMaxAmount(MuSigTradeAmountLimits.getReputationBasedQuoteSideAmount(marketPriceService, model.getMarket(), myReputationScore)
                .orElse(Fiat.fromValue(0, model.getMarket().getQuoteCurrencyCode()))
        );
    }

    private void applyLowestAndHighestAmountInAvailableOffers() {
        Monetary selectedAmount = amountSelectionController.getMaxOrFixedQuoteSideAmount().get();
        if (selectedAmount == null) {
            return;
        }

        if (model.getMarket() == null) {
            log.warn("Market not yet set – skipping offer amount range calculation");
            return;
        }

        Pair<Optional<Monetary>, Optional<Monetary>> availableOfferAmountRange = getLowestAndHighestAmountInAvailableOffers(bisqEasyOfferbookChannelService,
                reputationService,
                userIdentityService,
                userProfileService,
                marketPriceService,
                model.getMarket(),
                model.getDirection());
        amountSelectionController.setLeftMarkerQuoteSideValue(availableOfferAmountRange.getFirst().orElse(null));
        amountSelectionController.setRightMarkerQuoteSideValue(availableOfferAmountRange.getSecond().orElse(null));

        boolean rangePresent = availableOfferAmountRange.getFirst().isPresent() && availableOfferAmountRange.getSecond().isPresent();
        model.getLearnMoreVisible().set(false);
        if (rangePresent) {
            Monetary lower = availableOfferAmountRange.getFirst().get();
            Monetary higher = availableOfferAmountRange.getSecond().get();
            boolean offersAvailable = selectedAmount.isGreaterThanOrEqual(lower) && selectedAmount.isLessThanOrEqual(higher);
            model.getShouldShowWarningIcon().set(!offersAvailable);

            if (offersAvailable) {
                model.getAmountLimitInfo().set(Res.get("bisqEasy.tradeWizard.amount.buyer.offersAvailable"));
            } else {
                model.getAmountLimitInfo().set(Res.get("bisqEasy.tradeWizard.amount.buyer.noOffersAvailable"));
            }
        }
        model.getShouldShowAmountLimitInfo().set(rangePresent);
    }

    private void applyReputationBasedQuoteSideAmount() {
        amountSelectionController.setMaxOrFixedQuoteSideAmount(amountSelectionController.getRightMarkerQuoteSideValue().round(0));
    }

    private long getNumMatchingOffers(Monetary quoteSideAmount) {
        return bisqEasyOfferbookChannelService.findChannel(model.getMarket()).orElseThrow().getChatMessages().stream()
                .filter(chatMessage -> chatMessage.getBisqEasyOffer().isPresent())
                .map(chatMessage -> chatMessage.getBisqEasyOffer().get())
                .filter(offer -> {
                    if (!isValidDirection(offer)) {
                        return false;
                    }
                    if (!isValidMarket(offer)) {
                        return false;
                    }
                    if (!isValidMakerProfile(offer)) {
                        return false;
                    }
                    if (!isValidAmountRange(offer)) {
                        return false;
                    }

                    if (!isValidAmountLimit(offer, quoteSideAmount)) {
                        return false;
                    }

                    return true;
                })
                .count();
    }

    /* --------------------------------------------------------------------- */
    // Filter
    /* --------------------------------------------------------------------- */

    private boolean isValidDirection(BisqEasyOffer peersOffer) {
        return peersOffer.getTakersDirection().equals(model.getDirection());
    }

    private boolean isValidMarket(BisqEasyOffer peersOffer) {
        return peersOffer.getMarket().equals(model.getMarket());
    }

    private boolean isValidMakerProfile(BisqEasyOffer peersOffer) {
        Optional<UserProfile> optionalMakersUserProfile = userProfileService.findUserProfile(peersOffer.getMakersUserProfileId());
        if (optionalMakersUserProfile.isEmpty()) {
            return false;
        }
        UserProfile makersUserProfile = optionalMakersUserProfile.get();
        if (userProfileService.isChatUserIgnored(makersUserProfile)) {
            return false;
        }
        if (userIdentityService.getUserIdentities().stream()
                .map(userIdentity -> userIdentity.getUserProfile().getId())
                .anyMatch(userProfileId -> userProfileId.equals(optionalMakersUserProfile.get().getId()))) {
            return false;
        }

        return true;
    }

    private boolean isValidPaymentMethods(BisqEasyOffer peersOffer) {
        List<String> fiatPaymentMethodNames = PaymentMethodSpecUtil.getPaymentMethodNames(peersOffer.getQuoteSidePaymentMethodSpecs());
        List<PaymentMethodSpec<?>> quoteSidePaymentMethodSpecs = PaymentMethodSpecUtil.createPaymentMethodSpecs(model.getPaymentMethods(), model.getMarket().getQuoteCurrencyCode());
        List<String> quoteSidePaymentMethodNames = PaymentMethodSpecUtil.getPaymentMethodNames(quoteSidePaymentMethodSpecs);
        if (quoteSidePaymentMethodNames.stream().noneMatch(fiatPaymentMethodNames::contains)) {
            return false;
        }

        return true;
    }

    private boolean isValidAmountRange(BisqEasyOffer peersOffer) {
        Optional<Monetary> myQuoteSideMinOrFixedAmount = OfferAmountUtil.findQuoteSideMinOrFixedAmount(marketPriceService, model.getBaseSideAmountSpec().get(), MARKET_PRICE_SPEC, model.getMarket());
        Optional<Monetary> peersQuoteSideMaxOrFixedAmount = OfferAmountUtil.findQuoteSideMaxOrFixedAmount(marketPriceService, peersOffer);
        if (myQuoteSideMinOrFixedAmount.isEmpty() || peersQuoteSideMaxOrFixedAmount.isEmpty()) {
            return false;
        }
        if (myQuoteSideMinOrFixedAmount.get().round(0).getValue() > peersQuoteSideMaxOrFixedAmount.get().round(0).getValue()) {
            return false;
        }

        Optional<Monetary> myQuoteSideMaxOrFixedAmount = OfferAmountUtil.findQuoteSideMaxOrFixedAmount(marketPriceService, model.getBaseSideAmountSpec().get(), MARKET_PRICE_SPEC, model.getMarket());
        Optional<Monetary> peersQuoteSideMinOrFixedAmount = OfferAmountUtil.findQuoteSideMinOrFixedAmount(marketPriceService, peersOffer);
        if (myQuoteSideMaxOrFixedAmount.isEmpty() || peersQuoteSideMinOrFixedAmount.isEmpty()) {
            return false;
        }
        if (myQuoteSideMaxOrFixedAmount.get().round(0).getValue() < peersQuoteSideMinOrFixedAmount.get().round(0).getValue()) {
            return false;
        }

        return true;
    }

    private boolean isValidAmountLimit(BisqEasyOffer peersOffer, Monetary quoteSideAmount) {
        Optional<Result> result = MuSigTradeAmountLimits.checkOfferAmountLimitForGivenAmount(reputationService,
                userIdentityService,
                userProfileService,
                marketPriceService,
                model.getMarket(),
                quoteSideAmount,
                peersOffer);
        if (!result.map(Result::isValid).orElse(false)) {
            return false;
        }
        return true;
    }


    private boolean isValidAmountLimit(BisqEasyOffer peersOffer) {
        if (!MuSigTradeAmountLimits.checkOfferAmountLimitForMaxOrFixedAmount(reputationService,
                        userIdentityService,
                        userProfileService,
                        marketPriceService,
                        peersOffer)
                .map(Result::isValid)
                .orElse(false)) {
            return false;
        }

        return true;
    }

    // Used for finding best price quote of available matching offers
    private boolean filterOffers(BisqEasyOffer peersOffer) {
        if (!isValidDirection(peersOffer)) {
            return false;
        }
        if (!isValidMarket(peersOffer)) {
            return false;
        }
        if (!isValidPaymentMethods(peersOffer)) {
            return false;
        }
        if (!isValidMakerProfile(peersOffer)) {
            return false;
        }
        if (!isValidAmountRange(peersOffer)) {
            return false;
        }
        if (!isValidAmountLimit(peersOffer)) {
            return false;
        }
        return true;
    }
}
