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

package bisq.desktop.main.content.authorized_role.mediator.mu_sig.components;

import bisq.common.monetary.Coin;
import bisq.contract.mu_sig.MuSigContract;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.converters.PercentageStringConverter;
import bisq.desktop.components.controls.AutoCompleteComboBox;
import bisq.desktop.components.controls.MaterialTextArea;
import bisq.desktop.components.controls.MaterialTextField;
import bisq.desktop.components.controls.validator.NumberValidator;
import bisq.desktop.components.controls.validator.PercentageValidator;
import bisq.desktop.main.content.authorized_role.mediator.mu_sig.MuSigMediationCaseListItem;
import bisq.i18n.Res;
import bisq.presentation.formatters.AmountFormatter;
import bisq.presentation.formatters.PercentageFormatter;
import bisq.presentation.parser.AmountParser;
import bisq.presentation.parser.PercentageParser;
import bisq.support.mediation.MediationCaseState;
import bisq.support.mediation.MediationPayoutDistributionType;
import bisq.support.mediation.MediationResultReason;
import bisq.support.mediation.mu_sig.MuSigMediationCase;
import bisq.support.mediation.mu_sig.MuSigMediationResult;
import bisq.support.mediation.mu_sig.MuSigMediatorService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Optional;

public class MuSigMediationResultSection {
    private final Controller controller;

    public MuSigMediationResultSection(ServiceProvider serviceProvider) {
        this.controller = new Controller(serviceProvider);
    }

    public VBox getRoot() {
        return controller.view.getRoot();
    }

    public void setMediationCaseListItem(MuSigMediationCaseListItem item) {
        controller.setMediationCaseListItem(item);
    }

    public void closeCase() {
        controller.closeCase();
    }

    public ReadOnlyBooleanProperty hasRequiredSelectionsProperty() {
        return controller.getHasRequiredSelections();
    }

    @Slf4j
    private static class Controller implements bisq.desktop.common.view.Controller {

        @Getter
        private final View view;
        private final Model model;

        private final MuSigMediatorService muSigMediatorService;
        private Optional<MuSigMediationPayoutDistributionCalculator.PayoutContext> payoutContext = Optional.empty();
        private Subscription selectedPayoutDistributionTypePin;
        private Subscription payoutAdjustmentPercentagePin;
        private Subscription buyerPayoutAmountPin;
        private Subscription sellerPayoutAmountPin;

        private Controller(ServiceProvider serviceProvider) {
            model = new Model();
            view = new View(new VBox(), model, this);
            muSigMediatorService = serviceProvider.getSupportService().getMuSigMediatorService();
            model.getHasRequiredSelections().bind(model.getSelectedReason().isNotNull());
        }

        private void setMediationCaseListItem(MuSigMediationCaseListItem item) {
            model.setMuSigMediationCaseListItem(item);
        }

        private ReadOnlyBooleanProperty getHasRequiredSelections() {
            return model.getHasRequiredSelections();
        }

        @Override
        public void onActivate() {
            MuSigMediationCase muSigMediationCase = model.getMuSigMediationCaseListItem().getMuSigMediationCase();
            Optional<MuSigMediationResult> muSigMediationResult = muSigMediationCase.getMuSigMediationResult().get();

            model.getPayoutDistributionTypes().setAll(MediationPayoutDistributionType.values());
            model.getReasons().setAll(MediationResultReason.values());

            model.getSelectedPayoutDistributionType().set(
                    muSigMediationResult.map(MuSigMediationResult::getMediationPayoutDistributionType).orElse(null));

            model.getSelectedReason().set(
                    muSigMediationResult.map(MuSigMediationResult::getMediationResultReason).orElse(null));

            model.getSummaryNotes().set(
                    muSigMediationResult
                            .map(i -> i.getSummaryNotes().orElse("")).orElse(""));
            model.getBuyerPayoutAmountAsCoin().set(
                    muSigMediationResult.map(MuSigMediationResult::getProposedBuyerPayoutAmount).map(Coin::asBtcFromValue).orElse(null));
            model.getSellerPayoutAmountAsCoin().set(
                    muSigMediationResult.map(MuSigMediationResult::getProposedSellerPayoutAmount).map(Coin::asBtcFromValue).orElse(null));
            model.getBuyerPayoutAmount().set(
                    muSigMediationResult.map(MuSigMediationResult::getProposedBuyerPayoutAmount).map(Controller::formatSatsAsBtc).orElse(""));
            model.getSellerPayoutAmount().set(
                    muSigMediationResult.map(MuSigMediationResult::getProposedSellerPayoutAmount).map(Controller::formatSatsAsBtc).orElse(""));
            model.getPayoutAdjustmentPercentageValue().set(
                    muSigMediationResult.flatMap(MuSigMediationResult::getPayoutAdjustmentPercentage).orElse(null));
            model.getPayoutAdjustmentPercentage().set(
                    muSigMediationResult
                            .flatMap(MuSigMediationResult::getPayoutAdjustmentPercentage)
                            .map(value -> PercentageFormatter.formatToPercent(value, 0))
                            .orElse(""));
            model.getShowCompensationOrPenalty().set(false);
            model.getUsePenaltyDescription().set(false);
            model.getPayoutAmountsEditable().set(false);
            payoutContext = resolvePayoutContext();

            selectedPayoutDistributionTypePin = EasyBind.subscribe(model.getSelectedPayoutDistributionType(), this::onPayoutDistributionTypeChanged);
            payoutAdjustmentPercentagePin = EasyBind.subscribe(model.getPayoutAdjustmentPercentage(),
                    this::onPayoutAdjustmentPercentageChanged);
            buyerPayoutAmountPin = EasyBind.subscribe(model.getBuyerPayoutAmount(), this::onBuyerPayoutAmountChanged);
            sellerPayoutAmountPin = EasyBind.subscribe(model.getSellerPayoutAmount(), this::onSellerPayoutAmountChanged);
        }

        @Override
        public void onDeactivate() {
            selectedPayoutDistributionTypePin.unsubscribe();
            payoutAdjustmentPercentagePin.unsubscribe();
            buyerPayoutAmountPin.unsubscribe();
            sellerPayoutAmountPin.unsubscribe();
            payoutContext = Optional.empty();
        }

        void onSelectReason(MediationResultReason reason) {
            model.getSelectedReason().set(reason);
        }

        void onSelectPayoutDistributionType(MediationPayoutDistributionType type) {
            model.getSelectedPayoutDistributionType().set(type);
        }

        void onBuyerPayoutAmountFocusChanged(boolean focused) {
            if (!focused) {
                alignCustomPayoutAmounts(true);
            }
        }

        void onSellerPayoutAmountFocusChanged(boolean focused) {
            if (!focused) {
                alignCustomPayoutAmounts(false);
            }
        }

        private void onPayoutDistributionTypeChanged(MediationPayoutDistributionType payoutDistributionType) {
            if (payoutDistributionType == null) {
                return;
            }

            applyPayoutAmountsForType(payoutDistributionType);
            boolean showCompensationOrPenalty = shouldShowCompensationOrPenalty(payoutDistributionType);
            if (!showCompensationOrPenalty) {
                model.getPayoutAdjustmentPercentage().set("");
            }
            model.getShowCompensationOrPenalty().set(showCompensationOrPenalty);
            model.getUsePenaltyDescription().set(shouldUsePenaltyDescription(payoutDistributionType));
            boolean caseOpen = model.getMuSigMediationCaseListItem().getMuSigMediationCase().getMediationCaseState().get() == MediationCaseState.OPEN;
            model.getPayoutAmountsEditable().set(caseOpen && shouldAllowManualPayoutAmounts(payoutDistributionType));
        }

        private void alignCustomPayoutAmounts(boolean buyerFieldEdited) {
            if (model.getSelectedPayoutDistributionType().get() != MediationPayoutDistributionType.CUSTOM_PAYOUT) {
                return;
            }

            payoutContext
                    .flatMap(context -> MuSigMediationPayoutDistributionCalculator.alignCustomPayout(
                            context,
                            Optional.ofNullable(model.getBuyerPayoutAmountAsCoin().get()).map(Coin::getValue),
                            Optional.ofNullable(model.getSellerPayoutAmountAsCoin().get()).map(Coin::getValue),
                            buyerFieldEdited))
                    .ifPresent(this::setPayoutAmounts);
        }

        private void onPayoutAdjustmentPercentageChanged(String value) {
            model.getPayoutAdjustmentPercentageValue().set(parsePercentage(value).orElse(null));
            MediationPayoutDistributionType payoutDistributionType = model.getSelectedPayoutDistributionType().get();
            if (payoutDistributionType == null) {
                return;
            }
            applyPayoutAmountsForType(payoutDistributionType);
        }

        private void onBuyerPayoutAmountChanged(String value) {
            model.getBuyerPayoutAmountAsCoin().set(parseBtcAmount(value).orElse(null));
        }

        private void onSellerPayoutAmountChanged(String value) {
            model.getSellerPayoutAmountAsCoin().set(parseBtcAmount(value).orElse(null));
        }

        private void applyPayoutAmountsForType(MediationPayoutDistributionType payoutDistributionType) {
            if (payoutDistributionType == MediationPayoutDistributionType.CUSTOM_PAYOUT) {
                return;
            }

            payoutContext
                    .flatMap(context -> MuSigMediationPayoutDistributionCalculator.calculateForType(
                            payoutDistributionType,
                            context,
                            getPayoutAdjustmentPercentageValue()))
                    .ifPresentOrElse(this::setPayoutAmounts, this::clearPayoutAmounts);
        }

        private Optional<Double> getPayoutAdjustmentPercentageValue() {
            return Optional.ofNullable(model.getPayoutAdjustmentPercentageValue().get());
        }

        private static Optional<Double> parsePercentage(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(PercentageParser.parse(value));
            } catch (Exception ignore) {
                return Optional.empty();
            }
        }

        private static Optional<Coin> parseBtcAmount(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(Coin.asBtcFromValue(AmountParser.parse(value, "BTC").getValue()));
            } catch (Exception ignore) {
                return Optional.empty();
            }
        }

        private Optional<MuSigMediationPayoutDistributionCalculator.PayoutContext> resolvePayoutContext() {
            MuSigContract contract = model.getMuSigMediationCaseListItem()
                    .getMuSigMediationCase()
                    .getMuSigMediationRequest()
                    .getContract();
            Optional<MuSigMediationPayoutDistributionCalculator.PayoutContext> optionalPayoutContext =
                    MuSigMediationPayoutDistributionCalculator.createPayoutContext(contract);
            if (optionalPayoutContext.isEmpty()) {
                log.warn("CollateralOption not found for tradeId={}",
                        model.getMuSigMediationCaseListItem().getMuSigMediationCase().getMuSigMediationRequest().getTradeId());
            }
            return optionalPayoutContext;
        }

        private void setPayoutAmounts(MuSigMediationPayoutDistributionCalculator.PayoutAmounts payoutAmounts) {
            model.getBuyerPayoutAmountAsCoin().set(Coin.asBtcFromValue(payoutAmounts.buyerAmountAsSats()));
            model.getSellerPayoutAmountAsCoin().set(Coin.asBtcFromValue(payoutAmounts.sellerAmountAsSats()));
            model.getBuyerPayoutAmount().set(formatSatsAsBtc(payoutAmounts.buyerAmountAsSats()));
            model.getSellerPayoutAmount().set(formatSatsAsBtc(payoutAmounts.sellerAmountAsSats()));
        }

        private void clearPayoutAmounts() {
            model.getBuyerPayoutAmountAsCoin().set(null);
            model.getSellerPayoutAmountAsCoin().set(null);
            model.getPayoutAdjustmentPercentageValue().set(null);
            model.getBuyerPayoutAmount().set("");
            model.getSellerPayoutAmount().set("");
            model.getPayoutAdjustmentPercentage().set("");
        }

        private static boolean shouldShowCompensationOrPenalty(MediationPayoutDistributionType payoutDistributionType) {
            return payoutDistributionType == MediationPayoutDistributionType.BUYER_GETS_TRADE_AMOUNT_PLUS_COMPENSATION ||
                    payoutDistributionType == MediationPayoutDistributionType.SELLER_GETS_TRADE_AMOUNT_PLUS_COMPENSATION ||
                    payoutDistributionType == MediationPayoutDistributionType.BUYER_GETS_TRADE_AMOUNT_MINUS_PENALTY ||
                    payoutDistributionType == MediationPayoutDistributionType.SELLER_GETS_TRADE_AMOUNT_MINUS_PENALTY;
        }

        private static boolean shouldUsePenaltyDescription(MediationPayoutDistributionType payoutDistributionType) {
            return payoutDistributionType == MediationPayoutDistributionType.BUYER_GETS_TRADE_AMOUNT_MINUS_PENALTY ||
                    payoutDistributionType == MediationPayoutDistributionType.SELLER_GETS_TRADE_AMOUNT_MINUS_PENALTY;
        }

        private static boolean shouldAllowManualPayoutAmounts(MediationPayoutDistributionType payoutDistributionType) {
            return payoutDistributionType == MediationPayoutDistributionType.CUSTOM_PAYOUT;
        }

        private static String formatSatsAsBtc(long sats) {
            return AmountFormatter.formatBaseAmount(Coin.asBtcFromValue(sats));
        }

        void closeCase() {
            MuSigMediationCase muSigMediationCase = model.getMuSigMediationCaseListItem().getMuSigMediationCase();

            if (muSigMediationCase.getMediationCaseState().get() == MediationCaseState.OPEN) {
                String summaryNotes = model.getSummaryNotes().get();
                MuSigMediationResult muSigMediationResult = muSigMediatorService.createMuSigMediationResult(
                        model.getSelectedReason().get(),
                        Optional.ofNullable(model.getBuyerPayoutAmountAsCoin().get()).map(Coin::getValue).orElse(0L),
                        Optional.ofNullable(model.getSellerPayoutAmountAsCoin().get()).map(Coin::getValue).orElse(0L),
                        Optional.ofNullable(model.getSelectedPayoutDistributionType().get())
                                .orElse(MediationPayoutDistributionType.CUSTOM_PAYOUT),
                        getPayoutAdjustmentPercentageValue(),
                        summaryNotes.isEmpty() ? Optional.empty() : Optional.of(summaryNotes));
                muSigMediatorService.closeMediationCase(muSigMediationCase, muSigMediationResult);
            } else {
                muSigMediatorService.closeReOpenedMediationCase(muSigMediationCase);
            }
        }
    }

    @Slf4j
    @Getter
    private static class Model implements bisq.desktop.common.view.Model {
        @Setter
        private MuSigMediationCaseListItem muSigMediationCaseListItem;
        private final ObjectProperty<MediationPayoutDistributionType> selectedPayoutDistributionType = new SimpleObjectProperty<>();
        private final ObservableList<MediationPayoutDistributionType> payoutDistributionTypes = FXCollections.observableArrayList();
        private final ObjectProperty<MediationResultReason> selectedReason = new SimpleObjectProperty<>();
        private final ObservableList<MediationResultReason> reasons = FXCollections.observableArrayList();

        private final StringProperty summaryNotes = new SimpleStringProperty("");
        private final StringProperty buyerPayoutAmount = new SimpleStringProperty("");
        private final StringProperty sellerPayoutAmount = new SimpleStringProperty("");
        private final ObjectProperty<Coin> buyerPayoutAmountAsCoin = new SimpleObjectProperty<>();
        private final ObjectProperty<Coin> sellerPayoutAmountAsCoin = new SimpleObjectProperty<>();
        private final StringProperty payoutAdjustmentPercentage = new SimpleStringProperty("");
        private final ObjectProperty<Double> payoutAdjustmentPercentageValue = new SimpleObjectProperty<>();
        private final BooleanProperty showCompensationOrPenalty = new SimpleBooleanProperty(false);
        private final BooleanProperty usePenaltyDescription = new SimpleBooleanProperty(false);
        private final BooleanProperty payoutAmountsEditable = new SimpleBooleanProperty(false);
        private final BooleanProperty hasRequiredSelections = new SimpleBooleanProperty(false);
    }

    @Slf4j
    private static class View extends bisq.desktop.common.view.View<VBox, Model, Controller> {
        private static final double PAYOUT_AMOUNT_PREF_WIDTH = 240;
        private static final double COMPENSATION_OR_PENALTY_PREF_WIDTH = 200;

        private final AutoCompleteComboBox<MediationPayoutDistributionType> payoutDistributionTypeSelection;
        private final MaterialTextField payoutDistributionTypeDisplay;
        private Subscription selectedPayoutDistributionTypePin;

        private final AutoCompleteComboBox<MediationResultReason> reasonSelection;
        private final MaterialTextField reasonDisplay;
        private Subscription selectedReasonPin;
        private Subscription buyerPayoutFocusPin;
        private Subscription sellerPayoutFocusPin;
        private Subscription payoutAdjustmentPercentageDescriptionPin;
        private Subscription payoutAmountsEditablePin;

        private final MaterialTextField buyerPayoutAmount;
        private final MaterialTextField sellerPayoutAmount;
        private final MaterialTextField payoutAdjustmentPercentage;

        private final MaterialTextArea summaryNotes;

        public View(VBox root, Model model, Controller controller) {
            super(root, model, controller);

            // payout types

            payoutDistributionTypeSelection = new AutoCompleteComboBox<>(model.getPayoutDistributionTypes(), Res.get("authorizedRole.mediator.mediationResult.selectPayoutDistributionType"));
            payoutDistributionTypeSelection.setPrefWidth(364);
            payoutDistributionTypeSelection.setConverter(new StringConverter<>() {
                @Override
                public String toString(MediationPayoutDistributionType payoutDistributionType) {
                    return payoutDistributionType != null ? Res.get("authorizedRole.mediator.mediationResult.payoutDistributionType." + payoutDistributionType.name()) : "";
                }

                @Override
                public MediationPayoutDistributionType fromString(String string) {
                    return null;
                }
            });
            payoutDistributionTypeDisplay = new MaterialTextField(Res.get("authorizedRole.mediator.mediationResult.selectPayoutDistributionType"));
            payoutDistributionTypeDisplay.setEditable(false);
            payoutDistributionTypeDisplay.setPrefWidth(364);
            payoutDistributionTypeDisplay.setMaxWidth(Double.MAX_VALUE);

            // reason

            reasonSelection = new AutoCompleteComboBox<>(model.getReasons(), Res.get("authorizedRole.mediator.mediationResult.selectReason"));
            reasonSelection.setPrefWidth(364);
            reasonSelection.setConverter(new StringConverter<>() {
                @Override
                public String toString(MediationResultReason reason) {
                    return reason != null ? Res.get("authorizedRole.mediator.mediationResult.reason." + reason.name()) : "";
                }

                @Override
                public MediationResultReason fromString(String string) {
                    return null;
                }
            });
            reasonDisplay = new MaterialTextField(Res.get("authorizedRole.mediator.mediationResult.selectReason"));
            reasonDisplay.setEditable(false);
            reasonDisplay.setPrefWidth(364);
            reasonDisplay.setMaxWidth(Double.MAX_VALUE);

            // payout details

            buyerPayoutAmount = new MaterialTextField(Res.get("authorizedRole.mediator.mediationResult.buyerPayoutAmount"));
            sellerPayoutAmount = new MaterialTextField(Res.get("authorizedRole.mediator.mediationResult.sellerPayoutAmount"));
            payoutAdjustmentPercentage = new MaterialTextField(Res.get("authorizedRole.mediator.mediationResult.compensationPercentage"));
            buyerPayoutAmount.setValidators(new NumberValidator(Res.get("validation.invalidNumber"), true));
            sellerPayoutAmount.setValidators(new NumberValidator(Res.get("validation.invalidNumber"), true));
            payoutAdjustmentPercentage.setValidators(new PercentageValidator(Res.get("validation.invalidPercentage"), true));
            payoutAdjustmentPercentage.setStringConverter(new PercentageStringConverter());

            buyerPayoutAmount.setPrefWidth(PAYOUT_AMOUNT_PREF_WIDTH);
            sellerPayoutAmount.setPrefWidth(PAYOUT_AMOUNT_PREF_WIDTH);
            payoutAdjustmentPercentage.setPrefWidth(COMPENSATION_OR_PENALTY_PREF_WIDTH);
            buyerPayoutAmount.setMaxWidth(Double.MAX_VALUE);
            sellerPayoutAmount.setMaxWidth(Double.MAX_VALUE);
            payoutAdjustmentPercentage.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(buyerPayoutAmount, Priority.ALWAYS);
            HBox.setHgrow(sellerPayoutAmount, Priority.ALWAYS);
            HBox.setHgrow(payoutAdjustmentPercentage, Priority.ALWAYS);

            HBox payoutAmounts = new HBox(10, payoutAdjustmentPercentage, buyerPayoutAmount, sellerPayoutAmount);

            // summary notes

            summaryNotes = new MaterialTextArea(Res.get("authorizedRole.mediator.mediationResult.summaryNotes"));
            summaryNotes.setFixedHeight(80);

            VBox content = new VBox(10,
                    payoutDistributionTypeSelection,
                    payoutDistributionTypeDisplay,
                    payoutAmounts,
                    reasonSelection,
                    reasonDisplay,
                    summaryNotes);

            content.setAlignment(Pos.CENTER_LEFT);
            root.getChildren().add(content);
        }

        @Override
        protected void onViewAttached() {
            payoutDistributionTypeSelection.setOnChangeConfirmed(e -> {
                if (payoutDistributionTypeSelection.getSelectionModel().getSelectedItem() == null) {
                    payoutDistributionTypeSelection.getSelectionModel().select(model.getSelectedPayoutDistributionType().get());
                    return;
                }
                controller.onSelectPayoutDistributionType(payoutDistributionTypeSelection.getSelectionModel().getSelectedItem());
            });

            selectedPayoutDistributionTypePin = EasyBind.subscribe(model.getSelectedPayoutDistributionType(),
                    payoutDistributionType -> {
                        if (payoutDistributionType != null) {
                            payoutDistributionTypeSelection.getSelectionModel().select(payoutDistributionType);
                        } else {
                            payoutDistributionTypeSelection.getSelectionModel().clearSelection();
                        }
                        updatePayoutDistributionTypeDisplay(payoutDistributionType);
                    });

            reasonSelection.setOnChangeConfirmed(e -> {
                if (reasonSelection.getSelectionModel().getSelectedItem() == null) {
                    reasonSelection.getSelectionModel().select(model.getSelectedReason().get());
                    return;
                }
                controller.onSelectReason(reasonSelection.getSelectionModel().getSelectedItem());
            });

            selectedReasonPin = EasyBind.subscribe(model.getSelectedReason(),
                    reason -> {
                        if (reason != null) {
                            reasonSelection.getSelectionModel().select(reason);
                        } else {
                            reasonSelection.getSelectionModel().clearSelection();
                        }
                        updateReasonDisplay(reason);
                    });
            updatePayoutDistributionTypeDisplay(model.getSelectedPayoutDistributionType().get());
            updateReasonDisplay(model.getSelectedReason().get());

            summaryNotes.textProperty().bindBidirectional(model.getSummaryNotes());
            buyerPayoutAmount.textProperty().bindBidirectional(model.getBuyerPayoutAmount());
            sellerPayoutAmount.textProperty().bindBidirectional(model.getSellerPayoutAmount());
            payoutAdjustmentPercentage.textProperty().bindBidirectional(model.getPayoutAdjustmentPercentage());
            payoutAdjustmentPercentage.visibleProperty().bind(model.getShowCompensationOrPenalty());
            payoutAdjustmentPercentage.managedProperty().bind(model.getShowCompensationOrPenalty());
            applyPayoutAdjustmentPercentageDescription(model.getUsePenaltyDescription().get());
            payoutAdjustmentPercentageDescriptionPin = EasyBind.subscribe(model.getUsePenaltyDescription(),
                    this::applyPayoutAdjustmentPercentageDescription);

            buyerPayoutFocusPin = EasyBind.subscribe(buyerPayoutAmount.textInputFocusedProperty(),
                    controller::onBuyerPayoutAmountFocusChanged);
            sellerPayoutFocusPin = EasyBind.subscribe(sellerPayoutAmount.textInputFocusedProperty(),
                    controller::onSellerPayoutAmountFocusChanged);

            MediationCaseState mediationCaseState = model.muSigMediationCaseListItem.getMuSigMediationCase().getMediationCaseState().get();
            boolean caseOpen = mediationCaseState == MediationCaseState.OPEN;

            payoutDistributionTypeSelection.setVisible(caseOpen);
            payoutDistributionTypeSelection.setManaged(caseOpen);
            reasonSelection.setVisible(caseOpen);
            reasonSelection.setManaged(caseOpen);
            payoutDistributionTypeDisplay.setVisible(!caseOpen);
            payoutDistributionTypeDisplay.setManaged(!caseOpen);
            reasonDisplay.setVisible(!caseOpen);
            reasonDisplay.setManaged(!caseOpen);

            payoutDistributionTypeSelection.setDisable(!caseOpen);
            reasonSelection.setDisable(!caseOpen);
            summaryNotes.setEditable(caseOpen);
            payoutAdjustmentPercentage.setEditable(caseOpen);
            buyerPayoutAmount.setEditable(model.getPayoutAmountsEditable().get());
            sellerPayoutAmount.setEditable(model.getPayoutAmountsEditable().get());
            payoutAmountsEditablePin = EasyBind.subscribe(model.getPayoutAmountsEditable(),
                    editable -> {
                        buyerPayoutAmount.setEditable(editable);
                        sellerPayoutAmount.setEditable(editable);
                    });
        }

        @Override
        protected void onViewDetached() {
            payoutDistributionTypeSelection.setOnChangeConfirmed(null);
            selectedPayoutDistributionTypePin.unsubscribe();
            reasonSelection.setOnChangeConfirmed(null);
            selectedReasonPin.unsubscribe();
            buyerPayoutFocusPin.unsubscribe();
            sellerPayoutFocusPin.unsubscribe();
            payoutAdjustmentPercentageDescriptionPin.unsubscribe();
            payoutAmountsEditablePin.unsubscribe();

            summaryNotes.textProperty().unbindBidirectional(model.getSummaryNotes());
            buyerPayoutAmount.textProperty().unbindBidirectional(model.getBuyerPayoutAmount());
            sellerPayoutAmount.textProperty().unbindBidirectional(model.getSellerPayoutAmount());
            payoutAdjustmentPercentage.textProperty().unbindBidirectional(model.getPayoutAdjustmentPercentage());
            payoutAdjustmentPercentage.visibleProperty().unbind();
            payoutAdjustmentPercentage.managedProperty().unbind();
            payoutDistributionTypeSelection.setVisible(true);
            payoutDistributionTypeSelection.setManaged(true);
            reasonSelection.setVisible(true);
            reasonSelection.setManaged(true);
            payoutDistributionTypeDisplay.setVisible(false);
            payoutDistributionTypeDisplay.setManaged(false);
            reasonDisplay.setVisible(false);
            reasonDisplay.setManaged(false);
        }

        private void applyPayoutAdjustmentPercentageDescription(boolean usePenaltyDescription) {
            if (usePenaltyDescription) {
                payoutAdjustmentPercentage.setDescription(Res.get("authorizedRole.mediator.mediationResult.penaltyPercentage"));
            } else {
                payoutAdjustmentPercentage.setDescription(Res.get("authorizedRole.mediator.mediationResult.compensationPercentage"));
            }
        }

        private void updatePayoutDistributionTypeDisplay(MediationPayoutDistributionType payoutDistributionType) {
            payoutDistributionTypeDisplay.setText(payoutDistributionType == null
                    ? ""
                    : Res.get("authorizedRole.mediator.mediationResult.payoutDistributionType." + payoutDistributionType.name()));
        }

        private void updateReasonDisplay(MediationResultReason reason) {
            reasonDisplay.setText(reason == null
                    ? ""
                    : Res.get("authorizedRole.mediator.mediationResult.reason." + reason.name()));
        }
    }
}
