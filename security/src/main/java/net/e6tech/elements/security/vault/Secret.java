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
package net.e6tech.elements.security.vault;


import java.io.Serializable;
import java.util.Properties;

import static net.e6tech.elements.security.vault.Constants.ALIAS;
import static net.e6tech.elements.security.vault.Constants.VERSION;

/**
 * Created by futeh.
 */
public class Secret implements Serializable {

    private static final long serialVersionUID = -5640813231783061396L;
    private Properties properties;
    private String protectedProperties;
    private String encryptedSecret;

    public Secret() {
    }

    public Secret(Secret secret) {
        this.properties = secret.properties;
        this.protectedProperties = secret.protectedProperties;
        this.encryptedSecret = secret.encryptedSecret;

        // copy properties from secret to this
        if (secret.properties != null) {
            this.properties = new Properties();
            for (String key : secret.properties.stringPropertyNames()) {
                this.properties.setProperty(key, secret.properties.getProperty(key));
            }
        }
    }

    public String getProperty(String key) {
        if (properties == null)
            return null;
        return properties.getProperty(key);
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public String getProtectedProperties() {
        return protectedProperties;
    }

    public void setProtectedProperties(String protectedProperties) {
        this.protectedProperties = protectedProperties;
    }

    public String getSecret() {
        return encryptedSecret;
    }

    public void setSecret(String secret) {
        this.encryptedSecret = secret;
    }

    public String keyAlias() {
        String[] components = encryptedSecret.split("\\$");
        return components[2];
    }

    public String keyVersion() {
        String[] components = encryptedSecret.split("\\$");
        return components[3];
    }

    public String alias() {
        return getProperty(ALIAS);
    }

    public String version() {
        return getProperty(VERSION);
    }

}
