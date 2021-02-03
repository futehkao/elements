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

import javax.net.ssl.*;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class SSLSocketConfig extends SSLBaseConfig {
    private boolean skipCertCheck = false;
    private static final X509Certificate[] EMPTY_CERTIFICATES = new X509Certificate[0];
    private SSLSocketFactory sslSocketFactory;

    public SSLSocketFactory getSSLSocketFactory() throws GeneralSecurityException, IOException {
        if (sslSocketFactory != null)
            return sslSocketFactory;
        TrustManager[] trustManagers;
        KeyManager[] keyManagers = null;
        if (skipCertCheck) {
            trustManagers = new TrustManager[]{new AcceptAllTrustManager()};
        } else {
            if (getKeyStore() != null) {
                JavaKeyStore javaKeyStore = new JavaKeyStore(getKeyStore(), getKeyStorePassword(), getKeyStoreFormat())
                        .includeSystem(isIncludeSystem())
                        .init(getKeyManagerPassword());
                trustManagers = javaKeyStore.getTrustManagers();
                keyManagers = javaKeyStore.getKeyManagers();
            } else {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                trustManagers = trustManagerFactory.getTrustManagers();
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(null, null);
                keyManagers = keyManagerFactory.getKeyManagers();
            }
        }

        erasePasswords();
        SSLContext ctx;
        ctx = SSLContext.getInstance(getTlsProtocol());
        ctx.init(keyManagers, trustManagers, null);
        sslSocketFactory = ctx.getSocketFactory();
        return sslSocketFactory;
    }

    public boolean isSkipCertCheck() {
        return skipCertCheck;
    }

    public void setSkipCertCheck(boolean skipCertCheck) {
        this.skipCertCheck = skipCertCheck;
    }

    @SuppressWarnings("squid:S4424")
    public class AcceptAllTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // do nothing
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // do nothing
        }

        public X509Certificate[] getAcceptedIssuers() {
            return EMPTY_CERTIFICATES;
        }
    }
}
