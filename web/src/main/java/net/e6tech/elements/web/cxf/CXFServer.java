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
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.datastructure.Pair;
import net.e6tech.elements.jmx.JMXService;
import net.e6tech.elements.jmx.stat.Measurement;
import net.e6tech.elements.security.JavaKeyStore;
import net.e6tech.elements.security.SelfSignedCert;
import net.e6tech.elements.web.JaxExceptionHandler;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.ClientAuthentication;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.annotation.Nonnull;
import javax.management.JMException;
import javax.management.ObjectInstance;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S134")
public class CXFServer implements Initializable, Startable {
    private static final String CANNOT_BE_NULL = " cannot be null. \n";
    private static final Logger logger = Logger.getLogger();
    private Provision provision;
    private Interceptor interceptor;
    private List<Server> servers = new ArrayList<>();
    private List<URL> urls = new ArrayList<>();
    private String keyStoreFile;
    private String keyStoreFormat = JavaKeyStore.DEFAULT_FORMAT;
    private KeyStore keyStore;
    private char[] keyStorePassword;
    private char[] keyManagerPassword;
    private SelfSignedCert selfSignedCert;
    private ExecutorService executor;
    private QueuedThreadPool queuedThreadPool;
    private boolean initialized = false;
    private boolean started = false;
    private boolean measurement = false;
    private Observer headerObserver;
    private ExceptionMapper exceptionMapper;
    private Map<String, String> responseHeaders = new LinkedHashMap<>();

    public void setAddresses(List<String> addresses) throws MalformedURLException {
        for (String address : addresses) {
            URL url = new URL(address);
            urls.add(url);
        }
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public Interceptor getInterceptor() {
        return interceptor;
    }

    @Inject
    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
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

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
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

    public SelfSignedCert getSelfSignedCert() {
        return selfSignedCert;
    }

    public void setSelfSignedCert(SelfSignedCert selfSignedCert) {
        this.selfSignedCert = selfSignedCert;
    }

    public ExecutorService getThreadPool() {
        return executor;
    }

    @Inject(optional = true)
    public void setThreadPool(ExecutorService executor) {
        this.executor = executor;
    }

    public QueuedThreadPool getQueuedThreadPool() {
        return queuedThreadPool;
    }

    @Inject(optional = true)
    public void setQueuedThreadPool(QueuedThreadPool queuedThreadPool) {
        this.queuedThreadPool = queuedThreadPool;
    }

    public boolean isMeasurement() {
        return measurement;
    }

    @Inject(optional = true)
    public Observer getHeaderObserver() {
        return headerObserver;
    }

    public void setHeaderObserver(Observer headerObserver) {
        this.headerObserver = headerObserver;
    }

    public void setMeasurement(boolean measurement) {
        this.measurement = measurement;
    }

    public ExceptionMapper getExceptionMapper() {
        return exceptionMapper;
    }

    @Inject(optional = true)
    public void setExceptionMapper(ExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
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
    @SuppressWarnings({"squid:S3776", "squid:MethodCyclomaticComplexity", "squid:CommentedOutCodeLine"})
    protected void initKeyStore() throws GeneralSecurityException, IOException {
        if (keyStoreFile == null && selfSignedCert == null && keyStore == null)
            return;
        KeyManager[] keyManagers ;
        TrustManager[] trustManagers;
        if (keyStore != null || keyStoreFile != null) {
            JavaKeyStore jceKeyStore;
            if (keyStore != null) {
                jceKeyStore = new JavaKeyStore(keyStore);
            } else {
                jceKeyStore = new JavaKeyStore(keyStoreFile, keyStorePassword, keyStoreFormat);
            }
            if (keyManagerPassword == null)
                keyManagerPassword = keyStorePassword;
            jceKeyStore.init(keyManagerPassword);
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
        for (URL url : urls) {
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
    @SuppressWarnings("squid:CommentedOutCodeLine")
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

    Pair<HttpServletRequest, HttpServletResponse> getServletRequestResponse(Message message) {
        Pair<HttpServletRequest, HttpServletResponse> pair = new Pair<>(null, null);
        if (message != null) {
            HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
            HttpServletResponse response = (HttpServletResponse) message.get(AbstractHTTPDestination.HTTP_RESPONSE);
            pair = new Pair<>(request, response);
            if (response != null)
                getResponseHeaders().forEach(response::setHeader);
        }
        return pair;
    }

    @SuppressWarnings("squid:S00112")
    void handleException(CallFrame frame, Throwable th) throws Throwable {
        Throwable throwable = ExceptionMapper.unwrap(th);
        if (frame.getTarget() instanceof JaxExceptionHandler) {
            Object response = ((JaxExceptionHandler) frame.getTarget()).handleException(frame, throwable);
            if (response != null) {
                throw new InvocationException(response);
            } else {
                // do nothing
            }
        } else {
            throw throwable;
        }
    }

    void computePerformance(Method method, Map<Method,String> methods, long duration) {
        ObjectInstance instance = null;
        try {
            instance = getMeasurement(method, methods);
            logger.trace("{} call took {}ms",  instance.getObjectName().getCanonicalName(), duration);
            JMXService.invoke(instance.getObjectName(), "add", duration);
        } catch (Exception e) {
            logger.debug("Unable to record measurement for " + method, e);
        }
    }

    void recordFailure(Method method, Map<Method,String> methods) {
        try {
            ObjectInstance instance = getMeasurement(method, methods);
            JMXService.invoke(instance.getObjectName(), "fail");
        } catch (Exception e) {
            logger.debug("Unable to record fail measurement for " + method, e);
        }
    }

    private ObjectInstance getMeasurement(Method method, Map<Method, String> methods) throws JMException {
        String methodName = methods.computeIfAbsent(method, m -> {
            StringBuilder builder = new StringBuilder();
            builder.append(m.getDeclaringClass().getTypeName());
            builder.append(".");
            builder.append(m.getName());
            Class[] types = m.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                builder.append("|"); // separating parameters using underscores instead commas because of JMX
                // ObjectName constraint
                builder.append(types[i].getSimpleName());
            }
            return builder.toString();
        });

        String objectName = "net.e6tech:type=Restful,name=" + methodName;
        return JMXService.registerIfAbsent(objectName, () -> new Measurement(methodName, "ms", isMeasurement()));
    }

    @SuppressWarnings("squid:S3776")
    void checkInvocation(Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        int idx = 0;
        StringBuilder builder = null;
        for (Parameter param : params) {
            QueryParam queryParam =  param.getAnnotation(QueryParam.class);
            PathParam pathParam =  param.getAnnotation(PathParam.class);
            if (args[idx] == null || (args[idx] instanceof String && ((String) args[idx]).trim().isEmpty())) {
                if (pathParam != null) {
                    if (builder == null)
                        builder = new StringBuilder();
                    builder.append("path parameter ").append(pathParam.value()).append(CANNOT_BE_NULL);
                }

                if (param.getAnnotation(Nonnull.class) != null) {
                    if (queryParam != null) {
                        if (builder == null)
                            builder = new StringBuilder();
                        builder.append("query parameter ").append(queryParam.value()).append(CANNOT_BE_NULL);
                    } else if (pathParam == null) {
                        if (builder == null)
                            builder = new StringBuilder();
                        builder.append("post parameter ").append("arg").append(idx).append(CANNOT_BE_NULL);
                    }
                }
            }

            idx++;
        }
        if (builder != null) {
            throw new IllegalArgumentException(builder.toString());
        }
    }
}

