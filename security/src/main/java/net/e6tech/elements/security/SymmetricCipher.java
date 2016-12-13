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

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.*;
import java.util.Base64;
import java.util.Map;

/**
 * Created by futeh.
 */
public class SymmetricCipher {

    static volatile boolean initialized = false;

    static final Logger logger = Logger.getLogger();

    private String algorithm = "AES";
    private String transformation = algorithm + "/CBC/PKCS7PADDING";
    private int keyLength = 256;
    private boolean base64 = false;

    static {
        initialize();
    }

    public static SymmetricCipher getInstance(String algorithm) {
        if ("AES".equalsIgnoreCase(algorithm)) {
            return new SymmetricCipher("AES");
        } else {
            throw new IllegalArgumentException(algorithm + " is not supported");
        }
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

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

    public static byte[] toBytes(String hexString) {
        return DatatypeConverter.parseHexBinary(hexString);
        //  String decodedString = new String(decodedHex, "UTF-8");
    }

    public static String toString(byte[] bytes) {
        return DatatypeConverter.printHexBinary(bytes);
    }

    protected SymmetricCipher(String algorithm) {
        this.algorithm = algorithm;
        this.transformation = algorithm + "/CBC/PKCS7PADDING";
    }

    public String encrypt(SecretKey key, byte[] plain, String iv) throws GeneralSecurityException {
        byte[] ivBytes = null;
        if (iv != null) {
            if (base64) ivBytes = Base64.getDecoder().decode(iv);
            else ivBytes = Hex.toBytes(iv);
        }
        byte[] encrypted = encryptBytes(key, plain, ivBytes);

        if (base64) {
            return Base64.getEncoder().encodeToString(encrypted);
        } else {
            return Hex.toString(encrypted);
        }
    }

    public byte[] encryptBytes(SecretKey key, byte[] plain, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation, "BC");
        if (iv != null) {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        } else {
            iv = new byte[16];
            for (int i=0; i < iv.length; i++) iv[i] = 0;
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        }
        return cipher.doFinal(plain);
    }

    public byte[] decrypt(SecretKey key, String encrypted, String iv) throws GeneralSecurityException {
        byte[] ivBytes = null;
        if (iv != null) {
            if (base64) ivBytes = Base64.getDecoder().decode(iv);
            else ivBytes = Hex.toBytes(iv);
        }
        byte[] decodedBytes = null;
        if (base64) {
            decodedBytes = Base64.getDecoder().decode(encrypted);
        } else {
            decodedBytes = Hex.toBytes(encrypted);
        }
        return decryptBytes(key, decodedBytes, ivBytes);
    }

    public byte[] decryptBytes(SecretKey key, byte[] encrypted, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation, "BC");
        if (iv != null) {
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        } else {
            iv= new byte[16];
            for (int i=0; i < iv.length; i++) iv[i] = 0;
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        }
        return cipher.doFinal(encrypted);
    }

    public SecretKey generateKeySpec() {
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        keyGen.init(keyLength);
        SecretKey secretKey = keyGen.generateKey();
        return secretKey;
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

    public static void main(String ... args) {

    }
}
