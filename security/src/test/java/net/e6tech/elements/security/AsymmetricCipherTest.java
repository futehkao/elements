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

package net.e6tech.elements.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsymmetricCipherTest {

    @Test
    void basic() throws GeneralSecurityException {
        AsymmetricCipher cipher = AsymmetricCipher.getInstance(AsymmetricCipher.ALGORITHM_RSA);
        long start = System.currentTimeMillis();
        KeyPair keyPair = cipher.generateKeySpec();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyPair.getPrivate();
        System.out.println("generateKeySpec " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("Public Key Exponent: " + publicKey.getPublicExponent());
        System.out.println("Public Key Modulus: " + publicKey.getModulus());
        System.out.println("Private Key Public Exponent: " + privateKey.getPublicExponent());
        System.out.println("Private Key Modulus " + privateKey.getModulus());
        System.out.println("Private Key exponent: " + privateKey.getPrivateExponent() + " modulus" + privateKey.getModulus());
        System.out.println("Private Key exponent: " + privateKey.getPrivateExponent() + " modulus" + privateKey.getModulus());

        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7};
        start = System.currentTimeMillis();
        String encrypted = cipher.encrypt(keyPair.getPublic(), data);
        System.out.println("encrypt " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();
        byte[] decrypted = cipher.decrypt(keyPair.getPrivate(), encrypted);
        System.out.println("decrypt " + (System.currentTimeMillis() - start) + "ms");
        assertTrue(Arrays.equals(data, decrypted));
    }
}
