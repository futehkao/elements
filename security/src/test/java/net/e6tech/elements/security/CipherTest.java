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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.crypto.SecretKey;
import java.security.KeyPair;

/**
 * Created by futeh.
 */
public class CipherTest {
    @Test
    public void testSymmetric() throws Exception {
        SymmetricCipher encryption = SymmetricCipher.getInstance("AES");
        SecretKey key = encryption.generateKeySpec();
        String iv = encryption.generateIV();
        String encrypted = encryption.encrypt(key, "hello world".getBytes("UTF-8"), iv);
        byte[] decrypted = encryption.decrypt(key, encrypted, iv);
        System.out.println(new String(decrypted, "UTF-8"));
    }

    @Test
    public void testAsymmetric() throws Exception {
        AsymmetricCipher encryption = AsymmetricCipher.getInstance("RSA");
        KeyPair key = encryption.generateKeySpec();
        String encrypted = encryption.encrypt(key.getPublic(), "hello world, 1234567890".getBytes("UTF-8"));
        byte[] decrypted = encryption.decrypt(key.getPrivate(), encrypted);
        System.out.println(new String(decrypted, "UTF-8"));
    }
}
