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

package bisq.account.accounts.fiat;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
@ToString
@EqualsAndHashCode(callSuper = true)
public final class Pin4Account extends CountryBasedAccount<Pin4AccountPayload> {
    public Pin4Account(String id, long creationDate, String accountName, Pin4AccountPayload accountPayload) {
        super(id, creationDate, accountName, accountPayload);
    }

    @Override
    protected bisq.account.protobuf.CountryBasedAccount.Builder getCountryBasedAccountBuilder(boolean serializeForHash) {
        return super.getCountryBasedAccountBuilder(serializeForHash).setPin4Account(
                toPin4AccountProto(serializeForHash));
    }

    private bisq.account.protobuf.Pin4Account toPin4AccountProto(boolean serializeForHash) {
        return resolveBuilder(getPin4AccountBuilder(serializeForHash), serializeForHash).build();
    }

    private bisq.account.protobuf.Pin4Account.Builder getPin4AccountBuilder(boolean serializeForHash) {
        return bisq.account.protobuf.Pin4Account.newBuilder();
    }

    public static Pin4Account fromProto(bisq.account.protobuf.Account proto) {
        return new Pin4Account(proto.getId(),
                proto.getCreationDate(),
                proto.getAccountName(),
                Pin4AccountPayload.fromProto(proto.getAccountPayload()));
    }
}
