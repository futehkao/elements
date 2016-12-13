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

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by futeh.
 */
public class Hash {

    private static final int iterations = 20*1000;

    public static String pbkdf2_256(String text, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        SecretKey key = f.generateSecret(new PBEKeySpec(text.toCharArray(), salt, iterations, 256));
        return Hex.toString(key.getEncoded());
    }

    public static String pbkdf2_512(String text, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        SecretKey key = f.generateSecret(new PBEKeySpec(text.toCharArray(), salt, iterations, 512));
        return Hex.toString(key.getEncoded());
    }

    public static String sha256(String text) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return digest(sha256, text);
    }

    public static String sha512(String text) throws NoSuchAlgorithmException {
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        return digest(sha512, text);
    }

    public static String digest(MessageDigest md, String text) {
        try {
            md.update(text.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // should not happen
            throw new RuntimeException(e);
        }
        byte byteData[] = md.digest();

        return Hex.toString(byteData);
    }

    public static void main(String ... args) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 50; i++) builder.append((char) i);
        String str = "";
        byte[] salt = RNG.getSecureRandom().generateSeed(32);
        str = pbkdf2_256(builder.toString(), salt);

        long start = System.currentTimeMillis();
        str = pbkdf2_256(builder.toString(), salt);
        System.out.println("time=" + (System.currentTimeMillis() - start) + "ms");
        System.out.println("length=" + str.length() + " " + str);

        start = System.currentTimeMillis();
        str = pbkdf2_512(builder.toString(), salt);
        System.out.println("time=" + (System.currentTimeMillis() - start) + "ms");
        System.out.println("length=" + str.length() + " " + str);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            str = sha256(builder.toString());
        }
        System.out.println("sha256 time=" + (System.currentTimeMillis() - start) + "ms");
        System.out.println("length=" + str.length() + " " + str);
    }
}
