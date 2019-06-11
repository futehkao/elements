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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.JavaKeyStore;
import net.e6tech.elements.security.SelfSignedCert;
import net.e6tech.elements.web.cxf.CXFServer;
import net.e6tech.elements.web.cxf.ServerController;
import net.e6tech.elements.web.cxf.ServerEngine;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.ClientAuthentication;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;

/**
 * This class is design to start CXFServer using Jetty.
 * It is designed to contain only configuration data but stateless
 * in respect to the Jetty servers it has started so that it can be shared
 * by more than one CXFServers.
 * Therefore, Jetty servers are stored in CXFServer's serverEngineData
 */
public class JettyEngine extends ServerEngine {

    private static Logger logger = Logger.getLogger();

    private QueuedThreadPool queuedThreadPool;

    public QueuedThreadPool getQueuedThreadPool() {
        return queuedThreadPool;
    }

    @Inject(optional = true)
    public void setQueuedThreadPool(QueuedThreadPool queuedThreadPool) {
        this.queuedThreadPool = queuedThreadPool;
    }

    public void start(CXFServer cxfServer, ServerController<?> controller) {
        List<Server> servers = cxfServer.computeServerEngineData(LinkedList::new);
        try {
            initKeyStore(cxfServer);
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
        if (queuedThreadPool != null) {
            Destination dest = server.getDestination();
            if (dest instanceof JettyHTTPDestination) {
                JettyHTTPDestination jetty = (JettyHTTPDestination) dest;
                if (jetty.getEngine() instanceof JettyHTTPServerEngine) {
                    ((JettyHTTPServerEngine) jetty.getEngine()).setThreadPool(queuedThreadPool);
                }
            }
        }
        cxfServer.setServerEngineData(servers);
        server.start();
    }

    public void stop(CXFServer cxfServer) {
        List<Server> servers = cxfServer.computeServerEngineData(LinkedList::new);
        Iterator<Server> iterator = servers.iterator();
        while (iterator.hasNext()) {
            Server server = iterator.next();
            try {
                server.stop();
                iterator.remove();
            } catch (Exception ex) {
                logger.warn("Cannot stop Jetty " + server.getDestination().getAddress().getAddress().getValue());
            }
        }
    }

    /* see http://aruld.info/programming-ssl-for-jetty-based-cxf-services/
        on how to setup TLS for CXF server.  The article also includes an example
        for setting up client TLS.
        We can use filters to control cipher suites<br>
           <code>FiltersType filter = new FiltersType();
           filter.getInclude().add(".*_EXPORT_.*");
           filter.getInclude().add(".*_EXPORT1024_.*");
           filter.getInclude().add(".*_WITH_DES_.*");
           filter.getInclude().add(".*_WITH_NULL_.*");
           filter.getExclude().add(".*_DH_anon_.*");
           tlsParams.setCipherSuitesFilter(filter);
           </code>
        */
    @SuppressWarnings({"squid:S3776", "squid:MethodCyclomaticComplexity", "squid:CommentedOutCodeLine"})
    public void initKeyStore(CXFServer server) throws GeneralSecurityException, IOException {
        String keyStoreFile = server.getKeyStoreFile();
        SelfSignedCert selfSignedCert = server.getSelfSignedCert();
        KeyStore keyStore = server.getKeyStore();

        if (keyStoreFile == null && selfSignedCert == null && keyStore == null)
            return;
        KeyManager[] keyManagers ;
        TrustManager[] trustManagers;
        if (keyStore != null || keyStoreFile != null) {
            JavaKeyStore jceKeyStore;
            if (keyStore != null) {
                jceKeyStore = new JavaKeyStore(keyStore);
            } else {
                jceKeyStore = new JavaKeyStore(keyStoreFile, server.getKeyStorePassword(), server.getKeyStoreFormat());
            }
            if (server.getKeyManagerPassword() == null)
                server.setKeyManagerPassword(server.getKeyStorePassword());
            jceKeyStore.init(server.getKeyManagerPassword());
            keyManagers = jceKeyStore.getKeyManagers();
            trustManagers = jceKeyStore.getTrustManagers();
        } else { // selfSignedCert
            keyManagers = selfSignedCert.getKeyManagers();
            trustManagers = selfSignedCert.getTrustManagers();
        }
        TLSServerParameters tlsParams = new TLSServerParameters();
        tlsParams.setKeyManagers(keyManagers);
        tlsParams.setTrustManagers(trustManagers);

        ClientAuthentication ca = new ClientAuthentication();
        ca.setRequired(false);
        ca.setWant(false);
        tlsParams.setClientAuthentication(ca);

        JettyHTTPServerEngineFactory factory = new JettyHTTPServerEngineFactory();
        for (URL url : server.getURLs()) {
            if ("https".equals(url.getProtocol())) {
                JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(url.getPort());
                TLSServerParameters existingParams = (engine == null) ? null : engine.getTlsServerParameters();
                if (existingParams != null) {
                    // key managers
                    Set<KeyManager> keyManagerSet = new LinkedHashSet<>();
                    if (existingParams.getKeyManagers() != null) {
                        Collections.addAll(keyManagerSet, existingParams.getKeyManagers());
                    }

                    if (keyManagers != null)
                        Collections.addAll(keyManagerSet, keyManagers);

                    // trust manager
                    Set<TrustManager> trustManagerSet = new LinkedHashSet<>();
                    if (existingParams.getTrustManagers() != null) {
                        Collections.addAll(trustManagerSet, existingParams.getTrustManagers());
                    }

                    if (trustManagers != null)
                        Collections.addAll(trustManagerSet, trustManagers);

                    existingParams.setKeyManagers(keyManagerSet.toArray(new KeyManager[0]));
                    existingParams.setTrustManagers(trustManagerSet.toArray(new TrustManager[0]));
                    ClientAuthentication clientAuthentication = new ClientAuthentication();
                    String value = server.getClientAuth();
                    if ("true".equalsIgnoreCase(value) ||
                            "yes".equalsIgnoreCase(value) ||
                            "require".equalsIgnoreCase(value) ||
                            "required".equalsIgnoreCase(value)) {
                        clientAuthentication.setRequired(true);
                    } else if ("optional".equalsIgnoreCase(value) ||
                            "want".equalsIgnoreCase(value)) {
                        clientAuthentication.setWant(true);
                    } else if ("false".equalsIgnoreCase(value) ||
                            "no".equalsIgnoreCase(value) ||
                            "none".equalsIgnoreCase(value)) {
                    } else {
                        // Could be a typo. Don't default to NONE since that is not
                        // secure. Force user to fix config. Could default to REQUIRED
                        // instead.
                        throw new IllegalArgumentException("Invalid ClientAuth value: " + value);
                    }
                    if (clientAuthentication.isRequired() != null || clientAuthentication.isWant() != null)
                        existingParams.setClientAuthentication(clientAuthentication);
                } else {
                    factory.setTLSServerParametersForPort(url.getPort(), tlsParams);
                }
            }
        }
    }
}
