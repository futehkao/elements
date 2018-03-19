/*
 * Copyright 2017 Futeh Kao
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
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@SuppressWarnings("squid:S2278")
class MasterCardARQC {

    private static final String ALGORITHM = "DESede";

    private String pan;
    private String cardSequence = "00";
    private String diversification;
    private AtallaSimulator simulator;
    private AKB imk;
    private SecretKey iccMasterKey;
    private SecretKey leftSessionKey;
    private SecretKey rightSessionKey;
    private SecretKey sessionKey;
    private String sessionKeyCheckDigit;
    private String arqc;
    private String arc;
    private String failureCode;
    private String dataBlock;
    private int derivationType = 0;  // 0 - legacy master, 2 - emv 4.1
    private String computedARQC;

    MasterCardARQC(AtallaSimulator simulator) {
        this.simulator = simulator;
    }

    MasterCardARQC derivationType(int derivationType) {
        this.derivationType = derivationType;
        return this;
    }

    MasterCardARQC pan(String pan) {
        this.pan = pan;
        return this;
    }

    MasterCardARQC cardSequence(String cardSequence) {
        this.cardSequence = cardSequence;
        if (cardSequence.length() == 0)
            this.cardSequence = "00";
        return this;
    }

    MasterCardARQC diversification(String diversification) {
        this.diversification = diversification;
        return this;
    }

    MasterCardARQC imk(AKB imk) {
        this.imk = imk;
        return this;
    }

    MasterCardARQC arqc(String arqc) {
        this.arqc = arqc;
        return this;
    }

    MasterCardARQC dataBlock(String dataBlock) {
        this.dataBlock = dataBlock;
        return this;
    }

    MasterCardARQC arc(String arc) {
        this.arc = arc;
        return this;
    }

    MasterCardARQC failureCode(String failureCode) {
        this.failureCode = failureCode;
        return this;
    }

    public String getComputedARQC() {
        return computedARQC;
    }

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
            byte[] iccMasterKeyBytes = new byte[leftMK.length + rightMK.length];
            System.arraycopy(leftMK, 0, iccMasterKeyBytes, 0, leftMK.length);
            System.arraycopy(rightMK, 0, iccMasterKeyBytes, leftMK.length, rightMK.length);
            iccMasterKey = new SecretKeySpec(AKB.normalizeKey(iccMasterKeyBytes), ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new CommandException(3, e);
        }

        // now we need to get the session key which uses diversification data field 5
        deriveSessionKey();

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

    private void deriveSessionKey() throws CommandException {
        byte[] diverse = Hex.toBytes(diversification);
        byte[] leftDiverse = Arrays.copyOf(diverse, diverse.length);
        leftDiverse[2] = (byte) 0xF0;
        byte[] rightDiverse = Arrays.copyOf(diverse, diverse.length);
        rightDiverse[2] = (byte) 0x0F;

        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, iccMasterKey);
            byte[] leftSK = cipher.doFinal(leftDiverse);
            cipher.init(Cipher.ENCRYPT_MODE, iccMasterKey);
            byte[] rightSK = cipher.doFinal(rightDiverse);

            leftSessionKey = new SecretKeySpec(AKB.normalizeKey(leftSK), ALGORITHM);
            rightSessionKey = new SecretKeySpec(AKB.normalizeKey(rightSK), ALGORITHM);

            byte[] sessionKeyBytes = new byte[leftSK.length + rightSK.length];
            System.arraycopy(leftSK, 0, sessionKeyBytes, 0, leftSK.length);
            System.arraycopy(rightSK, 0, sessionKeyBytes, leftSK.length, rightSK.length);
            sessionKey = new SecretKeySpec(AKB.normalizeKey(sessionKeyBytes), ALGORITHM);
            sessionKeyCheckDigit = AKB.calculateCheckDigits(sessionKeyBytes);

        } catch (GeneralSecurityException e) {
            throw new CommandException(5, e);
        }
    }

    private String computeARQC() throws CommandException {
        byte[] bytes = Hex.toBytes(dataBlock);
        if (bytes.length % 8 != 0 || bytes.length == 0)
            throw new CommandException(7, new IllegalArgumentException("data block is not multiple of 8 bytes"));

        Cipher cipher;
        try {
            cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, leftSessionKey);

            byte[] input = new byte[8];
            System.arraycopy(bytes, 0, input, 0, 8);
            for (int i = 1; i < bytes.length / 8; i++) {
                input = cipher.doFinal(input);
                for (int j = 0; j < 8; j++)
                    input[j] = (byte) (input[j] ^ bytes[i * 8 + j]);
            }
            input = cipher.doFinal(input);

            cipher.init(Cipher.DECRYPT_MODE, rightSessionKey);
            input = cipher.doFinal(input);
            cipher.init(Cipher.ENCRYPT_MODE, leftSessionKey);
            input = cipher.doFinal(input);
            return Hex.toString(input);
        } catch (GeneralSecurityException e) {
            throw new CommandException(7, e);
        }
    }

    private String computeARPC(String code) throws CommandException {
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
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            if (derivationType == 0) {
                cipher.init(Cipher.ENCRYPT_MODE, iccMasterKey);
            } else if (derivationType == 2) {
                cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
            } else {
                throw new CommandException(1, new IllegalArgumentException("Invalid derivation type"));
            }
            return Hex.toString(cipher.doFinal(bytes));
        } catch(GeneralSecurityException e) {
            throw new CommandException(6, e);
        }
    }

    public static void main(String ... args) throws Exception {
        AtallaSimulator simulator = new AtallaSimulator();
        MasterCardARQC mc = new MasterCardARQC(simulator);
        mc.imk(simulator.asAKB(simulator.IMK_ARQC))
                .pan("9901234567890123")
                .cardSequence("45")
                .diversification("1234567890123456")
                .arqc("922F3E83125EB46B")
                .dataBlock("0123456789ABCDEF0123456789ABCDEF")
                .arc("0000")
                .process();
    }
}
