/*
Copyright 2015-2019 Futeh Kao

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
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.datastructure.Pair;
import net.e6tech.elements.jmx.JMXService;
import net.e6tech.elements.jmx.stat.Measurement;
import net.e6tech.elements.security.JavaKeyStore;
import net.e6tech.elements.security.SelfSignedCert;
import net.e6tech.elements.web.JaxExceptionHandler;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import javax.annotation.Nonnull;
import javax.management.JMException;
import javax.management.ObjectInstance;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S134")
public class CXFServer implements Initializable, Startable {
    private static final String CANNOT_BE_NULL = " cannot be null. \n";
    private static final Logger logger = Logger.getLogger();
    private Provision provision;
    private Interceptor interceptor;
    private List<URL> urls = new LinkedList<>();
    private String keyStoreFile;
    private String keyStoreFormat = JavaKeyStore.DEFAULT_FORMAT;
    private KeyStore keyStore;
    private char[] keyStorePassword;
    private char[] keyManagerPassword;
    private String sslProtocol = "TLS";
    private String clientAuth;
    private SelfSignedCert selfSignedCert;
    private boolean sendServerVersion = false;
    private boolean initialized = false;
    private boolean started = false;
    private boolean measurement = false;
    private Observer headerObserver;
    private ExceptionMapper exceptionMapper;
    private Map<String, String> responseHeaders = new LinkedHashMap<>();
    private ServerEngine serverEngine;  // for example Jetty vs Tomcat
    private Class<? extends ServerEngine> serverEngineClass;
    private Object serverEngineData;
    private List<ServerController> controllers = new LinkedList<>();

    public void setAddresses(List<String> addresses) throws MalformedURLException {
        for (String address : addresses) {
            URL url = new URL(address);
            if (!urls.contains(url))
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

    public List<URL> getURLs() {
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

    public String getClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(String clientAuth) {
        this.clientAuth = clientAuth;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public SelfSignedCert getSelfSignedCert() {
        return selfSignedCert;
    }

    public void setSelfSignedCert(SelfSignedCert selfSignedCert) {
        this.selfSignedCert = selfSignedCert;
    }

    public boolean isSendServerVersion() {
        return sendServerVersion;
    }

    public void setSendServerVersion(boolean sendServerVersion) {
        this.sendServerVersion = sendServerVersion;
    }

    public boolean isMeasurement() {
        return measurement;
    }

    public Observer getHeaderObserver() {
        return headerObserver;
    }

    @Inject(optional = true)
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

    public ServerEngine getServerEngine() {
        return serverEngine;
    }

    @Inject(optional = true)
    public void setServerEngine(ServerEngine serverEngine) {
        this.serverEngine = serverEngine;
    }

    public Class<? extends ServerEngine> getServerEngineClass() {
        return serverEngineClass;
    }

    public void setServerEngineClass(Class<? extends ServerEngine> serverEngineClass) {
        this.serverEngineClass = serverEngineClass;
    }

    @SuppressWarnings("unchecked")
    public <T> T getServerEngineData() {
        return (T) serverEngineData;
    }

    public <T> void setServerEngineData(T serverEngineData) {
        this.serverEngineData = serverEngineData;
    }

    @SuppressWarnings("unchecked")
    public <T> T computeServerEngineData(Supplier<T> supplier) {
        if (serverEngineData == null)
            setServerEngineData(supplier.get());
        return (T) serverEngineData;
    }


    protected void addController(ServerController controller) {
        if (!controllers.contains(controller))
            controllers.add(controller);
    }

    @SuppressWarnings("unchecked")
    public void initialize(Resources resources){
        initialized = true;
        if (resources != null) {
            resources.getResourceManager().onShutdown("CXFServer " + getURLs(), notification -> stop());
        }
        if (serverEngine == null) {
            try {
                Class cls = (serverEngineClass != null) ? serverEngineClass :
                        getClass().getClassLoader().loadClass("net.e6tech.elements.web.cxf.jetty.JettyEngine");
                serverEngine = (ServerEngine) cls.getConstructor().newInstance();
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }

        if (resources != null) {
            resources.inject(serverEngine);
        }
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

        for (ServerController controller : controllers )
            serverEngine.start(this, controller);
    }

    public void stop() {
        serverEngine.stop(this);
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
    void handleException(Message message, CallFrame frame, Throwable th) throws Throwable {
        Throwable throwable = ExceptionMapper.unwrap(th);
        if (frame.getTarget() instanceof JaxExceptionHandler) {
            Object response = ((JaxExceptionHandler) frame.getTarget()).handleException(frame, throwable);
            if (response != null) {
                Exception exception = new InvocationException(response);
                serverEngine.onException(message, frame, exception);
                throw exception;
            } else {
                // do nothing
            }
        } else {
            serverEngine.onException(message, frame, throwable);
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

