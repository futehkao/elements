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

package net.e6tech.elements.web.cxf.tomcat;

import net.e6tech.elements.web.cxf.CXFServer;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

import java.net.URL;
import java.util.Objects;

public class TomcatSSL {

    protected Connector createConnector() {
        return new Connector("org.apache.coyote.http11.Http11Nio2Protocol");
    }

    @SuppressWarnings("squid:S3776")
    protected Connector initialize(CXFServer cxfServer, URL url, Connector connector) {
        if ("https".equals(url.getProtocol())) {
            connector.setSecure(true);
            connector.setScheme("https");
            connector.setProperty("protocol", "HTTP/1.1");
            connector.setProperty("SSLEnabled", "true");
            connector.setProperty("defaultSSLHostConfigName", url.getHost());
            if (cxfServer.getKeyStoreFile() == null &&
                    cxfServer.getKeyStore() == null &&
                    cxfServer.getSelfSignedCert() == null)
                throw new IllegalArgumentException("Missing keyStoreFile or keyStore");

            SSLHostConfig config = new SSLHostConfig();
            config.setHostName(url.getHost()); // this needs to match defaultSSLHostConfigName attribute

            initializeHostConfig(cxfServer, connector, config);

            if (cxfServer.getClientAuth() != null)
                config.setCertificateVerification(cxfServer.getClientAuth());

            config.setSslProtocol(cxfServer.getSslProtocol());
            customize(cxfServer, connector, config);
            connector.addSslHostConfig(config);
        }
        if (!cxfServer.isSendServerVersion())
            connector.setProperty("server", "Elements");
        return connector;
    }

    protected void initializeHostConfig(CXFServer cxfServer, Connector connector, SSLHostConfig config) {
        Objects.requireNonNull(connector);
        // only support keyStoreFile
        if (cxfServer.getKeyStoreFile() != null) {
            config.setCertificateKeystoreFile(cxfServer.getKeyStoreFile());
            if (cxfServer.getKeyStorePassword() != null)
                config.setCertificateKeystorePassword(new String(cxfServer.getKeyStorePassword()));
            if (cxfServer.getKeyManagerPassword() != null)
                config.setCertificateKeyPassword(new String(cxfServer.getKeyManagerPassword()));
            config.setCertificateKeystoreType(cxfServer.getKeyStoreFormat());
        } else if (cxfServer.getKeyStore() != null) {
            SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(config,  SSLHostConfigCertificate.Type.UNDEFINED);
            certificate.setCertificateKeystore(cxfServer.getKeyStore());
            if (cxfServer.getKeyStorePassword() != null)
                certificate.setCertificateKeystorePassword(new String(cxfServer.getKeyStorePassword()));
            if (cxfServer.getKeyManagerPassword() != null)
                certificate.setCertificateKeyPassword(new String(cxfServer.getKeyManagerPassword()));
            config.addCertificate(certificate);
        } else {
            SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(config,  SSLHostConfigCertificate.Type.UNDEFINED);
            certificate.setCertificateKeystore(cxfServer.getSelfSignedCert().getKeyStore());
            certificate.setCertificateKeystorePassword(new String(cxfServer.getSelfSignedCert().getPassword()));
            certificate.setCertificateKeyPassword(new String(cxfServer.getSelfSignedCert().getPassword()));
            config.addCertificate(certificate);
        }
    }

    protected void customize(CXFServer cxfServer, Connector connector, SSLHostConfig config) {
        // to be overridden by subclass if necessary.
    }
}
