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

package bisq.bisq_easy;

import bisq.account.payment_method.BitcoinPaymentMethod;
import bisq.account.payment_method.fiat.FiatPaymentMethod;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.market.Market;
import bisq.common.util.StringUtils;
import bisq.i18n.Res;
import bisq.offer.Direction;
import bisq.offer.amount.OfferAmountFormatter;
import bisq.offer.amount.spec.AmountSpec;
import bisq.offer.amount.spec.RangeAmountSpec;
import bisq.offer.bisq_easy.BisqEasyOffer;
import bisq.account.payment_method.PaymentMethodSpecFormatter;
import bisq.offer.price.spec.PriceSpec;
import bisq.offer.price.spec.PriceSpecFormatter;
import bisq.user.identity.UserIdentityService;

import java.util.List;

public class BisqEasyServiceUtil {

    public static boolean isMaker(UserIdentityService userIdentityService, BisqEasyOffer bisqEasyOffer) {
        return bisqEasyOffer.isMyOffer(userIdentityService.getMyUserProfileIds());
    }

    public static String createBasicOfferBookMessage(MarketPriceService marketPriceService,
                                                     Market market,
                                                     String bitcoinPaymentMethodNames,
                                                     String fiatPaymentMethodNames,
                                                     AmountSpec amountSpec,
                                                     PriceSpec priceSpec) {
        String priceInfo = String.format("%s %s", Res.get("bisqEasy.tradeWizard.review.chatMessage.price"), PriceSpecFormatter.getFormattedPriceSpec(priceSpec));
        boolean hasAmountRange = amountSpec instanceof RangeAmountSpec;
        String quoteAmountAsString = OfferAmountFormatter.formatQuoteAmount(marketPriceService, amountSpec, priceSpec, market, hasAmountRange, true);
        return Res.get("bisqEasy.tradeWizard.review.chatMessage.offerDetails", quoteAmountAsString, bitcoinPaymentMethodNames, fiatPaymentMethodNames, priceInfo);
    }

    public static String createOfferBookMessageFromPeerPerspective(String messageOwnerNickName,
                                                                   MarketPriceService marketPriceService,
                                                                   Direction direction,
                                                                   Market market,
                                                                   List<BitcoinPaymentMethod> bitcoinPaymentMethods,
                                                                   List<FiatPaymentMethod> fiatPaymentMethods,
                                                                   AmountSpec amountSpec,
                                                                   PriceSpec priceSpec) {
        String bitcoinPaymentMethodNames = PaymentMethodSpecFormatter.fromPaymentMethods(bitcoinPaymentMethods);
        String fiatPaymentMethodNames = PaymentMethodSpecFormatter.fromPaymentMethods(fiatPaymentMethods);
        return createOfferBookMessageFromPeerPerspective(messageOwnerNickName,
                marketPriceService,
                direction,
                market,
                bitcoinPaymentMethodNames,
                fiatPaymentMethodNames,
                amountSpec,
                priceSpec);
    }

    public static String createOfferBookMessageFromPeerPerspective(String messageOwnerNickName,
                                                                   MarketPriceService marketPriceService,
                                                                   Direction direction,
                                                                   Market market,
                                                                   String bitcoinPaymentMethodNames,
                                                                   String fiatPaymentMethodNames,
                                                                   AmountSpec amountSpec,
                                                                   PriceSpec priceSpec) {
        String ownerNickName = StringUtils.truncate(messageOwnerNickName, 28);
        String priceInfo = String.format("%s %s", Res.get("bisqEasy.tradeWizard.review.chatMessage.price"), PriceSpecFormatter.getFormattedPriceSpec(priceSpec));
        boolean hasAmountRange = amountSpec instanceof RangeAmountSpec;
        String quoteAmountAsString = OfferAmountFormatter.formatQuoteAmount(marketPriceService, amountSpec, priceSpec, market, hasAmountRange, true);
        return buildOfferBookMessage(ownerNickName, direction, quoteAmountAsString, bitcoinPaymentMethodNames, fiatPaymentMethodNames, priceInfo);
    }

    private static String buildOfferBookMessage(String messageOwnerNickName,
                                                Direction direction,
                                                String quoteAmount,
                                                String bitcoinPaymentMethodNames,
                                                String fiatPaymentMethodNames,
                                                String price) {
        return direction == Direction.BUY
                ? Res.get("bisqEasy.tradeWizard.review.chatMessage.peerMessage.sell",
                messageOwnerNickName, quoteAmount, bitcoinPaymentMethodNames, fiatPaymentMethodNames, price)
                : Res.get("bisqEasy.tradeWizard.review.chatMessage.peerMessage.buy",
                messageOwnerNickName, quoteAmount, bitcoinPaymentMethodNames, fiatPaymentMethodNames, price);
    }
}
