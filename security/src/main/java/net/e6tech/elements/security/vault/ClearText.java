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
package net.e6tech.elements.security.vault;

import net.e6tech.elements.security.Hex;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Properties;

import static net.e6tech.elements.security.vault.Constants.*;


/**
 * Created by futeh.
 */
public class ClearText implements Serializable {
    private static final long serialVersionUID = -6495396359046821847L;

    public static final String PUBLIC_KEY_MOD = "public-key-mod";
    public static final String PUBLIC_KEY_EXP= "public-key-exp";

    Properties properties = new Properties();
    Properties protectedProperties = new Properties();
    byte[] bytes;

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Properties getProtectedProperties() {
        return protectedProperties;
    }

    public void setProtectedProperties(Properties protectedProperties) {
        this.protectedProperties = protectedProperties;
    }

    // not a getter so that ObjectMapper won't pick it up
    public String toText() {
        if (bytes == null) return null;
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // not a setter so that ObjectMapper won't pick it up
    public void resetText(String text) {
        try {
            setBytes(text.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] text) {
        this.bytes = text;
    }

    public String getProperty(String key) {
        if (getProperties() == null) return null;
        return getProperties().getProperty(key);
    }

    public void setProperty(String key, String value) {
        if (getProperties() == null) properties = new Properties();
        properties.setProperty(key, value);
    }

    public String getProtectedProperty(String key) {
        if (getProtectedProperties() == null) return null;
        return getProtectedProperties().getProperty(key);
    }

    public void setProtectedProperty(String key, String value) {
        if (getProtectedProperties() == null) protectedProperties = new Properties();
        protectedProperties.setProperty(key, value);
    }

    public void protect() {
        if (getProtectedProperties() == null) protectedProperties = new Properties();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (value != null && !protectedProperties.containsKey(key)) protectedProperties.setProperty(key, properties.getProperty(key));
        }
    }

    public String toString() {
        return Hex.toString(bytes) + properties + protectedProperties;
    }

    // not getter/setter pattern to avoid JSON encoding
    public String alias() {
        return getProperty(ALIAS);
    }

    public void alias(String alias) {
        setProperty(ALIAS, alias);
        setProtectedProperty(ALIAS, alias);
    }

    // not getter/setter pattern to avoid JSON encoding
    public String version() {
        return getProperty(VERSION);
    }

    public void version(String version) {
        setProperty(VERSION, version);
        setProtectedProperty(VERSION, version);
    }

    public SecretKey asSecretKey() {
        SecretKey secretKey = new SecretKeySpec(getBytes(), getProperty(ALGORITHM));
        return secretKey;
    }

    public KeyPair asKeyPair() throws GeneralSecurityException {
        try {
            String text = new String(getBytes(), "UTF-8");
            String[] components = text.split("\\$");
            if (components.length != 2) {
                throw new IllegalStateException("Invalid encryption format");
            }
            BigInteger mod = new BigInteger(components[0], 16);
            BigInteger exp = new BigInteger(components[1], 16);
            RSAPrivateKeySpec priv = new RSAPrivateKeySpec(mod, exp);
            mod = new BigInteger(getProtectedProperty(PUBLIC_KEY_MOD), 16);
            exp = new BigInteger(getProtectedProperty(PUBLIC_KEY_EXP), 16);
            RSAPublicKeySpec pub = new RSAPublicKeySpec(mod, exp);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PublicKey pubKey = fact.generatePublic(pub);
            PrivateKey privateKey = fact.generatePrivate(priv);
            return new KeyPair(pubKey, privateKey);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

}
