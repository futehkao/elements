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

package net.e6tech.elements.web.cxf.jetty;

import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.web.cxf.CXFServer;
import net.e6tech.elements.web.cxf.ServerController;
import net.e6tech.elements.web.cxf.ServerEngine;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * This class is design to start CXFServer using Jetty.
 * It is designed to contain only configuration data but stateless
 * in respect to the Jetty servers it has started so that it can be shared
 * by more than one CXFServers.
 * Therefore, Jetty servers are stored in CXFServer's serverEngineData
 */
public class JettyEngine implements ServerEngine {

    private static Logger logger = Logger.getLogger();

    private QueuedThreadPool queuedThreadPool;
    private boolean useThreadPool = false;
    private Provision provision;
    private WorkerPoolConfig workerPoolConfig = new WorkerPoolConfig();
    private JettySSL jettySSL = new JettySSL();

    public QueuedThreadPool getQueuedThreadPool() {
        return queuedThreadPool;
    }

    @Inject(optional = true)
    public void setQueuedThreadPool(QueuedThreadPool queuedThreadPool) {
        this.queuedThreadPool = queuedThreadPool;
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

    public WorkerPoolConfig getWorkerPoolConfig() {
        return workerPoolConfig;
    }

    @Inject(optional = true)
    public void setWorkerPoolConfig(WorkerPoolConfig workerPoolConfig) {
        this.workerPoolConfig = workerPoolConfig;
    }

    public JettySSL getJettySSL() {
        return jettySSL;
    }

    @Inject(optional = true)
    public void setJettySSL(JettySSL jettySSL) {
        this.jettySSL = jettySSL;
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    @Override
    public void onException(Message message, CallFrame frame, Throwable th) {
        // org.eclipse.jetty.server.Request request = ( org.eclipse.jetty.server.Request) message.get(AbstractHTTPDestination.HTTP_REQUEST);
        // request.setHandled(true);
    }

    public void start(CXFServer cxfServer, ServerController<?> controller) {
        List<Server> servers = cxfServer.computeServerEngineData(LinkedList::new);
        try {
            jettySSL.initialize(cxfServer);
        } catch (Exception th) {
            throw new SystemException(th);
        }

        Server server;
        if (controller.getFactory() instanceof JAXRSServerFactoryBean) {
            JAXRSServerFactoryBean bean = (JAXRSServerFactoryBean) controller.getFactory();
            bean.setStart(false);
            server = bean.create();
        } else if (controller.getFactory() instanceof JaxWsServerFactoryBean) {
            JaxWsServerFactoryBean bean = (JaxWsServerFactoryBean) controller.getFactory();
            bean.setStart(false);
            server = bean.create();
        } else {
            throw new SystemException("Don't know how to start " + controller.getFactory().getClass());
        }

        servers.add(server);

        // set up thread pool
        Destination dest = server.getDestination();
        JettyHTTPServerEngine engine = null;
        if (dest instanceof JettyHTTPDestination) {
            JettyHTTPDestination jetty = (JettyHTTPDestination) dest;
            if (jetty.getEngine() instanceof JettyHTTPServerEngine) {
                engine = (JettyHTTPServerEngine) jetty.getEngine();
                engine.setSendServerVersion(cxfServer.isSendServerVersion());
            }
        }

        // need to start before swapping out thread pool.
        // The server doesn't like it if otherwise.
        server.start();

        if (engine != null) {
            startThreadPool(engine);
        }
    }

    private void startThreadPool(JettyHTTPServerEngine engine) {
        if (queuedThreadPool != null) {
            engine.setThreadPool(queuedThreadPool);
        } else if (useThreadPool && provision != null && provision.getExecutor() instanceof ThreadPoolExecutor) {
            try {
                engine.setThreadPool(new ExecutorThreadPool((ThreadPoolExecutor)provision.getExecutor()));
            } catch (Exception ex) {
                logger.warn("Cannot start ActorThreadPool", ex);
            }
        } else {
            queuedThreadPool = new QueuedThreadPool(256, 10, 120000);
            engine.setThreadPool(queuedThreadPool);
        }
    }

    public void stop(CXFServer cxfServer) {
        List<Server> servers = cxfServer.computeServerEngineData(LinkedList::new);
        Iterator<Server> iterator = servers.iterator();
        while (iterator.hasNext()) {
            Server server = iterator.next();
            try {
                server.stop();
                server.destroy();
                JettyHTTPDestination jettyDest = (JettyHTTPDestination) server.getDestination();
                JettyHTTPServerEngine jettyEngine = (JettyHTTPServerEngine) jettyDest.getEngine();
                jettyEngine.shutdown();
                iterator.remove();
            } catch (Exception ex) {
                logger.warn("Cannot stop Jetty {}", server.getDestination().getAddress().getAddress().getValue());
            }
        }
    }
}
