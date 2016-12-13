/*
 * Copyright 2015 Futeh Kao
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

/**
 * Created by futeh.
 */
public class SelfSignedCert {

    private String alias = "cert";
    private String DN = "CN=localhost.net,OU=IT,O=Unemployed,L=Austin,ST=Texas,C=US";
    private int expiration = 3; // 3 years
    private JCEKS jceks;

    public SelfSignedCert() {
    }

    public void init() throws GeneralSecurityException {
        char[] password = Password.generateRandomPassword(9, 15);
        jceks = new JCEKS();
        jceks.createSelfSignedCertificate(alias, DN, password, expiration);
        jceks.init(password);
    }

    public void init(JCEKS jceks) {
        this.jceks = jceks;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDN() {
        return DN;
    }

    public void setDN(String DN) {
        this.DN = DN;
    }

    public int getExpiration() {
        return expiration;
    }

    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    public KeyManager[] getKeyManagers() {
        return jceks.getKeyManagers();
    }

    public TrustManager[] getTrustManagers() {
        return jceks.getTrustManagers();
    }

}
