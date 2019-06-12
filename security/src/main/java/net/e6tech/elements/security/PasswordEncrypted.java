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

import javax.xml.bind.DatatypeConverter;
import java.util.Base64;

/**
 * Created by futeh on 1/2/16.
 */
public class PasswordEncrypted {

    byte[] iv;
    byte[] salt;
    byte[] encrypted;
    String keyAlias;
    String keyVersion;

    public PasswordEncrypted() {
    }

    public PasswordEncrypted(byte[] salt, byte[] iv, byte[] encrypted, String keyAlias, String keyVersion) {
        this.salt = salt;
        this.iv = iv;
        this.encrypted = encrypted;
        this.keyAlias = keyAlias;
        this.keyVersion = keyVersion;
    }

    public PasswordEncrypted(String encoded) {
        String[] components = encoded.split("\\$");
        if (components.length <= 3) {
            throw new IllegalStateException("The stored password have the form 'salt$hash'");
        }
        boolean base64 = components[0].endsWith("==");
        if (base64) {
            salt = Base64.getDecoder().decode(components[0]);
            iv = Base64.getDecoder().decode(components[1]);
            encrypted = Base64.getDecoder().decode(components[2]);
        } else {
            salt = DatatypeConverter.parseHexBinary(components[0]);
            iv = DatatypeConverter.parseHexBinary(components[1]);
            encrypted = DatatypeConverter.parseHexBinary(components[2]);
        }
        if (components.length > 3)
            keyAlias = components[3];
        if (components.length > 4)
            keyVersion = components[4];
    }

    public byte[] getIv() {
        return iv;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getEncrypted() {
        return encrypted;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public String getBase64Iv() {
        return Base64.getEncoder().encodeToString(iv);
    }

    public String getHexIv() {
        return DatatypeConverter.printHexBinary(iv);
    }

    public String getBase64Salt() {
        return Base64.getEncoder().encodeToString(salt);
    }

    public String getHexSalt() {
        return DatatypeConverter.printHexBinary(salt);
    }

    public String getBase64Encrypted() {
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String getHexEncrypted() {
        return DatatypeConverter.printHexBinary(encrypted);
    }

    public String toBase64() {
        return Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(iv)
                + "$" + Base64.getEncoder().encodeToString(encrypted)
                + "$" + keyAlias
                + "$" + keyVersion;
    }

    public String toHex() {
        return DatatypeConverter.printHexBinary(salt)
                + "$" + DatatypeConverter.printHexBinary(iv)
                + "$" + DatatypeConverter.printHexBinary(encrypted)
                + "$" + keyAlias
                + "$" + keyVersion;
    }
}
