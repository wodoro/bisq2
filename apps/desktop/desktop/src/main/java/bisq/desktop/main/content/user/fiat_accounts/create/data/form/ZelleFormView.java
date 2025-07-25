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

package bisq.desktop.main.content.user.fiat_accounts.create.data.form;

import bisq.common.util.StringUtils;
import bisq.desktop.components.containers.Spacer;
import bisq.desktop.components.controls.MaterialTextField;
import bisq.i18n.Res;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

@Slf4j
public class ZelleFormView extends FormView<ZelleFormModel, ZelleFormController> {
    private final MaterialTextField holderName, emailOrMobileNr;
    private Subscription runValidationPin;

    public ZelleFormView(ZelleFormModel model, ZelleFormController controller) {
        super(model, controller);

        holderName = new MaterialTextField(Res.get("paymentAccounts.holderName"),
                Res.get("paymentAccounts.createAccount.prompt", StringUtils.unCapitalize(Res.get("paymentAccounts.holderName"))));
        holderName.setValidators(model.getHolderNameValidator());
        holderName.setMaxWidth(Double.MAX_VALUE);


        emailOrMobileNr = new MaterialTextField(Res.get("paymentAccounts.emailOrMobileNr"),
                Res.get("paymentAccounts.createAccount.prompt", StringUtils.unCapitalize(Res.get("paymentAccounts.emailOrMobileNr"))));
        emailOrMobileNr.setValidators(model.getEmailOrPhoneNumberValidator());
        emailOrMobileNr.setMaxWidth(Double.MAX_VALUE);

        root.getChildren().addAll(holderName, emailOrMobileNr, Spacer.height(100));
    }

    @Override
    protected void onViewAttached() {
        if (StringUtils.isNotEmpty(model.getHolderName().get())) {
            holderName.setText(model.getHolderName().get());
            holderName.validate();
        }
        if (StringUtils.isNotEmpty(model.getEmailOrMobileNr().get())) {
            emailOrMobileNr.setText(model.getEmailOrMobileNr().get());
            emailOrMobileNr.validate();
        }

        holderName.textProperty().bindBidirectional(model.getHolderName());
        emailOrMobileNr.textProperty().bindBidirectional(model.getEmailOrMobileNr());

        runValidationPin = EasyBind.subscribe(model.getRunValidation(), runValidation -> {
            if (runValidation) {
                holderName.validate();
                emailOrMobileNr.validate();
                controller.onValidationDone();
            }
        });
    }

    @Override
    protected void onViewDetached() {
        holderName.resetValidation();
        emailOrMobileNr.resetValidation();

        holderName.textProperty().unbindBidirectional(model.getHolderName());
        emailOrMobileNr.textProperty().unbindBidirectional(model.getEmailOrMobileNr());

        runValidationPin.unsubscribe();
    }
}