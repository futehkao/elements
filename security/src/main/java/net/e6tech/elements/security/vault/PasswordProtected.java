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

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.PasswordEncrypted;
import net.e6tech.elements.security.PasswordEncryption;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Properties;

import static net.e6tech.elements.security.vault.Constants.PASSPHRASE;
import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh on 1/4/16.
 */
public class PasswordProtected {

    private static final String UTF8 = "UTF-8";

    public Secret sealUser(ClearText clear, char[] password) throws GeneralSecurityException {
        ClearText passphrase = new ClearText();
        passphrase.version("0");
        passphrase.alias(PASSPHRASE);
        try {
            passphrase.setBytes((new String(password)).getBytes(UTF8));
        } catch (UnsupportedEncodingException e) {
            throw new SystemException(e);
        }
        return seal(clear, passphrase);
    }

    public Secret seal(ClearText clear, ClearText passphrase) throws GeneralSecurityException {
        if (clear.alias() == null)
            throw new SystemException("null alias");
        clear.setProtectedProperty("alias", clear.alias());
        PasswordEncryption pwdEnc = null;
        try {
            pwdEnc = new PasswordEncryption((new String(passphrase.getBytes(), UTF8)).toCharArray());
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }
        PasswordEncrypted encrypted = pwdEnc.encrypt(clear.getBytes(), passphrase.alias(), passphrase.version());
        Secret secret = new Secret();
        secret.setProperties((Properties) clear.getProperties().clone());
        secret.setSecret(encrypted.toHex());
        if (clear.getProtectedProperties() != null) {
            try {
                String str = mapper.writeValueAsString(clear.getProtectedProperties());
                encrypted = pwdEnc.encrypt(str.getBytes(UTF8), passphrase.alias(), passphrase.version());
                secret.setProtectedProperties(encrypted.toHex());
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        return secret;
    }

    public ClearText unsealUserOrPassphrase(Secret secret, char[] password) throws GeneralSecurityException {
        ClearText passphrase = new ClearText();
        passphrase.version("0");
        passphrase.alias(PASSPHRASE);
        try {
            passphrase.setBytes((new String(password)).getBytes(UTF8));
        } catch (UnsupportedEncodingException e) {
            throw new SystemException(e);
        }
        return unseal(secret, passphrase);
    }

    public ClearText unseal(Secret secret, ClearText passphrase) throws GeneralSecurityException {
        PasswordEncrypted encrypted = new PasswordEncrypted(secret.getSecret());
        PasswordEncryption pwdEnc = null;
        try {
            pwdEnc = new PasswordEncryption((new String(passphrase.getBytes(), UTF8)).toCharArray());
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }

        byte[] plain = pwdEnc.decrypt(encrypted);
        ClearText ct = new ClearText();
        ct.setBytes(plain);
        ct.setProperties((Properties) secret.getProperties().clone());
        if (secret.getProtectedProperties() != null) {
            byte[] props = pwdEnc.decrypt(new PasswordEncrypted(secret.getProtectedProperties()));
            Properties properties = null;
            try {
                properties = mapper.readValue(new String(props, UTF8), Properties.class);
            } catch (IOException e) {
                throw new SystemException(e);
            }
            ct.setProtectedProperties(properties);
        }
        return ct;
    }
}
