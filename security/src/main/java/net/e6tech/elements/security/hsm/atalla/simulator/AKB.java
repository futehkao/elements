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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.security.Hex;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Key;

/**
 * Created by futeh.
 */
public class AKB {
    public static final String DES_EDE_CBC_NO_PADDING = "DESede/CBC/NoPadding";
    public static final String ALGORITHM_DES_EDE = "DESede";
    private String keyBlock;
    String checkDigits;

    public AKB(String keyBlock) {
        this.keyBlock = keyBlock;
    }

    public AKB(String header, byte[] keyEncryptionkey, byte[] key) throws GeneralSecurityException {
        keyBlock = generateAKB(header, keyEncryptionkey, key);
        checkDigits = calculateCheckDigits(key);
    }

    public static byte[] normalizeKey(byte[] key) throws GeneralSecurityException {
        byte[] normalized = new byte[24];
        if (key.length == 8) { // single des
            System.arraycopy(key, 0, normalized, 0, key.length);
            System.arraycopy(key, 0, normalized, 8, key.length);
            System.arraycopy(key, 0, normalized, 16, key.length);
        } else if (key.length == 16) { // double length
            System.arraycopy(key, 0, normalized, 0, key.length);
            System.arraycopy(key, 0, normalized, 16, 8);
        } else if (key.length == 24) { // triple length
            normalized = key;
        } else {
            throw new GeneralSecurityException("Invalid key side=" + key.length);
        }
        return normalized;
    }

    public String getKeyBlock() {
        return keyBlock;
    }

    public String getCheckDigits() {
        return checkDigits;
    }

    public String getHeader() {
        return keyBlock.split(",")[0];
    }

    public String getEncryptedKey() {
        return keyBlock.split(",")[1];
    }

    public String getMac() {
        return keyBlock.split(",")[2];
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    public byte[] decryptKey(byte[] keyEncryptionKey) throws GeneralSecurityException {
        byte[] headerBytes = new byte[0];
        try {
            headerBytes = getHeader().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
        }
        IvParameterSpec ivSpec = new IvParameterSpec(headerBytes);
        Cipher cipher = Cipher.getInstance(DES_EDE_CBC_NO_PADDING);
        byte[] xorMKey = maskAKBEncryptionKey(keyEncryptionKey, Hex.toBytes("45")[0]);
        SecretKey mkey = new SecretKeySpec(xorMKey, ALGORITHM_DES_EDE);
        cipher.init(Cipher.DECRYPT_MODE, mkey, ivSpec);
        byte[] keyBytes = cipher.doFinal(Hex.toBytes(getEncryptedKey()));
        byte[] key;
        byte padding1 = Hex.toBytes("53")[0];
        byte padding2 = Hex.toBytes("44")[0];
        if (keyBytes[8] == padding1 && keyBytes[9] == padding1
                && keyBytes[10] == padding1 && keyBytes[11] == padding1
                && keyBytes[12] == padding1 && keyBytes[13] == padding1
                && keyBytes[14] == padding1 && keyBytes[15] == padding1) {
            key = new byte[8];
            System.arraycopy(keyBytes, 0, key, 0, 8);
        } else if (keyBytes[16] == padding2 && keyBytes[17] == padding2
                && keyBytes[18] == padding2 && keyBytes[19] == padding2
                && keyBytes[20] == padding2 && keyBytes[21] == padding2
                && keyBytes[22] == padding2 && keyBytes[23] == padding2) {
            key = new byte[16];
            System.arraycopy(keyBytes, 0, key, 0, 16);
        } else {
            key = keyBytes;
        }

        String mac = generateAKB(getHeader(), keyEncryptionKey, key).split(",")[2];
        if (!mac.equals(getMac())) 
            throw new GeneralSecurityException("Mac not verified");
        if (checkDigits == null)
            checkDigits = calculateCheckDigits(key);
        return key;
    }

    private String generateAKB(String header, byte[] keyEncryptionKey, byte[] key) throws GeneralSecurityException {
        if (key.length % 8 != 0) 
            throw new GeneralSecurityException("key size must be multiple of 8");
        byte[] paddedKey = new byte[24];
        if (key.length == 8) { // single des
            byte padding = Hex.toBytes("53")[0];
            System.arraycopy(key, 0, paddedKey, 0, key.length);
            for (int i = 8; i < 24; i++) 
                paddedKey[i] = padding;
        } else if (key.length == 16) { // double length
            byte padding = Hex.toBytes("44")[0];
            System.arraycopy(key, 0, paddedKey, 0, key.length);
            for (int i = 16; i < 24; i++) 
                paddedKey[i] = padding;
        } else if (key.length == 24) { // triple length
            // do nothing
        } else {
            throw new GeneralSecurityException("Invalid key size=" + key.length);
        }

        byte[] headerBytes = new byte[0];
        try {
            headerBytes = header.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
        }
        IvParameterSpec ivSpec = new IvParameterSpec(headerBytes);
        Cipher cipher = Cipher.getInstance(DES_EDE_CBC_NO_PADDING);
        byte[] xorMKey = maskAKBEncryptionKey(keyEncryptionKey, Hex.toBytes("45")[0]);
        SecretKey mkey = new SecretKeySpec(xorMKey, ALGORITHM_DES_EDE);
        cipher.init(Cipher.ENCRYPT_MODE, mkey, ivSpec);
        byte[] keyBytes = cipher.doFinal(paddedKey);
        String block = Hex.toString(keyBytes);

        // calculating Mac
        xorMKey = maskAKBEncryptionKey(keyEncryptionKey, Hex.toBytes("4D")[0]);
        String mac = cbcMac(xorMKey, Hex.toBytes(Hex.toString(headerBytes) + block));

        return header + "," + block + "," + mac;
    }

    private byte[] maskAKBEncryptionKey(byte[] keyEncrptionKey, byte mask) {
        byte[] xorMKey = new byte[keyEncrptionKey.length];
        for (int i = 0; i < keyEncrptionKey.length; i++) {
            xorMKey[i] = (byte) (keyEncrptionKey[i] ^  mask);
        }
        return xorMKey;
    }

    private String cbcMac(byte[] macKeyBytes, byte[] text) throws GeneralSecurityException {
        Key macKey = new SecretKeySpec(macKeyBytes, ALGORITHM_DES_EDE);
        Cipher cipher = Cipher.getInstance(DES_EDE_CBC_NO_PADDING);

        if (text.length % 8 != 0) 
            throw new GeneralSecurityException("data block size must be multiple of 8");

        int blocks = text.length / 8;

        byte[] output = null;
        byte[] icv = new byte[8];
        IvParameterSpec ivSpec = new IvParameterSpec(icv);
        for (int i = 0; i < blocks; i++) {
            cipher.init(Cipher.ENCRYPT_MODE, macKey, ivSpec);
            output = cipher.doFinal(text, i * 8, 8);
            ivSpec = new IvParameterSpec(output);
        }
        return Hex.toString(output);
    }

    @SuppressWarnings("squid:S2278")
    public static String calculateCheckDigits(byte[] key) throws GeneralSecurityException {
        Cipher chkCipher = Cipher.getInstance("DESede/ECB/NoPadding");
        chkCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(normalizeKey(key), ALGORITHM_DES_EDE));
        byte[] chk = chkCipher.doFinal(new byte[8]);
        return Hex.toString(chk).substring(0, 4);
    }
}
