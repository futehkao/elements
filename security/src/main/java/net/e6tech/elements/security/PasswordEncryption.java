/*
Copyright 2015-2019 Futeh Kao

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

/**
 * Created by futeh.
 */

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;

public class PasswordEncryption {
    static {
        SymmetricCipher.initialize();
    }

    char[] password;
    public static final int SALT_LEN = 64;
    private static final int KEYLEN_BITS = 256; // see notes below where this is used.
    private static final int ITERATIONS = 1000;

    /*
     * create an object with just the passphrase from the user. Don't do anything else yet
     */
    public PasswordEncryption(char[] password) {
        this.password = password;
    }

    public PasswordEncrypted encrypt(byte[] plain, String keyAlias, String keyVersion) throws GeneralSecurityException {
        byte[] iv = RNG.generateSeed(16);
        byte[] salt = RNG.generateSeed(SALT_LEN);
        return encrypt(salt, iv, plain, keyAlias, keyVersion);
    }

    public PasswordEncrypted encrypt(byte[] salt, byte[] iv, byte[] plain, String keyAlias, String keyVersion) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        KeySpec spec = new PBEKeySpec (password, salt, ITERATIONS, KEYLEN_BITS);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec (tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plain);

        return new PasswordEncrypted(salt, iv, encrypted, keyAlias, keyVersion);
    }

    public byte[] decrypt(PasswordEncrypted encrypted) throws GeneralSecurityException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        KeySpec spec = new PBEKeySpec(password, encrypted.getSalt(), ITERATIONS, KEYLEN_BITS);
        SecretKey tmp = factory.generateSecret(spec);
        byte[] hash = tmp.getEncoded();
        SecretKey secret = new SecretKeySpec(hash, "AES");

        /* Decrypt the message, given derived key and initialization vector. */
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7PADDING");
        cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(encrypted.getIv()));
        return cipher.doFinal(encrypted.getEncrypted());
    }

}
