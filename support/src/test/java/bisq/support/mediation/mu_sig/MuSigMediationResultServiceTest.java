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

package bisq.support.mediation.mu_sig;

import bisq.contract.ContractService;
import bisq.contract.mu_sig.MuSigContract;
import bisq.security.keys.KeyGeneration;
import bisq.support.mediation.MediationPayoutDistributionType;
import bisq.support.mediation.MediationResultReason;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MuSigMediationResultServiceTest {
    @Test
    void verifyReturnsTrueForMatchingSignature() throws GeneralSecurityException {
        KeyPair mediatorKeyPair = KeyGeneration.generateDefaultEcKeyPair();
        MuSigMediationResult mediationResult = createMediationResult();
        byte[] mediationResultSignature = MuSigMediationResultService.signMediationResult(mediationResult, mediatorKeyPair);

        assertThat(MuSigMediationResultService.verifyMediationResult(mediationResult,
                mediationResultSignature,
                createContract("contract-a"),
                mediatorKeyPair.getPublic())).isTrue();
    }

    @Test
    void verifyReturnsFalseForTamperedResult() throws GeneralSecurityException {
        KeyPair mediatorKeyPair = KeyGeneration.generateDefaultEcKeyPair();
        byte[] mediationResultSignature = MuSigMediationResultService.signMediationResult(createMediationResult(), mediatorKeyPair);
        MuSigMediationResult tamperedResult = new MuSigMediationResult(
                ContractService.getContractHash(createContract("contract-a")),
                MediationResultReason.BUG,
                MediationPayoutDistributionType.CUSTOM_PAYOUT,
                Optional.of(11L),
                Optional.of(22L),
                Optional.empty(),
                Optional.of("tampered"));

        assertThat(MuSigMediationResultService.verifyMediationResult(tamperedResult,
                mediationResultSignature,
                mediatorKeyPair.getPublic())).isFalse();
    }

    @Test
    void verifyThrowsForMismatchedContractHash() throws GeneralSecurityException {
        KeyPair mediatorKeyPair = KeyGeneration.generateDefaultEcKeyPair();
        MuSigMediationResult mediationResult = createMediationResult();
        byte[] mediationResultSignature = MuSigMediationResultService.signMediationResult(mediationResult, mediatorKeyPair);

        assertThatThrownBy(() -> MuSigMediationResultService.verifyMediationResult(mediationResult,
                mediationResultSignature,
                createContract("contract-b"),
                mediatorKeyPair.getPublic()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Contract hash");
    }

    private MuSigMediationResult createMediationResult() {
        return new MuSigMediationResult(
                ContractService.getContractHash(createContract("contract-a")),
                MediationResultReason.BUG,
                MediationPayoutDistributionType.CUSTOM_PAYOUT,
                Optional.of(10L),
                Optional.of(20L),
                Optional.empty(),
                Optional.of("summary"));
    }

    private MuSigContract createContract(String id) {
        MuSigContract contract = mock(MuSigContract.class);
        when(contract.serializeForHash()).thenReturn(createContractPayload(id));
        return contract;
    }

    private byte[] createContractPayload(String id) {
        return id.getBytes(StandardCharsets.UTF_8);
    }
}
