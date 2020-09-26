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

package net.e6tech.elements.web.webserver.tomcat;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.web.webserver.WebEngine;
import net.e6tech.elements.web.webserver.WebServer;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.net.SSLHostConfig;

import javax.servlet.Servlet;
import java.net.URL;
import java.util.Map;


public class TomcatWebEngine implements WebEngine {
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
    public void start(WebServer server) {
        Tomcat tomcat = server.computeServerData(Tomcat::new);
        tomcat.setHostname(server.getHost());
        registerServlets(server, tomcat);

        if (baseDir != null) {
            if (server.getHttpPort() > 0)
                tomcat.setBaseDir(baseDir + "." + server.getHttpPort());
            else if (server.getHttpsPort() > 0)
                tomcat.setBaseDir(baseDir + "." + server.getHttpsPort());
        }

        if (server.getHttpPort() >= 0) {
            try {
                Connector connector = createConnector(server, new URL("http://" + server.getHost() + ":" + server.getHttpPort()));
                connector.setPort(server.getHttpPort());
                tomcat.setConnector(connector);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        if (server.getHttpsPort() >= 0) {
            try {
                Connector connector = createConnector(server, new URL("https://" + server.getHost() + ":" + server.getHttpsPort()));
                connector.setPort(server.getHttpsPort());
                tomcat.setConnector(connector);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        try {
            tomcat.start();
        } catch (LifecycleException e) {
            throw new SystemException(e);
        }
    }

    public void stop(WebServer webServer) {
        Tomcat tomcat = webServer.getServerData();
        if (tomcat == null)
            return;
        try {
            tomcat.stop();
            tomcat.destroy();
            webServer.setServerData(null);
        } catch (Exception ex) {
            logger.warn("Cannot stop Tomcat {}:{}/{}", webServer.getHost(), webServer.getHttpPort(), webServer.getHttpsPort());
        }
    }

    private void registerServlets(WebServer server, Tomcat tomcat) {
        for (Map.Entry<String, Servlet> entry : server.getServlets().entrySet()) {
            Context ctx = tomcat.addContext(entry.getKey(), null);
            tomcat.addServlet(entry.getKey(), "servlet", entry.getValue());
            ctx.addServletMappingDecoded("/*", "servlet");
        }
    }

    protected Connector createConnector(WebServer server, URL url) {
        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(url.getPort());
        connector.setProperty("maxThreads", String.valueOf(maxThreads));  // default 200
        connector.setProperty("maxConnections", String.valueOf(maxConnections)); // default 10000
        connector.setProperty("minSpareThreads", String.valueOf(minSpareThreads)); // default 10
        connector.setProperty("address", url.getHost());

        if ("https".equals(url.getProtocol())) {
            connector.setSecure(true);
            connector.setScheme("https");
            connector.setProperty("protocol", "HTTP/1.1");
            connector.setProperty("SSLEnabled", String.valueOf(true));
            connector.setProperty("defaultSSLHostConfigName", url.getHost());
            if (server.getKeyStoreFile() == null)
                throw new IllegalArgumentException("Missing keyStoreFile or keyStore");

            SSLHostConfig config = new SSLHostConfig();
            config.setHostName(url.getHost()); // this needs to match defaultSSLHostConfigName attribute

            // only support keyStoreFile
            if (server.getKeyStoreFile() != null) {
                config.setCertificateKeystoreFile(server.getKeyStoreFile());
                if (server.getKeyStorePassword() != null)
                    config.setCertificateKeystorePassword(new String(server.getKeyStorePassword()));
                if (server.getKeyManagerPassword() != null)
                    config.setCertificateKeyPassword(new String(server.getKeyManagerPassword()));
                config.setCertificateKeystoreType(server.getKeyStoreFormat());
            }

            if (server.getClientAuth() != null)
                config.setCertificateVerification(server.getClientAuth());
            config.setSslProtocol(server.getSslProtocol());
            connector.addSslHostConfig(config);

        }
        return connector;
    }
}
