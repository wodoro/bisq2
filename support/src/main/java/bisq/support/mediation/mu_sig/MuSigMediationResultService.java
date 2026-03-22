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

import bisq.contract.mu_sig.MuSigContract;
import bisq.contract.ContractService;
import bisq.security.DigestUtil;
import bisq.security.SignatureUtil;
import bisq.common.validation.NetworkDataValidation;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

public final class MuSigMediationResultService {
    private MuSigMediationResultService() {
    }

    public static byte[] signMediationResult(MuSigMediationResult muSigMediationResult,
                                             KeyPair keyPair)
            throws GeneralSecurityException {
        byte[] mediationResultHash = getMediationResultHash(muSigMediationResult);
        return SignatureUtil.sign(mediationResultHash, keyPair.getPrivate());
    }

    public static boolean verifyMediationResult(MuSigMediationResult mediationResult,
                                                byte[] mediationResultSignature,
                                                PublicKey publicKey) throws GeneralSecurityException {
        NetworkDataValidation.validateECSignature(mediationResultSignature);
        return SignatureUtil.verify(getMediationResultHash(mediationResult), mediationResultSignature, publicKey);
    }

    public static boolean verifyMediationResult(MuSigMediationResult mediationResult,
                                                byte[] mediationResultSignature,
                                                MuSigContract contract,
                                                PublicKey publicKey) throws GeneralSecurityException {
        checkArgument(Arrays.equals(mediationResult.getContractHash(), ContractService.getContractHash(contract)),
                "Contract hash from MuSigMediationResult does not match the given contract");
        return verifyMediationResult(mediationResult, mediationResultSignature, publicKey);
    }

    private static byte[] getMediationResultHash(MuSigMediationResult mediationResult) {
        return DigestUtil.hash(mediationResult.serializeForHash());
    }
}
