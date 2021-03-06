/*
 * Copyright 2015-2020 Futeh Kao
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

import java.util.Arrays;

public abstract class SSLBaseConfig {
    private String tlsProtocol = "TLSv1.2";
    private String keyStore;
    private String keyStoreFormat = JavaKeyStore.DEFAULT_FORMAT;
    private char[] keyStorePassword;
    private char[] keyManagerPassword;
    private boolean includeSystem = true;
    private boolean erasePasswords = true;

    protected void erasePasswords() {
        if (erasePasswords && keyStorePassword != null) {
            Arrays.fill(keyStorePassword, (char) 0);
            keyStorePassword = null;
        }
        if (erasePasswords && keyManagerPassword != null) {
            Arrays.fill(keyManagerPassword, (char) 0);
            keyManagerPassword = null;
        }
    }

    public String getTlsProtocol() {
        return tlsProtocol;
    }

    public void setTlsProtocol(String tlsProtocol) {
        this.tlsProtocol = tlsProtocol;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStoreFormat() {
        return keyStoreFormat;
    }

    public void setKeyStoreFormat(String keyStoreFormat) {
        this.keyStoreFormat = keyStoreFormat;
    }

    public char[] getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(char[] keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public char[] getKeyManagerPassword() {
        return keyManagerPassword;
    }

    public void setKeyManagerPassword(char[] keyManagerPassword) {
        this.keyManagerPassword = keyManagerPassword;
    }

    public boolean isIncludeSystem() {
        return includeSystem;
    }

    public void setIncludeSystem(boolean includeSystem) {
        this.includeSystem = includeSystem;
    }

    public boolean isErasePasswords() {
        return erasePasswords;
    }

    public void setErasePasswords(boolean erasePasswords) {
        this.erasePasswords = erasePasswords;
    }
}
