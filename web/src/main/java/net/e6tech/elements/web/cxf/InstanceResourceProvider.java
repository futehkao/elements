/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.logging.LogLevel;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.ClassSignature;
import net.e6tech.elements.common.reflection.MethodSignature;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.reflection.Signature;
import net.e6tech.elements.common.resources.ResourcesFactory;
import net.e6tech.elements.common.resources.UnitOfWork;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.datastructure.Pair;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.jaxrs.lifecycle.PerRequestResourceProvider;
import org.apache.cxf.message.Message;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InstanceResourceProvider extends PerRequestResourceProvider {
    private ResourcesFactory factory;
    private Observer observer;
    private Map<Method, String> methods = new ConcurrentHashMap<>();
    private Object prototype;
    private CXFServer server;
    private Map<Signature, Map<Class<? extends Annotation>, Annotation>> annotations;

    @SuppressWarnings("unchecked")
    InstanceResourceProvider(JaxRSServer server, Class resourceClass, Object prototype, Module module, ResourcesFactory factory, Observer observer) {
        super(resourceClass);
        this.server = server;
        this.observer = observer;
        this.factory = factory;
        this.prototype = prototype;
        factory.preOpen(res -> {
            if (module != null)
                res.addModule(module);
            if (server.getExceptionMapper() != null) {
                res.rebind(ExceptionMapper.class, server.getExceptionMapper());
                res.rebind((Class<ExceptionMapper>) server.getExceptionMapper().getClass(), server.getExceptionMapper());
            }
        });
        annotations = Reflection.getAnnotations(resourceClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object createInstance(Message message) {
        Object instance = super.createInstance(message);
        if (prototype != null)
            Reflection.copyInstance(instance, prototype);
        Observer cloneObserver = (observer == null) ? null : observer.clone();
        UnitOfWork uow = (cloneObserver != null) ? cloneObserver.open(factory) : factory.open();
        return server.getInterceptor().newInterceptor(instance, new Handler(uow, methods, cloneObserver, message));
    }

    private class Handler implements InterceptorHandler {
        UnitOfWork uow;
        Message message;
        Observer observer;
        Map<Method, String> methods;

        Handler(UnitOfWork uow, Map<Method, String> methods, Observer observer, Message message) {
            this.uow = uow;
            this.message = message;
            this.observer = observer;
            this.methods = methods;
        }

        @SuppressWarnings("unchecked")
        private void open(Object target, Method method) {
            Class cls = target.getClass();
            ClassSignature classSignature = new ClassSignature(cls);
            Map<Class<? extends Annotation>, Annotation> map = annotations.getOrDefault(classSignature, new HashMap<>());
            MethodSignature methodSignature = new MethodSignature(method);
            Map<Class<? extends Annotation>, Annotation> map2 = annotations.getOrDefault(methodSignature, new HashMap<>());

            for (Map.Entry<Class<? extends Annotation>, Annotation> entry : map.entrySet()) {
                uow.put((Class) entry.getKey(), entry.getValue());
            }

            for (Map.Entry<Class<? extends Annotation>, Annotation> entry : map2.entrySet()) {
                uow.put((Class) entry.getKey(), entry.getValue());
            }

            uow.open();
        }

        @Override
        @SuppressWarnings("squid:S3776")
        public Object invoke(CallFrame frame) throws Throwable {
            boolean exception = false;
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
                    JaxRSServer.getLogger().debug(th.getMessage(), th);
                    server.handleException(message, frame, th);
                }
            }

            try {
                server.checkInvocation(frame.getMethod(), frame.getArguments());
                Pair<HttpServletRequest, HttpServletResponse> pair = server.getServletRequestResponse(message);
                if (!ignored) {
                    long start = System.currentTimeMillis();
                    result = uow.submit(() -> {
                        try {
                            if (observer != null) {
                                uow.getResources().inject(observer);
                                CachedOutputStream cachedOutputStream = message.getContent(CachedOutputStream.class);
                                if (cachedOutputStream != null) {
                                    pair.key().setAttribute("Content", cachedOutputStream.getBytes());
                                }
                                observer.beforeInvocation(pair.key(), pair.value(), frame.getTarget(), frame.getMethod(), frame.getArguments());
                            }
                            uow.getResources().inject(frame.getTarget());
                            Object ret = frame.invoke();
                            if (observer != null)
                                observer.afterInvocation(ret);
                            return ret;
                        } catch (Exception th) {
                            if (observer != null) { // this code is here so that the Resources object has not been clean up
                                                    // so that the observer can still use the Resources to retrieve information.
                                try {
                                    observer.onException(th);
                                } catch (Exception ex) {
                                    Logger.suppress(ex);
                                }
                            }
                            // uow will abort the resources and do clean up after this
                            throw th;
                        }
                    });
                    long duration = System.currentTimeMillis() - start;
                    server.computePerformance(frame.getMethod(), methods, duration);
                } else {
                    // PreDestroy is called
                    result = frame.invoke();
                }
            } catch (Exception th) {
                server.recordFailure(frame.getMethod(), methods);
                exception = true;
                server.getProvision().log(JaxRSServer.getLogger(), LogLevel.DEBUG, th.getMessage(), th);
                server.handleException(message, frame, th);
            } finally {
                if (uowOpen) {
                    if (exception)
                        uow.abort();
                    else if (!uow.isAborted()) // application can call abort
                        uow.commit();
                }
            }
            return result;
        }
    }
}
