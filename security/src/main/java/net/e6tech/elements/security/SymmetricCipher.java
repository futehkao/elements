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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.*;
import java.util.Base64;
import java.util.Map;

/**
 * Created by futeh.
 */
public class SymmetricCipher {

    public static final String ALGORITHM_AES = "AES";

    static volatile boolean initialized = false;

    static final Logger logger = Logger.getLogger();

    private String algorithm;
    private String transformation;
    private int keyLength;
    private boolean base64 = false;

    static {
        initialize();
    }

    protected SymmetricCipher(String algorithm, int keyLength) {
        this.algorithm = algorithm;
        this.transformation = algorithm + "/CBC/PKCS7PADDING";
        this.keyLength = keyLength;
        generateKeySpec(); // prime the pump
    }

    public static SymmetricCipher getInstance(String algorithm) {
        return getInstance(algorithm, 256);
    }

    public static SymmetricCipher getInstance(String algorithm, int keyLength) {
        if (ALGORITHM_AES.equalsIgnoreCase(algorithm)) {
            return new SymmetricCipher(ALGORITHM_AES, keyLength);
        } else {
            throw new IllegalArgumentException(algorithm + " is not supported");
        }
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public static void initialize() {
        if (initialized)
            return;
        initialized = true;
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        String version = System.getProperty("java.version");
        String[] components = version.split("\\.");

        int major;
        if (components[0].equals("1")) {
            try {
                major = Integer.valueOf(components[1]);
            } catch (NumberFormatException ex) {
                major = 8;
            }
        } else {
            try {
                major = Integer.valueOf(components[0]);
            } catch (NumberFormatException ex) {
                major = 9;
            }
        }
        if (major >= 9)
            unlimitedCrypto9();
        else
            unlimitedCrypto8();
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    private static void unlimitedCrypto9() {
        // In Java 9, default is unlimited.
        // Security.setProperty("crypto.policy", "unlimited");
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    private static void unlimitedCrypto8() {
        try {
            /*
             * Do the following, but with reflection to bypass access checks:
             *
             * JceSecurity.isRestricted = false;
             * JceSecurity.defaultPolicy.perms.clear();
             * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
             */
            final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
            final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
            final Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

            final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
            isRestrictedField.setAccessible(true);

            // isRestrictedField is static final.  We need to change its modifiers field
            // before we can change the its value.
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            int origMod = isRestrictedField.getModifiers();
            modifiersField.setInt(isRestrictedField, isRestrictedField.getModifiers() & ~Modifier.FINAL);
            isRestrictedField.set(null, false);
            // ok change it back to before.
            modifiersField.setInt(isRestrictedField, origMod);

            final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
            defaultPolicyField.setAccessible(true);
            final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

            final Field perms = cryptoPermissions.getDeclaredField("perms");
            perms.setAccessible(true);
            ((Map<?, ?>) perms.get(defaultPolicy)).clear();

            final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            defaultPolicy.add((Permission) instance.get(null));

            logger.info("Successfully removed cryptography restrictions");
        } catch (final Exception e) {
            logger.warn("Failed to remove cryptography restrictions", e);
        }
    }

    public boolean isBase64() {
        return base64;
    }

    public void setBase64(boolean base64) {
        this.base64 = base64;
    }

    public byte[] toBytes(String string) {
        if (base64)
            return Base64.getDecoder().decode(string);
        else
            return Hex.toBytes(string);
    }

    public String toString(byte[] bytes) {
        if (base64) {
            return Base64.getEncoder().encodeToString(bytes);
        } else {
            return Hex.toString(bytes);
        }
    }

    public String encrypt(SecretKey key, byte[] plain, String iv) throws GeneralSecurityException {
        byte[] ivBytes = null;
        if (iv != null) {
            ivBytes = toBytes(iv);
        }
        byte[] encrypted = encryptBytes(key, plain, ivBytes);

        return toString(encrypted);
    }

    public byte[] encryptBytes(SecretKey key, byte[] plain, byte[] initVector) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation, "BC");
        byte[] iv = initVector;
        if (iv != null) {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        } else {
            iv = new byte[16];
            for (int i=0; i < iv.length; i++)
                iv[i] = 0;
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        }
        return cipher.doFinal(plain);
    }

    public byte[] decrypt(SecretKey key, String encrypted, String initVector) throws GeneralSecurityException {
        byte[] ivBytes = null;
        if (initVector != null) {
            ivBytes = toBytes(initVector);
        }
        byte[] decodedBytes = toBytes(encrypted);
        return decryptBytes(key, decodedBytes, ivBytes);
    }

    public byte[] decryptBytes(SecretKey key, byte[] encrypted, byte[] initVector) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation, "BC");
        byte[] iv = initVector;
        if (iv != null) {
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        } else {
            iv= new byte[16];
            for (int i=0; i < iv.length; i++)
                iv[i] = 0;
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        }
        return cipher.doFinal(encrypted);
    }

    public SecretKey generateKeySpec() {
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new SystemException(e);
        }
        keyGen.init(keyLength);
        return keyGen.generateKey();
    }

    public SecretKey getKeySpec(byte[] bytes) {
        return new SecretKeySpec(bytes, algorithm);
    }

    public byte[] generateIVBytes() {
        return RNG.generateSeed(16);
    }

    public String generateIV() {
        byte[] iv = generateIVBytes();
        if (base64) {
            return Base64.getEncoder().encodeToString(iv);
        } else {
            return Hex.toString(iv);
        }
    }
}
