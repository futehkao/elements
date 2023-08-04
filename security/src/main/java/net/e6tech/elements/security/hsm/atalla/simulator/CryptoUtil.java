/*
 * Copyright 2020 Episode Six
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
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * @author Shell Xu
 */
public class CryptoUtil {
    protected static final String ALGORITHM_DES_EDE = "DESede";
    protected static final String DES_EDE_ECB_NO_PADDING = "DESede/ECB/NoPadding";
    protected static final String DES_EDE_CBC_NO_PADDING = "DESede/CBC/NoPadding";

    private CryptoUtil() {
    }

    public static byte[] encrypt(byte[] keyBytes, byte[] clearText) throws GeneralSecurityException {
        return encrypt(createSecretKey(keyBytes), clearText);
    }

    public static byte[] encrypt(SecretKey secretKey, byte[] clearText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(DES_EDE_ECB_NO_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(clearText);
    }

    public static String iso9797Alg3Mac(byte[] macKeyBytes, byte[] text, int resultLength) throws GeneralSecurityException {
        if (text.length % 8 != 0)
            throw new GeneralSecurityException("data block size must be multiple of 8");

        byte[] leftMacKeyBytes = new byte[8];
        byte[] rightMacKeyBytes = new byte[8];
        System.arraycopy(macKeyBytes, 0, leftMacKeyBytes, 0, leftMacKeyBytes.length);
        System.arraycopy(macKeyBytes, leftMacKeyBytes.length, rightMacKeyBytes, 0, rightMacKeyBytes.length);
        SecretKey macKeyLeft = new SecretKeySpec(leftMacKeyBytes, "DES");
        SecretKey macKeyRight = new SecretKeySpec(rightMacKeyBytes, "DES");
        Cipher cipherDesCbs = Cipher.getInstance("DES/CBC/NoPadding");
        Cipher cipherDesEcb = Cipher.getInstance("DES/ECB/NoPadding");

        int blocks = text.length / 8;
        byte[] output = null;
        byte[] icv = new byte[8];
        // encrypt with left key in cbc mode
        IvParameterSpec ivSpec = new IvParameterSpec(icv);
        for (int i = 0; i < blocks; i++) {
            cipherDesCbs.init(Cipher.ENCRYPT_MODE, macKeyLeft, ivSpec);
            output = cipherDesCbs.doFinal(text, i * 8, 8);
            ivSpec = new IvParameterSpec(output);
        }
        // decrypt with right key in ecb mode
        cipherDesEcb.init(Cipher.DECRYPT_MODE, macKeyRight);
        output = cipherDesEcb.doFinal(output);
        // encrypt with left key in ecb mode
        cipherDesEcb.init(Cipher.ENCRYPT_MODE, macKeyLeft);
        output = cipherDesEcb.doFinal(output);

        return Hex.toString(output).substring(0, resultLength);
    }

    /**
     * ICC Master Key
     */
    public static SecretKey createSecretKey(byte[] keyBytes) throws GeneralSecurityException {
        return new SecretKeySpec(AKB.normalizeKey(keyBytes), ALGORITHM_DES_EDE);
    }

    /**
     * Derivative ICC Master Key from Issuer Master key
     */
    public static SecretKey derivativeICCMasterKey(AtallaSimulator simulator, AKB issuerMasterKey, String pan, String cardSequence) throws GeneralSecurityException {
        byte[] iccMasterKeyBytes = derivativeICCMasterKeyBytes(simulator, issuerMasterKey, pan, cardSequence);
        return new SecretKeySpec(AKB.normalizeKey(iccMasterKeyBytes), ALGORITHM_DES_EDE);
    }

    /**
     * Derivative ICC Master Key bytes from Issuer Master key
     */
    public static byte[] derivativeICCMasterKeyBytes(AtallaSimulator simulator, AKB issuerMasterKey, String pan, String cardSequence) throws GeneralSecurityException {
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
        byte[] leftMK = simulator.encrypt(issuerMasterKey, mkBytes);
        byte[] rightMK = simulator.encrypt(issuerMasterKey, Hex.invert(mkBytes));
        byte[] iccMasterKeyBytes = new byte[leftMK.length + rightMK.length];
        System.arraycopy(leftMK, 0, iccMasterKeyBytes, 0, leftMK.length);
        System.arraycopy(rightMK, 0, iccMasterKeyBytes, leftMK.length, rightMK.length);

        return iccMasterKeyBytes;
    }

    public static byte[] deriveSessionKeyBytes(byte[] iccMasterKeyBytes, String diversificationData) throws GeneralSecurityException {
        if (diversificationData.length() == 16)
            return deriveSessionKeyBytesWithPaddedATC(iccMasterKeyBytes, diversificationData);
        else
            return deriveSessionKeyBytesWithATC(iccMasterKeyBytes, diversificationData);
    }

    private static byte[] deriveSessionKeyBytesWithPaddedATC(byte[] iccMasterKeyBytes, String diversificationData) throws GeneralSecurityException {
        SecretKey iccMasterKey = createSecretKey(iccMasterKeyBytes);
        byte[] diverse = Hex.toBytes(diversificationData);
        byte[] leftDiverse = Arrays.copyOf(diverse, diverse.length);
        leftDiverse[2] = (byte) 0xF0;
        byte[] rightDiverse = Arrays.copyOf(diverse, diverse.length);
        rightDiverse[2] = (byte) 0x0F;

        Cipher cipher = Cipher.getInstance(DES_EDE_ECB_NO_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, iccMasterKey);
        byte[] leftSK = cipher.doFinal(leftDiverse);
        cipher.init(Cipher.ENCRYPT_MODE, iccMasterKey);
        byte[] rightSK = cipher.doFinal(rightDiverse);

        byte[] sessionKeyBytes = new byte[leftSK.length + rightSK.length];
        System.arraycopy(leftSK, 0, sessionKeyBytes, 0, leftSK.length);
        System.arraycopy(rightSK, 0, sessionKeyBytes, leftSK.length, rightSK.length);
        return sessionKeyBytes;
    }

    private static byte[] deriveSessionKeyBytesWithATC(byte[] iccMasterKeyBytes, String diversificationData) {
        int keyComponentSize = iccMasterKeyBytes.length / 2;
        byte[] leftICCMasterKeyBytes = Arrays.copyOf(iccMasterKeyBytes, keyComponentSize);
        byte[] rightICCMasterKeyBytes = Arrays.copyOfRange(iccMasterKeyBytes, keyComponentSize, iccMasterKeyBytes.length);

        byte[] diversificationDataBytes = Hex.toBytes(diversificationData);

        byte[] paddedLeftDiverse = Hex.leftPad(diversificationDataBytes, keyComponentSize, (byte)0x00);
        byte[] leftSessionKeyBytes = Hex.xor(leftICCMasterKeyBytes, paddedLeftDiverse);

        byte[] invertedDiversificationData = Hex.invert(diversificationDataBytes);
        byte[] rightInvertedDiverse = Hex.xor(invertedDiversificationData, new byte[]{(byte)0xFF, (byte)0xFF});
        byte[] paddedRightInvertedDiverse = Hex.leftPad(rightInvertedDiverse, keyComponentSize, (byte)0x00);
        byte[] rightSessionKeyBytes = Hex.xor(rightICCMasterKeyBytes, paddedRightInvertedDiverse);

        return Hex.concat(leftSessionKeyBytes, rightSessionKeyBytes);
    }

    public static String calculateKeyBytesCheckDigits(byte[] keyBytes, int checkDigitHexLength) throws GeneralSecurityException {
        return calculateCheckDigits(createSecretKey(keyBytes), checkDigitHexLength);
    }

    public static String calculateCheckDigits(SecretKey secretKey, int checkDigitHexLength) throws GeneralSecurityException {
        Cipher chkCipher = Cipher.getInstance(DES_EDE_ECB_NO_PADDING);
        chkCipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] chk = chkCipher.doFinal(new byte[8]);
        return Hex.toString(chk).substring(0, checkDigitHexLength);
    }

    public static String calculateCheckDigits(AtallaSimulator simulator, String keyValue, int checkDigitHexLength) throws GeneralSecurityException {
        byte[] chk = simulator.encrypt(new AKB(keyValue), new byte[8]);
        return Hex.toString(chk).substring(0, checkDigitHexLength);
    }
}
