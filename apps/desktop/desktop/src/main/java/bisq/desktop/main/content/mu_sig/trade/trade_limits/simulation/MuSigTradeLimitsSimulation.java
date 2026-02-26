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

package bisq.desktop.main.content.mu_sig.trade.trade_limits.simulation;

import bisq.account.payment_method.PaymentRail;
import bisq.account.payment_method.fiat.FiatPaymentMethodChargebackRisk;
import bisq.account.payment_method.fiat.FiatPaymentRail;
import bisq.common.util.MathUtils;
import bisq.desktop.common.converters.LongStringConverter;
import bisq.desktop.common.utils.GridPaneUtil;
import bisq.desktop.components.controls.AutoCompleteComboBox;
import bisq.desktop.components.controls.MaterialTextField;
import bisq.desktop.components.controls.Switch;
import bisq.i18n.Res;
import bisq.mu_sig.MuSigTradeAmountLimits;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MuSigTradeLimitsSimulation {

    private final Controller controller;

    public MuSigTradeLimitsSimulation() {
        controller = new Controller();
    }

    public GridPane getViewRoot() {
        return controller.getView().getRoot();
    }

    @Slf4j
    public static class Controller implements bisq.desktop.common.view.Controller {
        @Getter
        private final View view;
        private final Model model;
        private final Set<Subscription> pins = new HashSet<>();

        private Controller() {
            List<FiatPaymentRail> fiatPaymentRails = Arrays.stream(FiatPaymentRail.values())
                    .filter(e -> e != FiatPaymentRail.CUSTOM)
                    .sorted(Comparator.comparing(PaymentRail::getShortDisplayString))
                    .toList();
            model = new Model(fiatPaymentRails);
            view = new View(model, this);

            applySelectFiatPaymentRail(FiatPaymentRail.SEPA);
        }

        @Override
        public void onActivate() {
            pins.add(EasyBind.subscribe(model.getAccountAge(), value -> updateLimits()));
            pins.add(EasyBind.subscribe(model.getReputationScore(), value -> updateLimits()));
        }

        @Override
        public void onDeactivate() {
            pins.forEach(Subscription::unsubscribe);
            pins.clear();
        }

        void onHasBisq1AccountAgeWitnessChanged(boolean selected) {
            model.hasBisq1AccountAgeWitness.set(selected);
            updateLimits();
        }

        void onSelectFiatPaymentRail(FiatPaymentRail selectedItem) {
            if (selectedItem != null) {
                applySelectFiatPaymentRail(selectedItem);
            }
        }

        private void applySelectFiatPaymentRail(FiatPaymentRail fiatPaymentRail) {
            model.getSelectedFiatPaymentRail().set(fiatPaymentRail);
            String maxTradeLimit = MuSigTradeAmountLimits.getFormattedMaxTradeLimit(fiatPaymentRail);
            model.getFiatPaymentRailMaxLimit().set(Res.get("muSig.trade.limits.simulation.fiatRail.maxLimit", maxTradeLimit));
            updateLimits();
        }

        private void updateLimits() {
            FiatPaymentRail fiatPaymentRail = model.getSelectedFiatPaymentRail().get();
            if (fiatPaymentRail == null) {
                return;
            }

            double maxTradeLimit = MuSigTradeAmountLimits.getMaxTradeLimit(fiatPaymentRail).getValue() / 10000d;
            // We use 10% of max limit as default limit
            double defaultTradeLimit = maxTradeLimit * 0.1;    // 250-1000

            // AccountAgeWitnessScore
            double accountAgeWitnessScoreTradeLimitBoost = model.getHasBisq1AccountAgeWitness().get()
                    ? defaultTradeLimit * 9
                    : 0;

            // AccountAge
            double minAccountAge = 15;
            double maxAccountAge = 60;
            double accountAge = MathUtils.roundDouble(model.getAccountAge().get(), 9);
            double accountAgeWeight = normalize(accountAge, minAccountAge, maxAccountAge);
            double accountAgeMultiplier = accountAge >= minAccountAge ? 0.25 + accountAgeWeight * 0.75 : 0;
            double tradeLimitBoostFromAccountAge = defaultTradeLimit * accountAgeMultiplier * 4; // 250-1000

            // ReputationScore
            double minReputationScore = 0;
            double maxReputationScore = 200000; // 2000 BSQ -> 4000 USD
            double reputationScore = model.getReputationScore().get();
            double reputationScoreWeight = normalize(reputationScore, minReputationScore, maxReputationScore);
            double tradeLimitBoostFromReputation = defaultTradeLimit * reputationScoreWeight * 6;

            // AccountAge combined with ReputationScore
            double accountAgeBasedReputationScoreMultiplier = accountAgeMultiplier * reputationScoreWeight;
            double accountAgeBasedReputationScoreTradeLimitBoost = defaultTradeLimit * accountAgeBasedReputationScoreMultiplier * 4;

            double tradeLimit = defaultTradeLimit +
                    tradeLimitBoostFromAccountAge +
                    tradeLimitBoostFromReputation +
                    accountAgeWitnessScoreTradeLimitBoost +
                    accountAgeBasedReputationScoreTradeLimitBoost;
            tradeLimit = Math.min(maxTradeLimit, tradeLimit);

            // Rate is trade limit / 1000
            double derivedFromTradeLimit = MathUtils.roundDouble(tradeLimit / 1000, 0, RoundingMode.FLOOR);
            double rateLimit = Math.max(1, derivedFromTradeLimit);

            model.getTradeLimit().set(formatTradeLimit(tradeLimit));
            model.getRateLimit().set(formatRateLimit(rateLimit));
        }

        private static double normalize(double value, double minValue, double maxValue) {
            double range = maxValue - minValue;
            double offset = value - minValue;
            return range > 0 ? MathUtils.bounded(0, 1, offset / range) : 0;
        }

        private static String formatTradeLimit(double tradeLimit) {
            return MathUtils.roundDoubleToInt(tradeLimit) + " USD";
        }

        private static String formatRateLimit(Double rateLimit) {
            int rateLimitRounded = MathUtils.roundDoubleToInt(rateLimit);
            if (rateLimitRounded >= 5) {
                return "No rate limit";
            } else if (rateLimitRounded == 1) {
                return rateLimitRounded + " trade per day";
            } else {
                return rateLimitRounded + " trades per day";
            }
        }
    }

    @Getter
    private static class Model implements bisq.desktop.common.view.Model {
        private final ObservableList<FiatPaymentRail> fiatPaymentRails = FXCollections.observableArrayList();
        private final ObjectProperty<FiatPaymentRail> selectedFiatPaymentRail = new SimpleObjectProperty<>();
        private final StringProperty fiatPaymentRailMaxLimit = new SimpleStringProperty();
        private final ObjectProperty<FiatPaymentMethodChargebackRisk> chargebackRisk = new SimpleObjectProperty<>(FiatPaymentMethodChargebackRisk.MODERATE);
        private final DoubleProperty reputationScore = new SimpleDoubleProperty();
        private final BooleanProperty hasBisq1AccountAgeWitness = new SimpleBooleanProperty();
        private final DoubleProperty accountAge = new SimpleDoubleProperty();
        private final StringProperty tradeLimit = new SimpleStringProperty();
        private final StringProperty rateLimit = new SimpleStringProperty();

        public Model(List<FiatPaymentRail> fiatPaymentRails) {
            this.fiatPaymentRails.addAll(fiatPaymentRails);
        }
    }

    private static class View extends bisq.desktop.common.view.View<GridPane, Model, Controller> {
        private final MaterialTextField tradeLimit, rateLimit;
        private final SliderWithValue accountAge, reputationScore;
        private final AutoCompleteComboBox<FiatPaymentRail> paymentRailSelection;
        private final Label paymentRailMaxLimit;
        private final Switch hasBisq1AccountAgeWitness;

        private View(Model model, Controller controller) {
            super(new GridPane(15, 15), model, controller);

            GridPaneUtil.setGridPaneTwoColumnsConstraints(root);

            int rowIndex = 0;

            paymentRailSelection = new AutoCompleteComboBox<>(model.getFiatPaymentRails(), Res.get("muSig.trade.limits.simulation.fiatRail"));
            paymentRailSelection.setMaxWidth(Double.MAX_VALUE);
            paymentRailSelection.setConverter(new StringConverter<>() {
                @Override
                public String toString(FiatPaymentRail fiatPaymentRail) {
                    return fiatPaymentRail != null ? fiatPaymentRail.getShortDisplayString() : "";
                }

                @Override
                public FiatPaymentRail fromString(String string) {
                    return null;
                }
            });

            paymentRailMaxLimit = new Label();

            VBox paymentRailSelectionBox = new VBox(10, paymentRailSelection, paymentRailMaxLimit);

            GridPane.setHgrow(paymentRailSelectionBox, Priority.ALWAYS);
            root.add(paymentRailSelectionBox, 0, rowIndex);

            hasBisq1AccountAgeWitness = new Switch(Res.get("muSig.trade.limits.simulation.hasBisq1AccountAgeWitness"));

            GridPane.setHgrow(hasBisq1AccountAgeWitness, Priority.ALWAYS);
            GridPane.setMargin(hasBisq1AccountAgeWitness, new Insets(0, 0, 37.5, 0));
            root.add(hasBisq1AccountAgeWitness, 1, rowIndex);

            // Row 2
            rowIndex++;
            accountAge = new SliderWithValue(0, 0, 60,
                    "muSig.trade.limits.simulation.accountAge",
                    value -> String.valueOf(MathUtils.roundDouble(value, 0)),
                    new LongStringConverter(0),
                    1);
            GridPane.setHgrow(accountAge.getViewRoot(), Priority.ALWAYS);
            root.add(accountAge.getViewRoot(), 0, rowIndex);

            reputationScore = new SliderWithValue(0, 0, 200000,
                    "muSig.trade.limits.simulation.reputationScore",
                    value -> String.valueOf(MathUtils.roundDouble(value, 0)),
                    new LongStringConverter(0),
                    1);
            GridPane.setHgrow(reputationScore.getViewRoot(), Priority.ALWAYS);
            root.add(reputationScore.getViewRoot(), 1, rowIndex);


            tradeLimit = new MaterialTextField(Res.get("muSig.trade.limits.simulation.tradeLimit"));
            tradeLimit.setEditable(false);
            rateLimit = new MaterialTextField(Res.get("muSig.trade.limits.simulation.rateLimit"));
            rateLimit.setEditable(false);

            // Row 3
            rowIndex++;
            root.add(tradeLimit, 0, rowIndex, 1, 1);
            root.add(rateLimit, 1, rowIndex, 1, 1);
        }

        @Override
        protected void onViewAttached() {
            model.getAccountAge().bind(accountAge.valueProperty());
            model.getReputationScore().bind(reputationScore.valueProperty());

            paymentRailMaxLimit.textProperty().bind(model.getFiatPaymentRailMaxLimit());
            tradeLimit.textProperty().bind(model.getTradeLimit());
            rateLimit.textProperty().bind(model.getRateLimit());

            paymentRailSelection.getSelectionModel().select(model.getSelectedFiatPaymentRail().get());
            paymentRailSelection.setOnChangeConfirmed(e -> {
                if (paymentRailSelection.getSelectionModel().getSelectedItem() == null) {
                    paymentRailSelection.getSelectionModel().select(model.getSelectedFiatPaymentRail().get());
                    return;
                }
                controller.onSelectFiatPaymentRail(paymentRailSelection.getSelectionModel().getSelectedItem());
            });

            hasBisq1AccountAgeWitness.setOnAction(e -> controller.onHasBisq1AccountAgeWitnessChanged(hasBisq1AccountAgeWitness.isSelected()));
        }

        @Override
        protected void onViewDetached() {
            model.getAccountAge().unbind();
            model.getReputationScore().unbind();

            paymentRailMaxLimit.textProperty().unbind();
            tradeLimit.textProperty().unbind();
            rateLimit.textProperty().unbind();

            paymentRailSelection.setOnChangeConfirmed(null);
            hasBisq1AccountAgeWitness.setOnAction(null);
        }
    }
}
