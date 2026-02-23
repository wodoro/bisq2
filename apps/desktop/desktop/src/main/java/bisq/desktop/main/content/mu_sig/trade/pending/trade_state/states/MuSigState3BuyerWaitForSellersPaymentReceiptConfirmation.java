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

package bisq.desktop.main.content.mu_sig.trade.pending.trade_state.states;

import bisq.chat.mu_sig.open_trades.MuSigOpenTradeChannel;
import bisq.desktop.ServiceProvider;
import bisq.desktop.components.controls.WrappingText;
import bisq.desktop.main.content.mu_sig.trade.components.MuSigWaitingAnimation;
import bisq.desktop.main.content.mu_sig.trade.components.MuSigWaitingState;
import bisq.i18n.Res;
import bisq.trade.mu_sig.MuSigTrade;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MuSigState3BuyerWaitForSellersPaymentReceiptConfirmation extends MuSigBaseState {
    private final Controller controller;

    public MuSigState3BuyerWaitForSellersPaymentReceiptConfirmation(ServiceProvider serviceProvider,
                                                                    MuSigTrade trade,
                                                                    MuSigOpenTradeChannel channel) {
        controller = new Controller(serviceProvider, trade, channel);
    }

    public VBox getRoot() {
        return controller.getView().getRoot();
    }

    private static class Controller extends MuSigBaseState.Controller<Model, View> {
        private Controller(ServiceProvider serviceProvider, MuSigTrade trade, MuSigOpenTradeChannel channel) {
            super(serviceProvider, trade, channel);
        }

        @Override
        protected Model createModel(MuSigTrade trade, MuSigOpenTradeChannel channel) {
            return new Model(trade, channel);
        }

        @Override
        protected View createView() {
            return new View(model, this);
        }

        @Override
        public void onActivate() {
            super.onActivate();

            if (model.getMarket().isBaseCurrencyBitcoin()) {
                model.setHeadline(Res.get("muSig.trade.state.phase3.headline.fiat"));
                model.setInfo(Res.get("muSig.trade.state.phase3.info.fiat", model.getFormattedNonBtcAmount()));
            } else {
                model.setHeadline(Res.get("muSig.trade.state.phase3.headline.crypto"));
                model.setInfo(Res.get("muSig.trade.state.phase3.info.crypto", model.getFormattedNonBtcAmount()));
            }
        }

        @Override
        public void onDeactivate() {
            super.onDeactivate();
        }
    }

    @Getter
    private static class Model extends MuSigBaseState.Model {
        @Setter
        private String headline;
        @Setter
        private String info;

        protected Model(MuSigTrade trade, MuSigOpenTradeChannel channel) {
            super(trade, channel);
        }
    }

    public static class View extends MuSigBaseState.View<Model, Controller> {
        private final WrappingText headline, info;
        private final MuSigWaitingAnimation waitingAnimation;

        private View(Model model, Controller controller) {
            super(model, controller);

            waitingAnimation = new MuSigWaitingAnimation(MuSigWaitingState.PAYMENT_CONFIRMATION);
            headline = MuSigFormUtils.getHeadline();
            info = MuSigFormUtils.getInfo();
            HBox waitingInfo = createWaitingInfo(waitingAnimation, headline, info);
            root.getChildren().add(waitingInfo);
        }

        @Override
        protected void onViewAttached() {
            super.onViewAttached();

            headline.setText(model.getHeadline());
            info.setText(model.getInfo());

            waitingAnimation.play();
        }

        @Override
        protected void onViewDetached() {
            super.onViewDetached();

            waitingAnimation.stop();
        }
    }
}
