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

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Random;

public class Password {
    // The higher the number of iterations the more
    // expensive computing the hash is for us and
    // also for an attacker.
    private static final int ITERATIONS = 20*1000;
    private static final int SALT_LEN = 64;
    private static final int DESIRED_KEY_LEN = 512;

    private Password() {
    }

    public static char[] generateRandomPassword(int min, int max) {
        StringBuilder builder = new StringBuilder();
        for (char ch = '0' ; ch <= '9'; ch++)
            builder.append(ch);
        for (char ch = 'A' ; ch <= 'Z'; ch++)
            builder.append(ch);
        for (char ch = 'A' ; ch <= 'z'; ch++)
            builder.append(ch);
        builder.append("!@#$%^&*-_+=~|<>");
        String charSet = builder.toString();

        Random random = new Random();
        int len = min + random.nextInt(max - min);
        char[] password = new char[len];
        for (int i = 0; i < password.length; i++) {
            int idx = random.nextInt(charSet.length());
            password[i] = charSet.charAt(idx);
        }
        return password;
    }

    /* Computes a salted PBKDF2 hash of given plaintext password
     suitable for storing in a database.
     Empty passwords are not supported. */
    public static String getSaltedHash(char[] password) throws GeneralSecurityException {
        return getSaltedHash(password, false);
    }

    /* Computes a salted PBKDF2 hash of given plaintext password
     suitable for storing in a database.
     Empty passwords are not supported. */
    protected static String getSaltedHash(char[] password, boolean base64) throws GeneralSecurityException {
        byte[] salt = RNG.generateSeed(SALT_LEN);
        // store the salt with the password
        if (base64) {
            return Base64.getEncoder().encodeToString(salt) + "$" + hash(password, salt, base64);
        }
        return Hex.toString(salt) + "$" + hash(password, salt, base64);
    }

    /* Checks whether given plaintext password corresponds
     to a stored salted hash of the password. */
    public static boolean check(char[] password, String stored) throws GeneralSecurityException {
        if (stored == null)
            return false;
        String[] saltAndPass = stored.split("\\$");
        if (saltAndPass.length != 2) {
            throw new IllegalStateException("The stored password have the form 'salt$hash'");
        }
        boolean base64 = saltAndPass[0].endsWith("==");
        String hashOfInput = null;
        if (base64)
            hashOfInput = hash(password, Base64.getDecoder().decode(saltAndPass[0]), base64);
        else hashOfInput = hash(password, DatatypeConverter.parseHexBinary(saltAndPass[0]), base64);
        return hashOfInput.equals(saltAndPass[1]);
    }

    // using PBKDF2 from Sun, an alternative is https://github.com/wg/scrypt
    // cf. http://www.unlimitednovelty.com/2012/03/dont-use-bcrypt.html
    private static String hash(char[] password, byte[] salt, boolean base64) throws GeneralSecurityException {
        if (password == null || password.length == 0)
            throw new IllegalArgumentException("Empty passwords are not supported.");
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        SecretKey key = f.generateSecret(new PBEKeySpec(password, salt, ITERATIONS, DESIRED_KEY_LEN));
        if (base64)
            return Base64.getEncoder().encodeToString(key.getEncoded());
        return Hex.toString(key.getEncoded());
    }
}