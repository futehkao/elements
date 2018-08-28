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
import net.e6tech.elements.security.SymmetricCipher;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Properties;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh on 1/4/16.
 */
public class KeyProtected {

    public Secret seal(String alias, ClearText clear, ClearText key) throws GeneralSecurityException {
        clear.setProperty("alias", alias);
        clear.setProtectedProperty("alias", alias);

        // setup cipher
        SymmetricCipher encryption = SymmetricCipher.getInstance("AES");
        encryption.setBase64(false);
        String iv = encryption.generateIV();

        // encrypt clear's content
        String encrypted = encryption.encrypt(key.asSecretKey(), clear.getBytes(), iv);

        Secret secret = new Secret();
        secret.setProperties((Properties) clear.getProperties().clone());
        secret.setSecret(iv + "$" + encrypted + "$" + key.alias() + "$" + key.version());

        // encrypt properties
        if (clear.getProperties() != null) {
            try {
                String str = mapper.writeValueAsString(clear.getProtectedProperties());
                str = encryption.encrypt(key.asSecretKey(), str.getBytes(StandardCharsets.UTF_8), iv);
                secret.setProtectedProperties(iv + "$" + str  + "$" + key.alias() + "$" + key.version());
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return secret;
    }

    public ClearText unseal(Secret secret, ClearText key) throws GeneralSecurityException {
        try {
            SymmetricCipher encryption = SymmetricCipher.getInstance("AES");
            encryption.setBase64(false);
            if (secret == null)
                return null;
            String encrypted = secret.getSecret();
            String[] components = encrypted.split("\\$");
            if (components.length != 4) {
                throw new IllegalStateException("The stored secret should have been formatted as 'iv$encypted$alias$version'");
            }

            if (!components[2].equalsIgnoreCase(key.alias()))
                throw new GeneralSecurityException("Key alias mismatch");
            if (!components[3].equalsIgnoreCase(key.version()))
                throw new GeneralSecurityException("Key version mismatch");
            byte[] plain = encryption.decrypt(key.asSecretKey(), components[1], components[0]);

            ClearText ct = new ClearText();
            ct.setBytes(plain);
            ct.setProperties((Properties) secret.getProperties().clone());
            if (secret.getProtectedProperties() != null) {
                components = secret.getProtectedProperties().split("\\$");
                if (components.length != 4) {
                    throw new IllegalStateException("The protected properties should have been formatted as 'iv$encypted$alias$version");
                }

                if (!components[2].equalsIgnoreCase(key.alias()))
                    throw new GeneralSecurityException("Key alias mismatch");
                if (!components[3].equalsIgnoreCase(key.version()))
                    throw new GeneralSecurityException("Key version mismatch");

                byte[] props = encryption.decrypt(key.asSecretKey(), components[1], components[0]);

                Properties properties = mapper.readValue(new String(props, StandardCharsets.UTF_8), Properties.class);
                ct.setProtectedProperties(properties);
            }
            return ct;
        } catch (GeneralSecurityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GeneralSecurityException(ex);
        }
    }
}
