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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.google.inject.Inject;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.jmx.JMXService;
import net.e6tech.elements.jmx.stat.Measurement;
import net.e6tech.elements.network.clustering.Cluster;
import net.e6tech.elements.network.clustering.ClusterService;
import net.e6tech.elements.web.JaxExceptionHandler;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.PerRequestResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.management.JMException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 *
 * code is based on http://cxf.apache.org/docs/jaxrs-services-configuration.html#JAXRSServicesConfiguration-JAX-RSRuntimeDelegateandApplications
 */
public class JaxRSServer extends CXFServer implements ClassBeanListener {

    static {
        System.setProperty("org.apache.cxf.useSpringClassHelpers", "false");
    }

    private static final String CLASS = "class";
    private static final String SINGLETON = "singleton";
    private static final String BIND_HEADER_OBSERVER = "bindHeaderObserver";
    private static final String REGISTER_BEAN = "registerBean";
    private static final String NAME = "name";
    private static Logger messageLogger = Logger.getLogger(JaxRSServer.class.getName() + ".message");

    private static Map<Integer, ServerFactorBeanEntry> entries = new Hashtable();

    private static Logger logger = Logger.getLogger();

    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        JaxRSServer.logger = logger;
    }

    @Inject(optional = true)
    private Observer headerObserver;

    private List<Map<String, Object>> resources = new ArrayList<>();

    @Inject(optional = true)
    private ExceptionMapper exceptionMapper;

    @Inject(optional = true)
    private ExecutorService threadPool;

    private Map<String, Object> instances = new Hashtable<>();

    private boolean corsFilter = false;

    @Inject(optional = true)
    private SecurityAnnotationEngine securityAnnotationEngine;

    public Observer getHeaderObserver() {
        return headerObserver;
    }

    public void setHeaderObserver(Observer headerObserver) {
        this.headerObserver = headerObserver;
    }

    public List<Map<String, Object>> getResources() {
        return resources;
    }

    public void setResources(List<Map<String, Object>> resources) {
        this.resources = resources;
    }

    public ExceptionMapper getExceptionMapper() {
        return exceptionMapper;
    }

    public void setExceptionMapper(ExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    public Map<String, Object> getInstances() {
        return instances;
    }

    public Object getInstance(String name) {
        return instances.get(name);
    }

    public boolean isCorsFilter() {
        return corsFilter;
    }

    public void setCorsFilter(boolean corsFilter) {
        this.corsFilter = corsFilter;
    }

    public SecurityAnnotationEngine getSecurityAnnotationEngine() {
        return securityAnnotationEngine;
    }

    public void setSecurityAnnotationEngine(SecurityAnnotationEngine securityAnnotationEngine) {
        this.securityAnnotationEngine = securityAnnotationEngine;
    }

    public void initialize(Resources res) {
        if (getURLs().size() == 0) {
            throw new IllegalStateException("address not set");
        }

        List<ServerFactorBeanEntry> entryList = new ArrayList<>();
        synchronized (entries) {
            for (URL url : getURLs()) {
                ServerFactorBeanEntry entry = entries.computeIfAbsent(url.getPort(), (port) -> new ServerFactorBeanEntry(url, new JAXRSServerFactoryBean()));
                if (!entry.getURL().equals(url)) {
                    throw new RuntimeException("Cannot register " + url.toExternalForm() + ".  Already a service at " + url.toExternalForm());
                }

                JAXRSServerFactoryBean bean = entry.getFactoryBean();
                bean.setAddress(url.toExternalForm());
                entries.put(url.getPort(), entry);
                entryList.add(entry);
            }
        }

        List<Class<?>> resourceClasses = new ArrayList<>();
        for (Map<String, Object> map : resources) {
            boolean singleton = false;
            Class resourceClass = null;
            String resourceClassName = (String) map.get(CLASS);
            if (resourceClassName == null) throw new RuntimeException("Missing resource class in resources map");
            try {
                resourceClass = provision.getClass().getClassLoader().loadClass(resourceClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            // determine if we should bind header observer or not
            Observer hObserver = headerObserver;
            boolean bindHeaderObserver = (map.get(BIND_HEADER_OBSERVER) == null) ? true : (Boolean) map.get(BIND_HEADER_OBSERVER);
            if (!bindHeaderObserver)
                hObserver = null;

            singleton = (map.get(SINGLETON) == null) ? false : (Boolean) map.get(SINGLETON);
            String resourceName = (String) map.get(NAME);

            Object instance = null;
            try {
                instance = resourceClass.newInstance();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            if (res != null) {
                res.inject(instance);
                if (hObserver != null) res.inject(hObserver);
            } else {
                provision.inject(instance);
                if (hObserver != null) provision.inject(hObserver);
            }

            if (securityAnnotationEngine != null)
                securityAnnotationEngine.register(instance);

            ResourceProvider resourceProvider ;
            if (singleton) {
                resourceProvider = new SharedResourceProvider(map, instance, hObserver);
                String beanName = (String) map.get(REGISTER_BEAN);
                if (beanName != null) provision.getResourceManager().registerBean(beanName, instance);
            } else {
                resourceProvider = new InstanceResourceProvider(map, resourceClass, res.getModule(), provision, hObserver);
            }

            for (ServerFactorBeanEntry entry : entryList) entry.getFactoryBean().setResourceProvider(resourceClass, resourceProvider);

            if (resourceName != null) instances.put(resourceName, instance);
            resourceClasses.add(resourceClass);
        }

        if (securityAnnotationEngine != null)
            securityAnnotationEngine.logMethodMap();

        for (ServerFactorBeanEntry entry : entryList) entry.addResourceClasses(resourceClasses);

        super.initialize(res);
    }

    public void start() {
        if (isStarted()) return;

        try {
            initKeyStore();
        } catch (Throwable th) {
            throw new RuntimeException(th);
        }

        List<JAXRSServerFactoryBean> beans = new ArrayList<>();
        synchronized (entries) {
            for (URL url : getURLs()) {
                ServerFactorBeanEntry entry = entries.get(url.getPort());
                if (entry != null) {
                    beans.add(entry.getFactoryBean());
                    entry.getFactoryBean().setResourceClasses(entry.getResourceClasses());
                    entries.remove(url.getPort());
                }
            }
        }

        for (JAXRSServerFactoryBean bean: beans) bean.getBus().setProperty("skip.default.json.provider.registration", true);
        JacksonJaxbJsonProvider jackson = new JacksonJaxbJsonProvider();
        jackson.disable(SerializationFeature.WRAP_ROOT_VALUE)
                .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        for (JAXRSServerFactoryBean bean: beans) bean.setProvider(jackson);

        if (isCorsFilter()) {
            logger.info("enabling CORS filter");
            CrossOriginResourceSharingFilter corsFilter = new CrossOriginResourceSharingFilter();
            for (JAXRSServerFactoryBean bean: beans) bean.setProvider(corsFilter);
        }

        for (JAXRSServerFactoryBean bean: beans) bean.getInInterceptors().add(new LoggingInInterceptor() {
            protected void log(java.util.logging.Logger otherLogger, final String message) {
                JaxRSServer.this.log(otherLogger, message);
            }
        });
        for (JAXRSServerFactoryBean bean: beans) bean.getOutInterceptors().add(new LoggingOutInterceptor() {
            protected void log(java.util.logging.Logger otherLogger, String message) {
                JaxRSServer.this.log(otherLogger, message);
            }
        });

        for (JAXRSServerFactoryBean bean: beans) bean.setProvider(new InternalExceptionMapper(exceptionMapper));
        for (JAXRSServerFactoryBean bean: beans) logger.info("Starting Restful at address " + bean.getAddress() + " " + bean.getResourceClasses());
        for (JAXRSServerFactoryBean bean: beans) {
            try {
                bean.setStart(false);
                registerServer(bean.create());
            } catch (Exception ex) {
                throw new RuntimeException("Cannot start RESTful service at " + bean.getAddress());
            }
        }
        super.start();
    }

    protected void log(java.util.logging.Logger otherLogger, String message) {
        Runnable runnable = () -> {
            messageLogger.trace(message);
        };

        if (messageLogger.isTraceEnabled()) {
            if (threadPool != null) {
                threadPool.execute(runnable);
            } else {
                runnable.run();
            }
        }
    }

    @Override
    public Class[] listenFor() {
        return new Class[] {Cluster.class};
    }

    @Override
    public void initialized(Object bean) {
        if (bean instanceof Cluster) {
            try {
                Cluster cluster = (Cluster) bean;
                for (URL url : getURLs()) {
                    for (Map<String, Object> map : resources) {
                        String resourceClassName = (String) map.get("class");
                        ClusterService service = ClusterService.newInstance(resourceClassName, url.toExternalForm());
                        cluster.addClusterService(service);
                        map.put("clusterService", service);
                    }
                }
            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }
    }

    private void handleException(Object instance, Method thisMethod, Object[] args, Throwable th) throws Throwable {
        th = ExceptionMapper.unwrap(th);
        if (instance instanceof JaxExceptionHandler) {
            Object response = ((JaxExceptionHandler) instance).handleException(thisMethod, args, th);
            if (response != null) {
                throw new InvocationException(response);
            } else {

            }
        } else {
            throw th;
        }
    }

    private class InstanceResourceProvider extends PerRequestResourceProvider {
        private Provision provision;
        private Observer observer;
        private InjectionModule module;
        private Map<String, Object> map;  // map is the Map<String, Object> in JaxRSServer's resources. It's used to record measurement
        private Map<Method, String> methods = new Hashtable<>();

        public InstanceResourceProvider(Map<String, Object> map, Class resourceClass, InjectionModule module, Provision provision, Observer observer) {
            super(resourceClass);
            this.provision = provision;
            this.observer = observer;
            this.module = module;
            this.map = map;
        }

        protected Object createInstance(Message message) {
            Object instance = super.createInstance(message);
            Observer cloneObserver = (observer == null) ? null : observer.clone();
            UnitOfWork uow = provision.preOpen((resources) -> {
                    resources.addModule(module);
                if (exceptionMapper != null) {
                    resources.rebind(ExceptionMapper.class, exceptionMapper);
                    resources.rebind((Class<ExceptionMapper>) exceptionMapper.getClass(), exceptionMapper);
                }
                }).onOpen((resources) -> {
                    // call header observer
                    if (cloneObserver !=  null) {
                        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
                        resources.inject(cloneObserver);
                        try {
                            cloneObserver.service(request);
                        } catch (Throwable ex) {
                            abort(message);
                            throw ex;
                        }
                    }

                    // copy from prototype properties to instance.
                    // Reflection.copyInstance(instance, prototype);
                    resources.inject(instance);
                });
            uow.open();
            message.getExchange().put(UnitOfWork.class, uow);
            return interceptor.newInterceptor(instance, new Handler(map, methods, cloneObserver, message));
        }

        public void releaseInstance(Message m, Object o) {
            try {
                super.releaseInstance(m, o);
            } finally {
                commit(m);
            }
        }
    }

    private class Handler implements InterceptorHandler {
        Message message;
        Observer observer;
        Map<String, Object> map;  // map is the Map<String, Object> in JaxRSServer's resources. It's used to record measurement
        Map<Method, String> methods;

        public Handler(Map<String, Object> map, Map<Method, String> methods, Observer observer, Message message) {
            this.message = message;
            this.observer = observer;
            this.map = map;
            this.methods = methods;
        }

        @Override
        public Object invoke(Object target, Method thisMethod, Object[] args) throws Throwable {
            boolean abort = false;
            UnitOfWork uow = null;
            Object result = null;
            boolean ignored = false;
            try {
                // Note PostConstruct is handled by CXF during createInstance
                if (thisMethod.getAnnotation(PreDestroy.class) != null) ignored = true;

                checkInvocation(thisMethod, args);

                if (!ignored) {
                    uow = message.getExchange().get(UnitOfWork.class);
                    if (uow.getResources() != null) // could be null when PreDestroy is called.
                        uow.getResources().inject(target);

                    long start = System.currentTimeMillis();
                    result = uow.submit(() -> {
                        if (observer != null) {
                            observer.beforeInvocation(target, thisMethod, args);
                        }

                        Object ret = thisMethod.invoke(target, args);

                        if (observer != null) {
                            observer.afterInvocation(ret);
                        }

                        return ret;
                    });

                    long duration = System.currentTimeMillis() - start;
                    computePerformance(thisMethod, methods, map, duration);
                } else {
                    result = thisMethod.invoke(target, args);
                }

            } catch (Throwable th) {
                recordFailure(thisMethod, methods, map);
                abort = true;
                ignored = false;
                logger.debug(th.getMessage(), th);
                handleException(target, thisMethod, args, th);
            } finally {
                if (resources != null && !ignored) {
                    if (abort) abort(message);
                    else commit(message);
                }
            }
            return result;
        }
    }

    private class SharedResourceProvider extends SingletonResourceProvider {

        Observer observer;
        Object proxy = null;
        Map<String, Object> map;
        Map<Method, String> methods = new Hashtable<>();

        public SharedResourceProvider(Map<String, Object> map, Object instance, Observer observer) {
            super(instance, true);
            this.observer = observer;
            this.map = map;
        }

        public Object getInstance(Message m) {
            Observer cloneObserver = (observer !=  null) ? observer.clone(): null;
            if (observer !=  null && m != null) {
                HttpServletRequest request = (HttpServletRequest) m.get(AbstractHTTPDestination.HTTP_REQUEST);
                provision.inject(cloneObserver);
                cloneObserver.service(request);
            }
            if (proxy == null) {
                proxy = interceptor.newInterceptor(super.getInstance(m), (target, thisMethod, args) -> {
                    ClusterService service = (ClusterService) map.get("clusterService");
                    try {
                        checkInvocation(thisMethod, args);
                        if (cloneObserver != null) {
                            cloneObserver.beforeInvocation(target, thisMethod, args);
                        }
                        long start = System.currentTimeMillis();
                        Object result = thisMethod.invoke(target, args);
                        long duration = System.currentTimeMillis() - start;
                        computePerformance(thisMethod, methods, map, duration);
                        if (service != null) service.getMeasurement().add(duration);
                        if (cloneObserver != null) {
                            cloneObserver.afterInvocation(result);
                        }
                        return result;
                    } catch (Throwable th) {
                        recordFailure(thisMethod, methods, map);
                        logger.debug(th.getMessage(), th);
                        handleException(target, thisMethod, args, th);
                    }
                    return null;
                });
            }
            return proxy;
        }

        public void releaseInstance(Message m, Object o) {
            super.releaseInstance(m, o);
        }
    }

    private static class ServerFactorBeanEntry {
        JAXRSServerFactoryBean bean;
        URL url;
        List<Class<?>> resourceClasses = new ArrayList<>();

        ServerFactorBeanEntry(URL url, JAXRSServerFactoryBean bean) {
            this.url = url;
            this.bean = bean;
        }

        synchronized URL getURL() {
            return url;
        }

        JAXRSServerFactoryBean getFactoryBean() {
            return bean;
        }

        synchronized void addResourceClasses(List<Class<?>> list) {
            resourceClasses.addAll(list);
        }

        List<Class<?>> getResourceClasses() {
            return resourceClasses;
        }
    }

    @Provider
    private static class InternalExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<Exception> {

        ExceptionMapper mapper;

        public InternalExceptionMapper(ExceptionMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Response toResponse(Exception exception) {
            Response.Status status = Response.Status.BAD_REQUEST;
            if (exception instanceof BadRequestException) {
                status = Response.Status.BAD_REQUEST;
            } else if (exception instanceof NotAuthorizedException) {
                status = Response.Status.UNAUTHORIZED;
            } else if (exception instanceof ForbiddenException) {
                status = Response.Status.FORBIDDEN;
            } else if (exception instanceof NotFoundException) {
                status = Response.Status.NOT_FOUND;
            } else if (exception instanceof NotAllowedException) {
                status = Response.Status.METHOD_NOT_ALLOWED;
            } else if (exception instanceof NotAcceptableException) {
                status = Response.Status.NOT_ACCEPTABLE;
            } else if (exception instanceof NotSupportedException) {
                status = Response.Status.UNSUPPORTED_MEDIA_TYPE;
            } else if (exception instanceof InternalServerErrorException) {
                status = Response.Status.INTERNAL_SERVER_ERROR;
            } else if (exception instanceof ServiceUnavailableException) {
                status = Response.Status.SERVICE_UNAVAILABLE;
            }

            // mapper should decide whether to log.
            // logger.warn(exception.getMessage(), ExceptionMapper.unwrap(exception));

            Object response;
            if (exception instanceof InvocationException) {
                response = ((InvocationException) exception).getResponse();
            } else {
                if (mapper != null) {
                    response = mapper.toResponse(exception);
                } else {
                    response = exception.getMessage();
                }
            }
            return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(response).build();
        }
    }

    static void abort(Message message) {
        commitOrAbort(message, false);
    }

    static void commit(Message message) {
        commitOrAbort(message, true);
    }

    static void commitOrAbort(Message message, boolean commit) {
        try {
            UnitOfWork uow = message.getExchange().get(UnitOfWork.class);
            if (uow != null) {
                if (commit) {
                    // application may have aborted programmatically
                    if (!uow.isAborted() && uow.isOpened()) uow.commit();
                } else {
                    uow.abort();
                }
            }
        } finally {
            message.getExchange().remove(UnitOfWork.class.getName(), null);
        }
    }

    private static void computePerformance(Method method, Map<Method,String> methods,  Map<String, Object> map, long duration) {
        ObjectInstance instance = null;
        try {
            instance = getMeasurement(method, methods);
            JMXService.invoke(instance.getObjectName(), "add", duration);
        } catch (Exception e) {
            logger.debug("Unable to record measurement for " + method, e);
        }

        ClusterService service = (ClusterService) map.get("clusterService");
        if (service != null) service.getMeasurement().add(duration);
    }

    private static void recordFailure(Method method, Map<Method,String> methods,  Map<String, Object> map) {
        ObjectInstance instance = null;
        try {
            instance = getMeasurement(method, methods);
            JMXService.invoke(instance.getObjectName(), "fail");
        } catch (Exception e) {
            logger.debug("Unable to record fail measurement for " + method, e);
        }

        ClusterService service = (ClusterService) map.get("clusterService");
        if (service != null) service.getMeasurement().fail();
    }

    private static ObjectInstance getMeasurement(Method method, Map<Method, String> methods) throws JMException {
        String methodName = methods.computeIfAbsent(method, m ->{
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
        return JMXService.registerIfAbsent(objectName, () -> new Measurement(methodName, "ms"));
    }

    private static void checkInvocation(Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        int idx = 0;
        StringBuilder builder = null;
        for (Parameter param : params) {
            QueryParam queryParam =  param.getAnnotation(QueryParam.class);
            PathParam pathParam =  param.getAnnotation(PathParam.class);

            if(pathParam != null && args[idx] == null) {
                if (builder == null) builder = new StringBuilder();
                builder.append("path parameter ").append(pathParam.value()).append(" cannot be null. \n");
            }

            if (param.getAnnotation(Nonnull.class) != null && args[idx] == null) {
                if (builder == null) builder = new StringBuilder();
                if (queryParam != null) {
                    builder.append("query parameter ").append(queryParam.value()).append(" cannot be null. \n");
                } else {
                    builder.append("post parameter ").append(" cannot be null. \n");
                }
            }
            idx++;
        }
        if (builder != null) {
            throw new IllegalArgumentException(builder.toString());
        }
    }

}
