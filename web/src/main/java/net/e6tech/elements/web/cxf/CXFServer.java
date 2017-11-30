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
package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.security.JCEKS;
import net.e6tech.elements.security.SelfSignedCert;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.ClientAuthentication;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S134")
public class CXFServer implements Initializable, Startable {

    @Inject
    protected Provision provision;

    @Inject
    protected Interceptor interceptor;

    protected List<Server> servers = new ArrayList<>();
    protected List<URL> urls = new ArrayList<>();
    protected String keyStoreFile;
    protected char[] keyStorePassword;
    protected char[] keyManagerPassword;
    protected SelfSignedCert selfSignedCert;

    @Inject(optional = true)
    protected ExecutorService executor;

    @Inject(optional = true)
    protected QueuedThreadPool queuedThreadPool;

    protected boolean initialized = false;

    private boolean started = false;

    public void setAddresses(List<String> addresses) throws MalformedURLException {
        for (String address : addresses) {
            URL url = new URL(address);
            urls.add(url);
        }
    }

    protected List<URL> getURLs() {
        return urls;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public char[] getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(char[] keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public SelfSignedCert getSelfSignedCert() {
        return selfSignedCert;
    }

    public void setSelfSignedCert(SelfSignedCert selfSignedCert) {
        this.selfSignedCert = selfSignedCert;
    }

    public ExecutorService getThreadPool() {
        return executor;
    }

    public void setThreadPool(ExecutorService executor) {
        this.executor = executor;
    }

    protected void registerServer(Server server) {
        if (!servers.contains(server))
            servers.add(server);
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
    @SuppressWarnings({"squid:S3776", "squid:MethodCyclomaticComplexity"})
    protected void initKeyStore() throws GeneralSecurityException, IOException {
        if (keyStoreFile == null && selfSignedCert == null)
            return;
        KeyManager[] keyManagers ;
        TrustManager[] trustManagers;
        if (selfSignedCert != null) {
            keyManagers = selfSignedCert.getKeyManagers();
            trustManagers = selfSignedCert.getTrustManagers();
        } else {
            JCEKS jceKeyStore = new JCEKS(keyStoreFile, keyStorePassword);
            if (keyManagerPassword == null)
                keyManagerPassword = keyStorePassword;
            jceKeyStore.init(keyManagerPassword);
            keyManagers = jceKeyStore.getKeyManagers();
            trustManagers = jceKeyStore.getTrustManagers();
        }
        TLSServerParameters tlsParams = new TLSServerParameters();
        tlsParams.setKeyManagers(keyManagers);
        tlsParams.setTrustManagers(trustManagers);

        ClientAuthentication ca = new ClientAuthentication();
        ca.setRequired(false);
        ca.setWant(false);
        tlsParams.setClientAuthentication(ca);

        JettyHTTPServerEngineFactory factory = new JettyHTTPServerEngineFactory();
        for (URL url : urls) {
            if ("https".equals(url.getProtocol())) {
                JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(url.getPort());
                TLSServerParameters existingParams = (engine == null) ? null : engine.getTlsServerParameters();
                if (existingParams != null) {
                    Set<KeyManager> keyManagerSet = new LinkedHashSet<>();
                    for (KeyManager km : existingParams.getKeyManagers())
                        keyManagerSet.add(km);
                    for (KeyManager km : keyManagers)
                        if (!keyManagerSet.contains(km))
                            keyManagerSet.add(km);
                    Set<TrustManager> trustManagerSet = new LinkedHashSet<>();
                    for (TrustManager tm : existingParams.getTrustManagers())
                        trustManagerSet.add(tm);
                    for (TrustManager tm : trustManagers)
                        if (!trustManagerSet.contains(tm))
                            trustManagerSet.add(tm);
                    existingParams.setKeyManagers(keyManagerSet.toArray(new KeyManager[keyManagerSet.size()]));
                    existingParams.setTrustManagers(trustManagerSet.toArray(new TrustManager[trustManagerSet.size()]));
                } else {
                    factory.setTLSServerParametersForPort(url.getPort(), tlsParams);
                }
            }
        }
    }

    public void initialize(Resources resources){
        initialized = true;
    }

    public boolean isStarted() {
        return started;
    }

    /*
    COMMENT
    To change threading parameters for the engine:
    ThreadingParameters params = new ThreadingParameters();
    params.setThreadNamePrefix("CXFServer");
    params.setMaxThreads(255);
    params.setMinThreads(20);
    engine.setThreadingParameters(params);*/
    public void start() {
        if (!initialized) {
            initialize(null);
        }

        if (started)
            return;
        started = true;

        if (queuedThreadPool != null) {
            for (Server server : servers) {
                Destination dest = server.getDestination();
                if (dest instanceof JettyHTTPDestination) {
                    JettyHTTPDestination jetty = (JettyHTTPDestination) dest;
                    if (jetty.getEngine() instanceof JettyHTTPServerEngine) {
                        ((JettyHTTPServerEngine) jetty.getEngine()).setThreadPool(queuedThreadPool);
                    }
                }
            }
        }
        for (Server server : servers )
            server.start();
    }

    public void stop() {
        for (Server server : servers )
            server.stop();
        started = false;
    }
}

