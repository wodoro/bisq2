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

import bisq.account.protocol_type.ProtocolType;
import bisq.common.proto.ProtobufUtils;
import bisq.i18n.Res;

public enum BankAccountType implements ProtocolType {
    CHECKINGS,
    SAVINGS;

    @Override
    public bisq.account.protobuf.BankAccountType toProtoEnum() {
        return bisq.account.protobuf.BankAccountType.valueOf(getProtobufEnumPrefix() + name());
    }

    public static BankAccountType fromProto(bisq.account.protobuf.BankAccountType proto) {
        return ProtobufUtils.enumFromProto(BankAccountType.class, proto.name(), CHECKINGS);
    }

    @Override
    public String toString() {
        return Res.get("paymentAccounts.bank.bankAccountType." + name());
    }
}

  