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

package bisq.account.payment_method;

/**
 * Base class for PaymentMethods based on national currencies like FiatPaymentMethod or StablecoinPaymentMethod
 */
public abstract class NationalCurrencyPaymentMethod<R extends NationalCurrencyPaymentRail> extends PaymentMethod<R> {
    protected NationalCurrencyPaymentMethod(R paymentRail) {
        super(paymentRail);
    }

    protected NationalCurrencyPaymentMethod(String customName) {
        super(customName);
    }
}
