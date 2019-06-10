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

package net.e6tech.elements.web.cxf.tomcat;

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.web.cxf.CXFServer;
import net.e6tech.elements.web.cxf.JaxRSServlet;
import net.e6tech.elements.web.cxf.ServerController;
import net.e6tech.elements.web.cxf.ServerEngine;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.tomcat.util.net.SSLHostConfig;

import java.net.URL;
import java.security.KeyStore;

public class TomcatEngine extends ServerEngine {

    private Tomcat tomcat;

    @Override
    public void start(CXFServer cxfServer, ServerController<?> controller) {
        if (tomcat == null)
            tomcat = new Tomcat();
        JaxRSServlet servlet = new JaxRSServlet((JAXRSServerFactoryBean) controller.getFactory());
        String context = controller.getURL().getPath();
        if (context.endsWith("/"))
            context = context.substring(0, context.length() - 1);
        Context ctx = tomcat.addContext(context, null);
        tomcat.addServlet(context, "jaxrs", servlet);
        ctx.addServletMappingDecoded("/*", "jaxrs");

        try {
            Connector connector = createConnector(cxfServer, controller.getURL());
            connector.setPort(controller.getURL().getPort());
            tomcat.setConnector(connector);
            tomcat.start();
        } catch (LifecycleException e) {
            throw new SystemException(e);
        }
    }

    public void stop() {
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
                tomcat = null;
            } catch (LifecycleException e) {
                throw new SystemException(e);
            }
        }
    }

    protected Connector createConnector(CXFServer cxfServer, URL url) {
        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(url.getPort());
        if ("https".equals(url.getProtocol())) {
            connector.setSecure(true);
            connector.setScheme("https");

            KeyStore keyStore = null;
            SSLHostConfig hostConfig = new SSLHostConfig();
            hostConfig.setHostName(url.getHost());
            hostConfig.setTruststoreType(cxfServer.getKeyStoreFormat());
            hostConfig.setTrustStore(keyStore);
            hostConfig.setCertificateVerification(cxfServer.getClientAuth());
            connector.addSslHostConfig(hostConfig);

            // connector.setAttribute("keyAlias", "tomcat");
            /*
            connector.setAttribute("keystorePass", "password");
            connector.setAttribute("keystoreType", "JKS");
            connector.setAttribute("keystoreFile", "keystore.jks");
            connector.setAttribute("clientAuth", "false");
            connector.setAttribute("protocol", "HTTP/1.1");
            connector.setAttribute("sslProtocol", "TLS");
            connector.setAttribute("maxThreads", "200");
            connector.setAttribute("protocol", "org.apache.coyote.http11.Http11AprProtocol");
            connector.setAttribute("SSLEnabled", true); */
        }
        return connector;
    }
}
