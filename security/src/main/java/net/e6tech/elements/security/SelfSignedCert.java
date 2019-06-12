/*
 * Copyright 2015-2019 Futeh Kao
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

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Created by futeh.
 */
public class SelfSignedCert {

    private String alias = "cert";
    private String dn = "CN=localhost.net,OU=IT,O=Unemployed,L=Austin,ST=Texas,C=US";
    private int expiration = 3; // 3 years
    private JavaKeyStore javaKeyStore;
    private String format = JavaKeyStore.DEFAULT_FORMAT;
    private char[] password = Password.generateRandomPassword(9, 15);

    public void init() throws GeneralSecurityException {
        if (password == null)
            password = Password.generateRandomPassword(9, 15);
        javaKeyStore = new JavaKeyStore(format);
        javaKeyStore.createSelfSignedCertificate(alias, dn, password, expiration);
        javaKeyStore.init(password);
    }

    public char[] getPassword() {
        return password;
    }

    public void init(JavaKeyStore javaKeyStore) {
        this.javaKeyStore = javaKeyStore;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDN() {
        return dn;
    }

    public void setDN(String dn) {
        this.dn = dn;
    }

    public int getExpiration() {
        return expiration;
    }

    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public KeyManager[] getKeyManagers() {
        return javaKeyStore.getKeyManagers();
    }

    public TrustManager[] getTrustManagers() {
        return javaKeyStore.getTrustManagers();
    }

    public KeyStore getKeyStore() {
        return javaKeyStore.getKeyStore();
    }

}
