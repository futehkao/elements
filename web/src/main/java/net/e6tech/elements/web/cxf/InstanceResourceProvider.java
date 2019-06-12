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
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Provision;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InstanceResourceProvider extends PerRequestResourceProvider {
    private Provision provision;
    private Observer observer;
    private Module module;
    private Map<Method, String> methods = new ConcurrentHashMap<>();
    private Object prototype;
    private CXFServer server;

    InstanceResourceProvider(JaxRSServer server, Class resourceClass, Object prototype, Module module, Provision provision, Observer observer) {
        super(resourceClass);
        this.server = server;
        this.provision = provision;
        this.observer = observer;
        this.module = module;
        this.prototype = prototype;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object createInstance(Message message) {
        Object instance = super.createInstance(message);
        if (prototype != null)
            Reflection.copyInstance(instance, prototype);
        Observer cloneObserver = (observer == null) ? null : observer.clone();
        UnitOfWork uow = provision.preOpen(res -> {
            if (module != null)
                res.addModule(module);
            if (server.getExceptionMapper() != null) {
                res.rebind(ExceptionMapper.class, server.getExceptionMapper());
                res.rebind((Class<ExceptionMapper>) server.getExceptionMapper().getClass(), server.getExceptionMapper());
            }
        });
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
            for (Annotation annotation : cls.getAnnotations())
                uow.put((Class) annotation.annotationType(), annotation);
            for (Annotation annotation : method.getAnnotations())
                uow.put((Class) annotation.annotationType(), annotation);
            uow.open();
        }

        @Override
        @SuppressWarnings("squid:S3776")
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
                    JaxRSServer.getLogger().debug(th.getMessage(), th);
                    server.handleException(frame, th);
                }
            }

            try {
                server.checkInvocation(frame.getMethod(), frame.getArguments());
                Pair<HttpServletRequest, HttpServletResponse> pair = server.getServletRequestResponse(message);
                if (!ignored) {
                    long start = System.currentTimeMillis();
                    result = uow.submit(() -> {
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
                    });

                    long duration = System.currentTimeMillis() - start;
                    server.computePerformance(frame.getMethod(), methods, duration);
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
                server.recordFailure(frame.getMethod(), methods);
                abort = true;
                JaxRSServer.getLogger().debug(th.getMessage(), th);
                server.handleException(frame, th);
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
}
