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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
class SymmetricCipherTest {
    @Test
    void basic() throws GeneralSecurityException {
        SymmetricCipher cipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        long start = System.currentTimeMillis();
        SecretKey key = cipher.generateKeySpec();
        System.out.println("generateKeySpec " + (System.currentTimeMillis() - start) + "ms");
        String hex = Hex.toString(key.getEncoded());
        System.out.println("Hex encoding: " + hex + " length: " + hex.length());
        start = System.currentTimeMillis();
        String iv = cipher.generateIV();
        assertTrue(iv.length() == 32);
        System.out.println("generateIV " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("IV: " + iv + " length: " + iv.length());

        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7};
        String encrypted = cipher.encrypt(key, data, iv);
        byte[] decrypted = cipher.decrypt(key, encrypted, iv);
        assertTrue(Arrays.equals(data, decrypted));
    }

    @Test
    void test() {
        byte[] mask = {0, 1, 2, 3, 4, 5, 6, 7};
        byte[] longBytes = longToByteArray(1234L);
        System.out.println(byteArrayToLong(longBytes));
        for (int i = 0; i < 8; i++) longBytes[i] = (byte)(longBytes[i] ^ mask[i]);
        System.out.println(byteArrayToLong(longBytes));
        for (int i = 0; i < 8; i++) longBytes[i] = (byte)(longBytes[i] ^ mask[i]);
        System.out.println(byteArrayToLong(longBytes));
    }

    private byte[] longToByteArray(long value) {
        return new byte[] {
                (byte) (value >> 56),
                (byte) (value >> 48),
                (byte) (value >> 40),
                (byte) (value >> 32),
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    private long byteArrayToLong(byte[] b) {
        return ((((long) b[0]) << 56)
                | (((long) b[1] & 0xff) << 48)
                | (((long) b[2] & 0xff) << 40)
                | (((long) b[3] & 0xff) << 32)
                | (((long) b[4] & 0xff) << 24)
                | (((long) b[5] & 0xff) << 16)
                | (((long) b[6] & 0xff) << 8)
                | (((long) b[7] & 0xff)));
    }
}
