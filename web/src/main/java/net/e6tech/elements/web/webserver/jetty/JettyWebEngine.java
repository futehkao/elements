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

package net.e6tech.elements.web.webserver.jetty;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.web.webserver.WebEngine;
import net.e6tech.elements.web.webserver.WebServer;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.Servlet;
import java.util.Map;

public class JettyWebEngine implements WebEngine {

    private static Logger logger = Logger.getLogger();

    private int maxThreads = 0;
    private int minThreads = 0;

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    public Server init(WebServer webServer) {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        if (maxThreads > 0)
            threadPool.setMaxThreads(maxThreads);
        if (minThreads > 0)
            threadPool.setMinThreads(minThreads);

        Server server = webServer.computeServerData(() -> new Server(threadPool));

        if (webServer.getHttpPort() >= 0) {
            // see http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
            // in the Embedding Connectors section
            ServerConnector http = new ServerConnector(server);
            // http.setHost("localhost");
            http.setHost(webServer.getHost());
            http.setPort(webServer.getHttpPort());
            // http.setIdleTimeout(30000);

            // Set the connector
            server.addConnector(http);
        }

        if (webServer.getHttpsPort() >= 0) {
            // see http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
            // in the Like Jetty XML section
            if (webServer.getKeyStoreFile() == null) {
                throw logger.systemException("Null keystore");
            }

            // SSL Context Factory
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStoreType(webServer.getKeyStoreFormat());
            sslContextFactory.setKeyStorePath(webServer.getKeyStoreFile());
            sslContextFactory.setKeyStorePassword(new String(webServer.getKeyStorePassword()));
            sslContextFactory.setKeyManagerPassword(new String(webServer.getKeyManagerPassword()));
            sslContextFactory.setProtocol(webServer.getSslProtocol());

            // sslContextFactory.setKeyManagerPassword();
            // sslContextFactory.setTrustStorePath(jetty_home + "/../../../jetty-server/src/test/config/etc/keystore");
            // sslContextFactory.setTrustStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
            sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                    "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                    "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

            // SSL HTTP Configuration

            HttpConfiguration httpsConfig = new HttpConfiguration();
            httpsConfig.setSecureScheme("https");
            httpsConfig.setSecurePort(webServer.getHttpsPort());
            httpsConfig.setSendDateHeader(false);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            // SSL Connector
            ServerConnector sslConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                    new HttpConnectionFactory(httpsConfig));
            sslConnector.setPort(webServer.getHttpsPort());
            sslConnector.setHost(webServer.getHost());

            String value = webServer.getClientAuth();
            if ("true".equalsIgnoreCase(value) ||
                    "yes".equalsIgnoreCase(value) ||
                    "require".equalsIgnoreCase(value) ||
                    "required".equalsIgnoreCase(value)) {
                sslContextFactory.setNeedClientAuth(true);
            } else if ("optional".equalsIgnoreCase(value) ||
                    "want".equalsIgnoreCase(value)) {
                sslContextFactory.setWantClientAuth(true);
            } else if ("false".equalsIgnoreCase(value) ||
                    "no".equalsIgnoreCase(value) ||
                    "none".equalsIgnoreCase(value) ||
                    value == null) {
                sslContextFactory.setTrustAll(true);
            } else {
                // Could be a typo. Don't default to NONE since that is not
                // secure. Force user to fix config. Could default to REQUIRED
                // instead.
                throw new IllegalArgumentException("Invalid ClientAuth value: " + value);
            }

            server.addConnector(sslConnector);
        }
        return server;
    }

    public void start(WebServer webServer) {
        Server server = init(webServer);
        HandlerCollection handlers = new HandlerCollection();
        initServlets(webServer, server, handlers);
        server.setHandler(handlers);
        try {
            server.start();
        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }

    public void stop(WebServer webServer) {
        Server server = webServer.getServerData();
        if (server == null)
            return;
        try {
            server.stop();
            server.destroy();
            webServer.setServerData(null);
        } catch (Exception ex) {
            logger.warn("Cannot stop Jetty {}:{}", webServer.getHost(), webServer.getHttpPort());
        }
    }

    protected void initServlets(WebServer webServer, Server server, HandlerCollection handlers) {
        if (webServer.getServlets().size() > 0) {
            ServletContextHandler handler = new ServletContextHandler(server, webServer.getRootContext());
            for (Map.Entry<String, Servlet> entry : webServer.getServlets().entrySet()) {
                handler.addServlet(new ServletHolder(entry.getValue()), entry.getKey());
            }
            handlers.addHandler(handler);
        }
    }

}
