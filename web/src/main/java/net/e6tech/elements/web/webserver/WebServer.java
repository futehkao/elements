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

package net.e6tech.elements.web.webserver;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.JavaKeyStore;

import javax.servlet.Servlet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class WebServer implements Startable {

    protected static Logger logger = Logger.getLogger();

    private int httpPort = 0;
    private int httpsPort = 0;
    private String keyStoreFile;
    private String keyStoreFormat = JavaKeyStore.JKS_FORMAT;
    private char[] keyStorePassword;
    private char[] keyManagerPassword;
    private String sslProtocol = "TLS";
    private String rootContext = "/";
    private Map<String, Servlet> servlets = new LinkedHashMap<>();
    private Map<String, String> servletClasses = new LinkedHashMap<>();
    private ClassLoader classLoader;
    private WebEngine engine;
    private Class<WebEngine> engineClass;
    private Object serverData;
    private String host = "0.0.0.0";
    private String clientAuth;
    private Provision provision;
    private boolean started = false;

    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        WebServer.logger = logger;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getHttpsPort() {
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

    public String getKeyStoreFormat() {
        return keyStoreFormat;
    }

    public void setKeyStoreFormat(String keyStoreFormat) {
        this.keyStoreFormat = keyStoreFormat;
    }

    public char[] getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(char[] keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public char[] getKeyManagerPassword() {
        return keyManagerPassword;
    }

    public void setKeyManagerPassword(char[] keyManagerPassword) {
        this.keyManagerPassword = keyManagerPassword;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getRootContext() {
        return rootContext;
    }

    public void setRootContext(String rootContext) {
        this.rootContext = rootContext;
    }

    public Map<String, Servlet> getServlets() {
        return servlets;
    }

    public void setServlets(Map<String, Servlet> servlets) {
        this.servlets = servlets;
    }

    public Map<String, String> getServletClasses() {
        return servletClasses;
    }

    public void setServletClasses(Map<String, String> servletClasses) {
        this.servletClasses = servletClasses;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public WebEngine getEngine() {
        return engine;
    }

    @Inject(optional = true)
    public void setEngine(WebEngine engine) {
        this.engine = engine;
    }

    public Class<WebEngine> getEngineClass() {
        return engineClass;
    }

    public void setEngineClass(Class<WebEngine> engineClass) {
        this.engineClass = engineClass;
    }

    public void addServlet(String context, Servlet servlet) {
        servlets.put(context, servlet);
    }

    public <T> T getServerData() {
        return (T) serverData;
    }

    public void setServerData(Object serverData) {
        this.serverData = serverData;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(String clientAuth) {
        this.clientAuth = clientAuth;
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public <T> T computeServerData(Supplier<T> supplier) {
        if (serverData == null)
            setServerData(supplier.get());
        return (T) serverData;
    }

    public boolean isStarted() {
        return started;
    }

    public void start() {

        if (started)
            return;
        started = true;

        getProvision().getResourceManager().onShutdown("WebServer " + getHost(), notification -> stop());

        for (Map.Entry<String, String> entry : getServletClasses().entrySet()) {
            try {
                ClassLoader loader = classLoader != null ? classLoader : getProvision().getClass().getClassLoader() ;
                Class servletClass = loader.loadClass(entry.getValue());
                Servlet servlet = (Servlet) servletClass.getDeclaredConstructor().newInstance();
                addServlet(entry.getKey(), servlet);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        if (engine == null) {
            try {
                Class cls = (engineClass != null) ? engineClass :
                        getClass().getClassLoader().loadClass("net.e6tech.elements.web.webserver.jetty.JettyWebEngine");
                engine = (WebEngine) cls.getConstructor().newInstance();
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }
        engine.start(this);
    }

    public void stop() {
        engine.stop(this);
        started = false;
    }
}
