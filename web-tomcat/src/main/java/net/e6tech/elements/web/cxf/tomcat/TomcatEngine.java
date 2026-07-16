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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
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

import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class is design to start CXFServer using Tomcat.
 * It is designed to contain only configuration data but stateless
 * in respect to the Tomcat servers it has started so that it can be shared
 * by more than one CXFServers.
 * Therefore, Tomcat servers are stored in CXFServer's serverEngineData
 */
public class TomcatEngine implements ServerEngine {

    private static Logger logger = Logger.getLogger();

    private int maxThreads = 250;
    private int minSpareThreads = 10;
    private int maxConnections = 10000;
    private String baseDir;
    private Provision provision;
    private boolean useThreadPool = false;
    private TomcatSSL tomcatSSL = new TomcatSSL();

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

    public Provision getProvision() {
        return provision;
    }

    @Inject(optional = true)
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public boolean isUseThreadPool() {
        return useThreadPool;
    }

    public void setUseThreadPool(boolean useThreadPool) {
        this.useThreadPool = useThreadPool;
    }

    public TomcatSSL getTomcatSSL() {
        return tomcatSSL;
    }

    @Inject(optional = true)
    public void setTomcatSSL(TomcatSSL tomcatSSL) {
        this.tomcatSSL = tomcatSSL;
    }

    @Override
    public void start(CXFServer cxfServer, ServerController<?> controller) {
        List<Tomcat> tomcats = cxfServer.computeServerEngineData(LinkedList::new);
        Tomcat tomcat = new Tomcat();
        tomcats.add(tomcat);
        tomcat.setHostname(controller.getURL().getHost());
        if (baseDir != null) {
            tomcat.setBaseDir(baseDir + "." + controller.getURL().getPort());
        }

        JaxRSServlet servlet = new JaxRSServlet((JAXRSServerFactoryBean) controller.getFactory());
        String context = controller.getURL().getPath();
        if (context.endsWith("/"))
            context = context.substring(0, context.length() - 1);
        Context ctx = tomcat.addContext(context, null);
        tomcat.addServlet(context, "jaxrs", servlet);

        // host name port and context, e.g, http://0.0.0.0:8080/restful are controlled by Tomcat.
        // JaxRSServer sets the addresse to "/" so that servlet mapping needs to
        // map "/*" to jaxrs.
        ctx.addServletMappingDecoded("/*", "jaxrs");

        try {
            Connector connector = createConnector(cxfServer, controller.getURL());
            if (provision != null && useThreadPool) {
                Executor executor = provision.getExecutor();
                connector.getProtocolHandler().setExecutor(executor);
            }
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
            } catch (Exception ex) {
                StringBuilder builder = new StringBuilder();
                for (Container container : tomcat.getHost().findChildren()) {
                    if (container instanceof Context) {
                        builder.append(" " + ((Context) container).getPath());
                    }
                }
                logger.warn("Cannot stop Tomcat for path{} - {}: {}", builder, ex.getMessage(), ex.getCause().getMessage());
            }
            iterator.remove();
        }
    }

    @SuppressWarnings("squid:S3776")
    protected Connector createConnector(CXFServer cxfServer, URL url) {
        Connector connector = tomcatSSL.createConnector();
        connector.setPort(url.getPort());
        connector.setProperty("maxThreads", String.valueOf(maxThreads));  // default 200
        connector.setProperty("maxConnections", String.valueOf(maxConnections)); // default 10000
        connector.setProperty("minSpareThreads", String.valueOf(minSpareThreads)); // default 10
        connector.setProperty("address", url.getHost());
        tomcatSSL.initialize(cxfServer, url, connector);
        return connector;
    }
}
