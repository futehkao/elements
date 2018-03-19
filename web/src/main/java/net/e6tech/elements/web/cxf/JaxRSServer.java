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
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.notification.NotificationListener;
import net.e6tech.elements.common.notification.ShutdownNotification;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.jmx.JMXService;
import net.e6tech.elements.jmx.stat.Measurement;
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
import javax.management.ObjectInstance;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 *
 * code is based on http://cxf.apache.org/docs/jaxrs-services-configuration.html#JAXRSServicesConfiguration-JAX-RSRuntimeDelegateandApplications
 */
@SuppressWarnings({"squid:S1149", "squid:MethodCyclomaticComplexity"})
public class JaxRSServer extends CXFServer {

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

    private Observer headerObserver;
    private List<Map<String, Object>> resources = new ArrayList<>();
    private ExceptionMapper exceptionMapper;
    private Map<String, Object> instances = new Hashtable<>();
    private boolean corsFilter = false;
    private boolean measurement = false;
    private SecurityAnnotationEngine securityAnnotationEngine;

    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        JaxRSServer.logger = logger;
    }

    @Inject(optional = true)
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

    @Inject(optional = true)
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

    @Inject(optional = true)
    public void setSecurityAnnotationEngine(SecurityAnnotationEngine securityAnnotationEngine) {
        this.securityAnnotationEngine = securityAnnotationEngine;
    }

    public boolean isMeasurement() {
        return measurement;
    }

    public void setMeasurement(boolean measurement) {
        this.measurement = measurement;
    }

    @Override
    @SuppressWarnings("squid:S2112")
    public void initialize(Resources res) {
        if (getURLs().isEmpty()) {
            throw new IllegalStateException("address not set");
        }

        res.getNotificationCenter().addNotificationListener(ShutdownNotification.class,
                NotificationListener.wrap("JaxRSServer" + getURLs(), notification -> stop())
        );

        List<ServerFactorBeanEntry> entryList = new ArrayList<>();
        synchronized (entries) {
            for (URL url : getURLs()) {
                ServerFactorBeanEntry entry = entries.computeIfAbsent(url.getPort(), port -> new ServerFactorBeanEntry(url, new JAXRSServerFactoryBean()));
                if (!entry.getURL().equals(url)) {
                    throw new SystemException("Cannot register " + url.toExternalForm() + ".  Already a service at " + url.toExternalForm());
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
            if (resourceClassName == null)
                throw new SystemException("Missing resource class in resources map");
            try {
                resourceClass = getProvision().getClass().getClassLoader().loadClass(resourceClassName);
            } catch (ClassNotFoundException e) {
                throw new SystemException(e);
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
            } catch (Exception e) {
                throw new SystemException(e);
            }
            if (res != null) {
                res.inject(instance);
                if (hObserver != null)
                    res.inject(hObserver);
            } else {
                getProvision().inject(instance);
                if (hObserver != null)
                    getProvision().inject(hObserver);
            }

            if (securityAnnotationEngine != null)
                securityAnnotationEngine.register(instance);

            ResourceProvider resourceProvider ;
            if (singleton) {
                resourceProvider = new SharedResourceProvider(map, instance, hObserver);
                String beanName = (String) map.get(REGISTER_BEAN);
                if (beanName != null)
                    getProvision().getResourceManager().registerBean(beanName, instance);
            } else {
                resourceProvider = new InstanceResourceProvider(map, resourceClass, res.getModule(), getProvision(), hObserver);
            }

            for (ServerFactorBeanEntry entry : entryList)
                entry.getFactoryBean().setResourceProvider(resourceClass, resourceProvider);

            if (resourceName != null)
                instances.put(resourceName, instance);
            resourceClasses.add(resourceClass);
        }

        if (securityAnnotationEngine != null)
            securityAnnotationEngine.logMethodMap();

        for (ServerFactorBeanEntry entry : entryList)
            entry.addResourceClasses(resourceClasses);

        super.initialize(res);
    }

    @Override
    public void start() {
        if (isStarted())
            return;

        try {
            initKeyStore();
        } catch (Exception th) {
            throw new SystemException(th);
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

        for (JAXRSServerFactoryBean bean: beans)
            bean.getBus().setProperty("skip.default.json.provider.registration", true);
        JacksonJaxbJsonProvider jackson = new JacksonJaxbJsonProvider();
        jackson.disable(SerializationFeature.WRAP_ROOT_VALUE)
                .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        for (JAXRSServerFactoryBean bean: beans)
            bean.setProvider(jackson);

        if (isCorsFilter()) {
            logger.info("enabling CORS filter");
            CrossOriginResourceSharingFilter cf = new CrossOriginResourceSharingFilter();
            for (JAXRSServerFactoryBean bean: beans)
                bean.setProvider(cf);
        }

        for (JAXRSServerFactoryBean bean: beans)
            bean.getInInterceptors().add(new LoggingInInterceptor() {
                @Override
                protected void log(java.util.logging.Logger otherLogger, final String message) {
                    JaxRSServer.this.log(message);
                }
            });
        for (JAXRSServerFactoryBean bean: beans)
            bean.getOutInterceptors().add(new LoggingOutInterceptor() {
                @Override
                protected void log(java.util.logging.Logger otherLogger, String message) {
                    JaxRSServer.this.log(message);
                }
            });

        for (JAXRSServerFactoryBean bean: beans)
            bean.setProvider(new InternalExceptionMapper(exceptionMapper));
        for (JAXRSServerFactoryBean bean: beans)
            logger.info("Starting Restful at address {} {} ", bean.getAddress(), bean.getResourceClasses());
        for (JAXRSServerFactoryBean bean: beans) {
            try {
                bean.setStart(false);
                registerServer(bean.create());
            } catch (Exception ex) {
                throw new SystemException("Cannot start RESTful service at " + bean.getAddress(), ex);
            }
        }
        super.start();
    }

    protected void log(String message) {
        Runnable runnable = () -> messageLogger.trace(message);

        if (messageLogger.isTraceEnabled()) {
            if (getThreadPool() != null) {
                getThreadPool().execute(runnable);
            } else {
                runnable.run();
            }
        }
    }

    @SuppressWarnings("squid:S00112")
    private void handleException(CallFrame frame, Throwable th) throws Throwable {
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

    private class InstanceResourceProvider extends PerRequestResourceProvider {
        private Provision provision;
        private Observer observer;
        private Module module;
        private Map<String, Object> map;  // map is the Map<String, Object> in JaxRSServer's resources. It's used to record measurement
        private Map<Method, String> methods = new Hashtable<>();

        public InstanceResourceProvider(Map<String, Object> map, Class resourceClass, Module module, Provision provision, Observer observer) {
            super(resourceClass);
            this.provision = provision;
            this.observer = observer;
            this.module = module;
            this.map = map;
        }

        @Override
        protected Object createInstance(Message message) {
            Object instance = super.createInstance(message);
            Observer cloneObserver = (observer == null) ? null : observer.clone();
            UnitOfWork uow = provision.preOpen(res -> {
                    res.addModule(module);
                if (exceptionMapper != null) {
                    res.rebind(ExceptionMapper.class, exceptionMapper);
                    res.rebind((Class<ExceptionMapper>) exceptionMapper.getClass(), exceptionMapper);
                }
                });
            return getInterceptor().newInterceptor(instance, new Handler(uow, map, methods, cloneObserver, message));
        }
    }

    private class Handler implements InterceptorHandler {
        UnitOfWork uow;
        Message message;
        Observer observer;
        Map<String, Object> map;  // map is the Map<String, Object> in JaxRSServer's resources. It's used to record measurement
        Map<Method, String> methods;

        public Handler(UnitOfWork uow, Map<String, Object> map, Map<Method, String> methods, Observer observer, Message message) {
            this.uow = uow;
            this.message = message;
            this.observer = observer;
            this.map = map;
            this.methods = methods;
        }

        private void open(Object target, Method method) {
            Class cls = target.getClass();
            for (Annotation annotation : cls.getAnnotations())
                uow.put((Class) annotation.annotationType(), annotation);
            for (Annotation annotation : method.getAnnotations())
                uow.put((Class) annotation.annotationType(), annotation);
            uow.open();
        }

        @Override
        public Object invoke(CallFrame frame) throws Throwable {
            boolean abort = false;
            Object result = null;
            boolean ignored = false;

            // Note PostConstruct is handled by CXF during createInstance
            boolean uowOpen = false;
            if (frame.getAnnotation(PreDestroy.class) != null) {
                ignored = true;
            } else {
                try {
                    open(frame.getTarget(), frame.getMethod());
                    uowOpen = true;
                } catch (Exception th) {
                    logger.debug(th.getMessage(), th);
                    handleException(frame, th);
                }
            }

            try {
                checkInvocation(frame.getMethod(), frame.getArguments());
                if (!ignored) {
                    long start = System.currentTimeMillis();
                    result = uow.submit(() -> {
                        if (observer != null) {
                            HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
                            uow.getResources().inject(observer);
                            observer.beforeInvocation(request, frame.getTarget(), frame.getMethod(), frame.getArguments());
                        }
                        uow.getResources().inject(frame.getTarget());
                        Object ret = frame.invoke();
                        if (observer != null)
                            observer.afterInvocation(ret);

                        return ret;
                    });

                    long duration = System.currentTimeMillis() - start;
                    computePerformance(frame.getMethod(), methods, map, duration);
                } else {
                    // PreDestroy is called
                    result = frame.invoke();
                }
            } catch (Exception th) {
                if (!ignored && observer != null) {
                    try {
                        observer.onException(th);
                    } catch (Exception ex) {
                        Logger.suppress(ex);
                    }
                }
                recordFailure(frame.getMethod(), methods, map);
                abort = true;
                logger.debug(th.getMessage(), th);
                handleException(frame, th);
            } finally {
                if (uowOpen) {
                    if (abort)
                        uow.abort();
                    else if (!uow.isAborted()) // application can call abort
                        uow.commit();
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

        @Override
        @SuppressWarnings("squid:S1188")
        public Object getInstance(Message m) {
            Observer cloneObserver = (observer !=  null) ? observer.clone(): null;
            if (proxy == null) {
                proxy = getInterceptor().newInterceptor(super.getInstance(m), frame -> {
                    try {
                        checkInvocation(frame.getMethod(), frame.getArguments());
                        if (cloneObserver != null) {
                            HttpServletRequest request = (HttpServletRequest) m.get(AbstractHTTPDestination.HTTP_REQUEST);
                            getProvision().inject(cloneObserver);
                            cloneObserver.beforeInvocation(request, frame.getTarget(), frame.getMethod(), frame.getArguments());
                        }
                        long start = System.currentTimeMillis();

                        Object result = frame.invoke();

                        long duration = System.currentTimeMillis() - start;
                        computePerformance(frame.getMethod(), methods, map, duration);
                        if (cloneObserver != null)
                            cloneObserver.afterInvocation(result);

                        return result;
                    } catch (Exception th) {
                        if (cloneObserver != null)
                            cloneObserver.onException(th);
                        recordFailure(frame.getMethod(), methods, map);
                        logger.debug(th.getMessage(), th);
                        handleException(frame, th);
                    }
                    return null;
                });
            }
            return proxy;
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

            Object response;
            if (exception instanceof InvocationException) {
                response = ((InvocationException) exception).getResponse();
            } else if (exception instanceof StatusException) {
                StatusException statusException = (StatusException) exception;
                status = statusException.getStatus();
                if (mapper != null) {
                    response = mapper.toResponse(statusException.getCause());
                } else {
                    response = statusException.getCause().getMessage();
                }
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

    @SuppressWarnings("squid:S1172")
    private void computePerformance(Method method, Map<Method,String> methods,  Map<String, Object> map, long duration) {
        ObjectInstance instance = null;
        try {
            instance = getMeasurement(method, methods);
            JMXService.invoke(instance.getObjectName(), "add", duration);
        } catch (Exception e) {
            logger.debug("Unable to record measurement for " + method, e);
        }
    }

    @SuppressWarnings("squid:S1172")
    private void recordFailure(Method method, Map<Method,String> methods,  Map<String, Object> map) {
        ObjectInstance instance = null;
        try {
            instance = getMeasurement(method, methods);
            JMXService.invoke(instance.getObjectName(), "fail");
        } catch (Exception e) {
            logger.debug("Unable to record fail measurement for " + method, e);
        }
    }

    private ObjectInstance getMeasurement(Method method, Map<Method, String> methods) throws JMException {
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
        return JMXService.registerIfAbsent(objectName, () -> new Measurement(methodName, "ms", measurement));
    }

    @SuppressWarnings( "squid:S134")
    private static void checkInvocation(Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        int idx = 0;
        StringBuilder builder = null;
        final String CANNOT_BE_NULL = " cannot be null. \n";
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
