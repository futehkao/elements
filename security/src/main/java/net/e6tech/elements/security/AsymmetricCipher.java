/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.security;

import net.e6tech.elements.common.util.SystemException;

import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;

/**
 * Created by futeh.
 */
public class AsymmetricCipher {

    public static final String ALGORITHM_RSA = "RSA";

    private String algorithm;
    private String transformation;
    private int keyLength;
    private boolean base64 = false;
    private KeyFactory keyFactory;

    static {
        SymmetricCipher.initialize();
    }

    protected AsymmetricCipher(String algorithm, int keyLength) {
        this.algorithm = algorithm;
        this.transformation = algorithm + "/None/OAEPWithSHA256AndMGF1Padding";
        this.keyLength = keyLength;
        try {
            this.keyFactory = KeyFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new SystemException(e);
        }
    }

    public static AsymmetricCipher getInstance(String algorithm) {
        return getInstance(algorithm, 2048);
    }

    public static AsymmetricCipher getInstance(String algorithm, int keyLength) {
        if (ALGORITHM_RSA.equalsIgnoreCase(algorithm)) {
            return new AsymmetricCipher("RSA", keyLength);
        } else {
            throw new IllegalArgumentException(algorithm + " is not supported");
        }
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String encrypt(PublicKey key, byte[] plain) throws GeneralSecurityException {
        byte[] encrypted = encryptBytes(key, plain);
        if (base64) {
            return Base64.getEncoder().encodeToString(encrypted);
        } else {
            return Hex.toString(encrypted);
        }
    }

    public byte[] encryptBytes(PublicKey publicKey, byte[] plain) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation, "BC");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plain);
    }

    public byte[] decrypt(PrivateKey key, String encrypted) throws GeneralSecurityException {
        byte[] decodedBytes;
        if (base64) {
            decodedBytes = Base64.getDecoder().decode(encrypted);
        } else {
            decodedBytes = Hex.toBytes(encrypted);
        }
        return decryptBytes(key, decodedBytes);
    }

    public byte[] decryptBytes(PrivateKey privateKey, byte[] encrypted) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation, "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encrypted);
    }

    public KeyPair generateKeySpec() throws GeneralSecurityException{
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(keyLength);
        return kpg.genKeyPair();
    }

    public KeyFactory getKeyFactory() {
        return keyFactory;
    }
}
