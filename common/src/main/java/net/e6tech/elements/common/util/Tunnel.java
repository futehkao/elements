/*
 * Copyright 2015-2026 Futeh Kao
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

package net.e6tech.elements.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.e6tech.elements.common.resources.Provision;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;

public class Tunnel {

    public static Integer maxCapacity = Provision.cacheGrandeInitialCapacity;
    public static Integer initialCapacity = Provision.cacheShortInitialCapacity;

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final SecretKey SECRET_KEY = generateKeySpec();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final IvParameterSpec IV = new IvParameterSpec(SECURE_RANDOM.generateSeed(16));

    private static class TunnelHolder {
        private static final Cache<String, Object> INSTANCE = Caffeine.newBuilder()  // Note CacheHolder static fields are initialized when it's first accessed.
                .initialCapacity(maxCapacity)
                .maximumSize(initialCapacity)
                .build();
    }

    private static SecretKey generateKeySpec() {
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            throw new SystemException(e);
        }
        keyGen.init(128);
        return keyGen.generateKey();
    }

    public static byte[] encryptBytes(byte[] plain) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, IV);
        return cipher.doFinal(plain);
    }

    public static byte[] decryptBytes(byte[] encrypted) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, IV);
        return cipher.doFinal(encrypted);
    }

    protected static Cache<String, Object> getTunnels() {
        return TunnelHolder.INSTANCE;  // use static lazy initialization.
    }

    public static long getTunnelSize() {
        return getTunnels().estimatedSize();
    }

    protected static Entry tunnel(String key, Object value) {
        Entry entry = Entry.NOT;
        if (getTunnels().getIfPresent(key) == null) {
            entry = new Entry(key);
            getTunnels().put(key, value);
            return entry;
        }
        return entry;
    }

    public static String decodeKey(String key) {
        byte[] bytes = Base64.getDecoder().decode(key);
        try {
            return new String(decryptBytes(bytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public static String getKey(Object value) {
        if (value == null)
            return "null";
        byte[] obfuscated;
        String key = value.getClass().getName() + "-" + System.identityHashCode(value) + "-" + value.hashCode();
        try {
            obfuscated = encryptBytes(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(obfuscated);
        } catch (GeneralSecurityException e) {
            return key;
        }
    }

    public static <T> Optional<T> getValue(String key) {
        if (key == null)
            return Optional.ofNullable(null);
        return Optional.ofNullable((T) getTunnels().getIfPresent(key));
    }

    public static <T> void tunnel(T value, Consumer<T> run) {
        String key = getKey(value);
        Entry entry = null;
        try {
            entry = tunnel(key, value);
            run.accept(value);
        } finally {
            if (entry != null)
                entry.destroy();
        }
    }

    public Entry getEntry(String key) {
        if (getTunnels().getIfPresent(key) != null) {
            return new Entry(key);
        }
        return Entry.NOT;
    }

    public static class Entry {
        public static final Entry NOT = new Entry(null);

        private final String key;

        protected Entry(String key) {
            this.key = key;
        }

        public void destroy() {
            if (key != null) {
                getTunnels().invalidate(key);
            }
        }
    }

}
