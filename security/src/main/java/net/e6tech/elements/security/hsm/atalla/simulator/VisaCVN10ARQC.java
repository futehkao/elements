/*
 * Copyright 2019 Shell Xu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.security.hsm.atalla.simulator;

import net.e6tech.elements.security.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

@SuppressWarnings({"squid:S2278", "squid:S1192"})
/*
    For VISA, CVN (Cryptogram Version Number) = 10, similar to MasterCard
    - VSDC_MDK (IMK_ARQC) is used for ARQC and ARPC calculation
    - Diversification is not needed
 */
class VisaCVN10ARQC extends MasterCardARQC {

    public VisaCVN10ARQC(AtallaSimulator simulator) {
        super(simulator);
    }

    @SuppressWarnings("squid:S3776")
    @Override
    protected String process() throws CommandException {
        if (arc == null || arc.length() == 0)
            throw new CommandException(8, new IllegalArgumentException("ARC is null"));
        String mk = pan + cardSequence;
        int pad = 16 - mk.length();
        if (pad > 0) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < pad; i++)
                builder.append("0");
            mk = builder.toString() + mk;
        }
        if (mk.length() > 16)
            mk = mk.substring(mk.length() - 16); // right most 16 digits
        byte[] mkBytes = Hex.toBytes(mk);

        // mk_l and mk_r are the ICC master key
        byte[] leftMK;
        byte[] rightMK;
        try {
            leftMK = simulator.encrypt(imk, mkBytes);
            rightMK = simulator.encrypt(imk, Hex.invert(mkBytes));
            // left session key and right session key are the diversified key of ICC VSDC_MDK (IMK_ARQC) key with PAN and Pan sequence
            leftSessionKey = new SecretKeySpec(AKB.normalizeKey(leftMK), ALGORITHM);
            rightSessionKey = new SecretKeySpec(AKB.normalizeKey(rightMK), ALGORITHM);

            byte[] iccMasterKeyBytes = new byte[leftMK.length + rightMK.length];
            System.arraycopy(leftMK, 0, iccMasterKeyBytes, 0, leftMK.length);
            System.arraycopy(rightMK, 0, iccMasterKeyBytes, leftMK.length, rightMK.length);
            sessionKey = new SecretKeySpec(AKB.normalizeKey(iccMasterKeyBytes), ALGORITHM);
            sessionKeyCheckDigit = AKB.calculateCheckDigits(iccMasterKeyBytes);
        } catch (GeneralSecurityException e) {
            throw new CommandException(3, e);
        }

        // verify ARQC by creating it
        computedARQC = computeARQC();
        String arpc;
        boolean verified;
        if (!computedARQC.equals(arqc)) {
            if (failureCode == null || failureCode.length() == 0)
                return "450##" + sessionKeyCheckDigit + "#"+ imk.getCheckDigits() ; // indicator is not needed for no failure code.
            // need to return false and compute arpc with failure code
            arpc = computeARPC(failureCode);
            verified = false;
        } else {
            arpc = computeARPC(arc);
            verified = true;
        }

        String result = "450#" + arpc + "#" + sessionKeyCheckDigit + "#"+ imk.getCheckDigits();
        if (failureCode != null && failureCode.length() > 0)
            result += (verified) ? "#Y" : "#N";
        return result;
    }

    @Override
    protected String computeARPC(String code) throws CommandException {
        byte[] bytes = Hex.toBytes(arqc);
        byte[] codeBytes = Hex.toBytes(code);
        byte[] arcBytes = new byte[bytes.length];
        System.arraycopy(codeBytes, 0, arcBytes, 0, codeBytes.length);
        for (int i = codeBytes.length; i < arcBytes.length; i++)
            arcBytes[i] = (byte) 0;
        for (int i = 0; i < arcBytes.length; i++) {
            bytes[i] = (byte)(bytes[i] ^ arcBytes[i]);
        }
        try {
            // use left session key and right session key, which are the diversified key of ICC VSDC_MDK (IMK_ARQC) key with PAN and Pan sequence
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, leftSessionKey);
            bytes = cipher.doFinal(bytes);
            cipher.init(Cipher.DECRYPT_MODE, rightSessionKey);
            bytes = cipher.doFinal(bytes);
            cipher.init(Cipher.ENCRYPT_MODE, leftSessionKey);
            bytes = cipher.doFinal(bytes);
            String cryptogram = Hex.toString(cipher.doFinal(bytes));
            return cryptogram;
        } catch(GeneralSecurityException e) {
            throw new CommandException(6, e);
        }
    }

}
