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


import java.io.Serializable;
import java.util.Properties;

import static net.e6tech.elements.security.vault.Constants.ALIAS;
import static net.e6tech.elements.security.vault.Constants.VERSION;

/**
 * Created by futeh.
 */
public class Secret implements Serializable, Cloneable {

    private static final long serialVersionUID = -5640813231783061396L;
    private Properties properties;
    private String protectedProperties;
    private String secret;

    public String getProperty(String key) {
        if (properties == null) return null;
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
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String keyAlias() {
        String[] components = secret.split("\\$");
        return components[2];
    }

    public String keyVersion() {
        String[] components = secret.split("\\$");
        return components[3];
    }

    public String alias() {
        return getProperty(ALIAS);
    }

    public String version() {
        return getProperty(VERSION);
    }

    public Secret clone() {
        try {
            Secret secret = (Secret) super.clone();
            secret.properties = null;
            if (properties != null) {
                secret.properties = new Properties();
                for (String key : properties.stringPropertyNames()) {
                    secret.properties.setProperty(key, properties.getProperty(key));
                }
            }
            return secret;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
