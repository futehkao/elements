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

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;

public class SSLServerSocketConfig extends SSLBaseConfig {
    private String[] enabledProtocols;
    private String[] enabledCipherSuites;
    private SSLServerSocketFactory sslServerSocketFactory;

    public SSLServerSocketFactory getSSLServerSocketFactory() throws GeneralSecurityException, IOException {
        if (sslServerSocketFactory != null)
            return sslServerSocketFactory;
        sslServerSocketFactory  = new JavaKeyStore(getKeyStore(), getKeyStorePassword(), getKeyStoreFormat())
                .includeSystem(isIncludeSystem())
                .init(getKeyManagerPassword())
                .createServerSocketFactory(getTlsProtocol());
        erasePasswords();
        return sslServerSocketFactory;
    }

    public ServerSocket createServerSocket(int port) throws GeneralSecurityException, IOException {
        if (getKeyStore() != null) {
            SSLServerSocket sslServerSocket = (SSLServerSocket) getSSLServerSocketFactory().createServerSocket(port);

            if (enabledProtocols != null) {
                sslServerSocket.setEnabledProtocols(enabledProtocols);
            }
            if (enabledCipherSuites != null) {
                sslServerSocket.setEnabledCipherSuites(enabledCipherSuites);
            }
            return sslServerSocket;
        } else {
            return new ServerSocket(port);
        }
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    public void setEnabledCipherSuites(String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }
}
