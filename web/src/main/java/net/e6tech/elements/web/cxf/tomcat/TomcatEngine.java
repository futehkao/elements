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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.web.cxf.CXFServer;
import net.e6tech.elements.web.cxf.JaxRSServlet;
import net.e6tech.elements.web.cxf.ServerController;
import net.e6tech.elements.web.cxf.ServerEngine;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is design to start CXFServer using Tomcat.
 * It is designed to contain only configuration data but stateless
 * in respect to the Tomcat servers it has started so that it can be shared
 * by more than one CXFServers.
 * Therefore, Tomcat servers are stored in CXFServer's serverEngineData
 */
public class TomcatEngine extends ServerEngine {

    private static Logger logger = Logger.getLogger();

    private int maxThreads = 250;
    private int minSpareThreads = 10;
    private int maxConnections = 10000;
    private String baseDir;

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void start(CXFServer cxfServer, ServerController<?> controller) {
        List<Tomcat> tomcats = cxfServer.computeServerEngineData(LinkedList::new);
        Tomcat tomcat = new Tomcat();
        tomcats.add(tomcat);

        JaxRSServlet servlet = new JaxRSServlet((JAXRSServerFactoryBean) controller.getFactory());
        String context = controller.getURL().getPath();
        if (context.endsWith("/"))
            context = context.substring(0, context.length() - 1);
        Context ctx = tomcat.addContext(context, null);
        tomcat.addServlet(context, "jaxrs", servlet);

        if (baseDir != null) {
            tomcat.setBaseDir(baseDir + "." + controller.getURL().getPort());
        }

        // host name port and context, e.g, http://0.0.0.0:8080/restful are controlled by Tomcat.
        // JaxRSServer sets the addresse to "/" so that servlet mapping needs to
        // map "/*" to jaxrs.
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

    public void stop(CXFServer cxfServer) {
        List<Tomcat> tomcats = cxfServer.computeServerEngineData(LinkedList::new);
        Iterator<Tomcat> iterator = tomcats.iterator();
        while (iterator.hasNext()) {
            Tomcat tomcat = iterator.next();
            try {
                tomcat.stop();
                tomcat.destroy();
                iterator.remove();
            } catch (Exception ex) {
                StringBuilder builder = new StringBuilder();
                for (Container container : tomcat.getHost().findChildren()) {
                    if (container instanceof Context) {
                        builder.append(" " +((Context) container).getPath());
                    }
                }
                logger.warn("Cannot stop Tomcat" + builder.toString());
            }
        }
    }

    protected Connector createConnector(CXFServer cxfServer, URL url) {
        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(url.getPort());
        connector.setAttribute("maxThreads", maxThreads);  // default 200
        connector.setAttribute("maxConnections", maxConnections); // default 10000
        connector.setAttribute("minSpareThreads", minSpareThreads); // default 10

        if ("https".equals(url.getProtocol())) {
            connector.setSecure(true);
            connector.setScheme("https");
            connector.setAttribute("protocol", "HTTP/1.1");
            connector.setAttribute("SSLEnabled", true);
            connector.setAttribute("defaultSSLHostConfigName", url.getHost());
            if (cxfServer.getKeyStoreFile() == null &&
                cxfServer.getKeyStore() == null &&
                cxfServer.getSelfSignedCert() == null)
                throw new IllegalArgumentException("Missing keyStoreFile or keyStore");

            SSLHostConfig config = new SSLHostConfig();
            config.setHostName(url.getHost());

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

            if (cxfServer.getClientAuth() != null)
                config.setCertificateVerification(cxfServer.getClientAuth());
            config.setSslProtocol(cxfServer.getSslProtocol());
            connector.addSslHostConfig(config);

        }
        return connector;
    }
}
