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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Configuration;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.ResourcesFactory;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.ext.logging.event.LogEvent;
import org.apache.cxf.ext.logging.event.LogEventSender;
import org.apache.cxf.ext.logging.event.LogMessageFormatter;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Created by futeh.
 *
 * code is based on http://cxf.apache.org/docs/jaxrs-services-configuration.html#JAXRSServicesConfiguration-JAX-RSRuntimeDelegateandApplications
 */
public class JaxRSServer extends CXFServer {

    static {
        System.setProperty("org.apache.cxf.useSpringClassHelpers", "false");
    }

    private static final Logger messageLogger = Logger.getLogger(JaxRSServer.class.getName() + ".message");
    private static final Map<Integer, JaxRSServerController> entries = new ConcurrentHashMap<>();
    private static Logger logger = Logger.getLogger();

    private List<Map<String, Object>> resources = new ArrayList<>();
    private List<JaxResource> jaxResources = new ArrayList<>();
    private Map<String, Object> instances = new ConcurrentHashMap<>();
    private boolean corsFilter = false;
    private SecurityAnnotationEngine securityAnnotationEngine;
    private Configuration.Resolver resolver;
    private ClassLoader classLoader;
    private LogEventSender logEventSender;
    private ResourcesFactory resourcesFactory;
    private Consumer<JAXRSServerFactoryBean> customizer;

    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        JaxRSServer.logger = logger;
    }

    public List<Map<String, Object>> getResources() {
        return resources;
    }

    public void setResources(List<Map<String, Object>> resources) {
        this.resources = resources;
    }

    public List<JaxResource> getJaxResources() {
        return jaxResources;
    }

    public void setJaxResources(List<JaxResource> jaxResources) {
        this.jaxResources = jaxResources;
    }

    public JaxRSServer add(JaxResource resource) {
        this.jaxResources.add(resource);
        return this;
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

    @Inject(optional = true)
    public Configuration.Resolver getResolver() {
        return resolver;
    }

    public void setResolver(Configuration.Resolver resolver) {
        this.resolver = resolver;
    }

    public LogEventSender getLogEventSender() {
        return logEventSender;
    }

    public void setLogEventSender(LogEventSender logEventSender) {
        this.logEventSender = logEventSender;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ResourcesFactory getResourcesFactory() {
        return resourcesFactory;
    }

    @Inject(optional = true)
    public void setResourcesFactory(ResourcesFactory resourcesFactory) {
        this.resourcesFactory = resourcesFactory;
    }

    public Consumer<JAXRSServerFactoryBean> getCustomizer() {
        return customizer;
    }

    public void setCustomizer(Consumer<JAXRSServerFactoryBean> customizer) {
        this.customizer = customizer;
    }

    private List<JaxRSServerController> updateServerControllers() throws URISyntaxException {
        List<JaxRSServerController> entryList = new ArrayList<>();
        synchronized (entries) {
            for (URL url : getURLs()) {
                JaxRSServerController controller = entries.get(url.getPort());
                if (controller == null) {
                    controller = new JaxRSServerController(url, new JAXRSServerFactoryBean());
                    entries.put(url.getPort(), controller);
                }

                if (!controller.getURI().equals(url.toURI())) {
                    throw new SystemException("Cannot register " + url.toExternalForm() + ".  Already a service at " + url.toExternalForm());
                }

                JAXRSServerFactoryBean bean = controller.getFactory();
                bean.setAddress(url.toExternalForm());
                entries.put(url.getPort(), controller);
                entryList.add(controller);
            }
        }
        return entryList;
    }

    private Observer initBindHeaderObserver(Resources res, JaxResource jaxResource) {
        // determine if we should bind header observer or not
        Observer hObserver = getHeaderObserver();
        boolean bindHeaderObserver = jaxResource.isBindHeaderObserver();
        if (!bindHeaderObserver)
            hObserver = null;
        injectInitialize(res, hObserver);
        return hObserver;
    }

    private ResourceProvider initResourceProvider(Resources res, JaxResource jaxResource, Observer hObserver) {
        Object prototype = jaxResource.resolvePrototype(classLoader, resolver);
        ResourceProvider resourceProvider;

        if (jaxResource.isSingleton()) {
            injectInitialize(res, prototype);
            resourceProvider = new SharedResourceProvider(this, prototype, hObserver);
            String beanName = jaxResource.getRegisterBean();
            if (beanName != null)
                getProvision().getResourceManager().registerBean(beanName, prototype);
        } else {
            Module module = (res == null) ? null : res.getModule();
            ResourcesFactory factory = (resourcesFactory != null) ? resourcesFactory : getProvision().resourcesFactory();
            resourceProvider = new InstanceResourceProvider(this, jaxResource.getResourceClass(), prototype, module, factory, hObserver);
        }

        String resourceName = jaxResource.getName();
        if (resourceName != null && prototype != null)
            instances.put(resourceName, prototype);

        return resourceProvider;
    }

    @Override
    public void initialize(Resources res) {
        if (getURLs().isEmpty()) {
            throw new IllegalStateException("address not set");
        }

        List<JaxRSServerController> entryList = null;
        try {
            entryList = updateServerControllers();
        } catch (URISyntaxException e) {
            throw new SystemException(e);
        }

        resources.forEach(m -> add(JaxResource.from(m)));
        List<Class<?>> resourceClasses = new ArrayList<>();
        for (JaxResource jaxResource : jaxResources) {
            Class<?> resourceClass = jaxResource.resolveResourceClass(classLoader, resolver);

            // determine if we should bind header observer or not
            Observer hObserver = initBindHeaderObserver(res, jaxResource);

            if (securityAnnotationEngine != null) {
                securityAnnotationEngine.register(jaxResource.getResourceClass());
            }

            ResourceProvider resourceProvider = initResourceProvider(res, jaxResource, hObserver);

            for (JaxRSServerController entry : entryList)
                entry.getFactory().setResourceProvider(resourceClass, resourceProvider);

            resourceClasses.add(resourceClass);
        }

        if (securityAnnotationEngine != null)
            securityAnnotationEngine.logMethodMap();

        for (JaxRSServerController controller : entryList)
            controller.addResourceClasses(resourceClasses);

        super.initialize(res);
    }

    private void injectInitialize(Resources res, Object object) {
        if (res != null) {
            if (object != null)
                res.inject(object);
        } else {
            if (object != null)
                getProvision().inject(object);
        }
    }


    @Override
    public void start() {
        if (isStarted())
            return;

        // create a list of JAXRSServerFactoryBean
        List<JAXRSServerFactoryBean> beans = new LinkedList<>();
        List<ServerController<?>> controllers = new LinkedList<>();
        synchronized (entries) {
            for (URL url : getURLs()) {
                JaxRSServerController controller = entries.get(url.getPort());
                if (controller != null) {
                    beans.add(controller.getFactory());
                    controller.getFactory().setResourceClasses(controller.getResourceClasses());
                    entries.remove(url.getPort());
                    controllers.add(controller);
                }
            }
        }

        setupJsonProvider(beans);
        setupCorsFilter(beans);
        setupLogging(beans);

        // setup exception mapper
        for (JAXRSServerFactoryBean bean: beans)
            bean.setProvider(new InternalExceptionMapper(getExceptionMapper()));

        if (customizer != null)
            for (JAXRSServerFactoryBean bean: beans)
                customizer.accept(bean);

        for (JAXRSServerFactoryBean bean: beans)
            logger.info("Starting Restful at address {} {} ", bean.getAddress(), bean.getResourceClasses());

        // add controller
        for (ServerController<?> controller: controllers) {
            addController(controller);
        }

        // start servers
        super.start();
    }

    private void setupJsonProvider(List<JAXRSServerFactoryBean> beans) {
        // use Jackson as the provider
        for (JAXRSServerFactoryBean bean: beans)
            bean.getBus().setProperty("skip.default.json.provider.registration", true);

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRAP_ROOT_VALUE)
                .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        JacksonJaxbJsonProvider jackson = new JacksonJaxbJsonProvider(mapper, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
        for (JAXRSServerFactoryBean bean: beans)
            bean.setProvider(jackson);
    }

    private void setupCorsFilter(List<JAXRSServerFactoryBean> beans) {
        if (isCorsFilter()) {
            logger.info("enabling CORS filter");
            CrossOriginResourceSharingFilter cf = new CrossOriginResourceSharingFilter();
            for (JAXRSServerFactoryBean bean: beans)
                bean.setProvider(cf);
        }
    }

    private void setupLogging(List<JAXRSServerFactoryBean> beans) {
        // logging
        LoggingFeature feature = new LoggingFeature();
        DefaultLogEventSender sender = new DefaultLogEventSender();
        feature.setInSender(sender);
        feature.setOutSender(sender);
        for (JAXRSServerFactoryBean bean: beans) {
            bean.getFeatures().add(feature);
        }
    }

    private class DefaultLogEventSender implements LogEventSender {

        @Override
        public void send(LogEvent event) {
            try {
                if (messageLogger.isTraceEnabled())
                    messageLogger.trace(getLogMessage(event));

                if (logEventSender != null)
                    logEventSender.send(event);
            } catch (Exception ex) {
                logger.warn("Unable to send LogEvent", ex);
            }
        }

        private String getLogMessage(LogEvent event) {
            return LogMessageFormatter.format(event);
        }
    }

    @Provider
    private static class InternalExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<Exception> {

        ExceptionMapper<?> mapper;

        InternalExceptionMapper(ExceptionMapper<?> mapper) {
            this.mapper = mapper;
        }

        private Response.Status toStatus(Exception exception) {
            Response.Status status;
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
            } else {
                status = Response.Status.BAD_REQUEST;
            }
            return status;
        }

        @Override
        public Response toResponse(Exception exception) {
            Response.Status status = toStatus(exception);

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
}
