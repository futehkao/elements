/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.web;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Startable;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AllowSymLinkAliasChecker;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This code is derived from http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
 * Created by futeh.
 */
@SuppressWarnings("squid:CommentedOutCodeLine")
public class WebServer implements Startable {

    protected static Logger logger = Logger.getLogger();

    int maxIdleTime = 0;
    int maxThreads = 0;
    int minThreads = 0;
    protected org.eclipse.jetty.server.Server server;
    int httpPort = 0;
    int httpsPort = 0;
    String keyStoreFile;
    String keyStorePassword;
    protected String rootContext = "/";
    protected Map<String, Servlet> servlets = new LinkedHashMap<>();
    protected Map<String, String> webapps = new LinkedHashMap<>();

    protected void init() {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        if (maxThreads > 0)
            threadPool.setMaxThreads(maxThreads);
        if (minThreads > 0)
            threadPool.setMinThreads(minThreads);

        server = new org.eclipse.jetty.server.Server(threadPool);

        if (httpPort > 0) {
            // see http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
            // in the Embedding Connectors section
            ServerConnector http = new ServerConnector(server);
            // http.setHost("localhost");
            http.setPort(httpPort);
            // http.setIdleTimeout(30000);

            // Set the connector
            server.addConnector(http);
        }

        if (httpsPort > 0) {
            // see http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
            // in the Like Jetty XML section
            if (keyStoreFile == null) {
                throw logger.systemException("Null keystore");
            }

            // SSL Context Factory
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(keyStoreFile);
            sslContextFactory.setKeyStorePassword(keyStorePassword);
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
            httpsConfig.setSecurePort(httpsPort);
            httpsConfig.setSendDateHeader(false);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            // SSL Connector
            ServerConnector sslConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                    new HttpConnectionFactory(httpsConfig));
            sslConnector.setPort(httpsPort);
            server.addConnector(sslConnector);
        }
    }

    public void start() {
        init();

        HandlerCollection handlers = new HandlerCollection();
        initServlets(handlers);
        initWebApps(handlers);
        server.setHandler(handlers);
        try {
            server.start();
        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }

    protected void initServlets(HandlerCollection handlers) {
        if (servlets.size() > 0) {
            ServletContextHandler handler = new ServletContextHandler(server, rootContext);
            for (Map.Entry<String, Servlet> entry : servlets.entrySet()) {
                handler.addServlet(new ServletHolder(entry.getValue()), entry.getKey());
            }
            handlers.addHandler(handler);
        }
    }

    protected void initWebApps(HandlerCollection handlers) {
        // see http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
        // in Embedding Web Applications section
        if (webapps.size() > 0) {
            for (Map.Entry<String, String> entry : webapps.entrySet()) {
                String context = entry.getKey();
                WebAppContext webapp = new WebAppContext();
                String root = rootContext;
                while (root.endsWith("/")) {
                    root = root.substring(0, root.length() - 1);
                }
                String fullContext = (context.startsWith("/")) ? root + context : root + "/" + context;
                webapp.setContextPath(fullContext);
                File warFile = new File(entry.getValue());
                webapp.setWar(warFile.getAbsolutePath());
                webapp.addAliasCheck(new AllowSymLinkAliasChecker());
                handlers.addHandler(webapp);
            }
        }
    }

    public void addServlet(String context, Servlet servlet) {
        servlets.put(context, servlet);
    }

    public void addWebApp(String context, String path) {
        webapps.put(context, path);
    }

    public void addWebApps(String path) {
        if (Paths.get(path).toFile().isDirectory()) {
            try (Stream<Path> stream = Files.list(Paths.get(path))) {
                stream.forEach(subdir -> {
                    String context = subdir.getFileName().toString();
                    webapps.put(context, subdir.toFile().getAbsolutePath());
                });
            } catch (IOException e) {
                logger.systemException(e);
            }
        }
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    /**
     * setting threadPool's max threads
     * @param maxThreads max threads
     */
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getMinThreads() {
        return minThreads;
    }

    /**
     * setting threadPool's min threads.
     * @param minThreads min threads
     */
    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    /**
     * setting the HTTP port
     * @return HTTP port
     */
    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    /**
     * Setting the HTTPS port.  If this is set, you must set the keyStoreFile
     * @return HTTPS port
     */
    public long getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(int httpsPort) {
        this.httpsPort = httpsPort;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getRootContext() {
        return rootContext;
    }

    /**
     * Setting the rootContext
     *
     * @param rootContext root context
     */
    public void setRootContext(String rootContext) {
        this.rootContext = rootContext;
    }
}
