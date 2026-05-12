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

import net.e6tech.elements.common.logging.LogLevel;
import net.e6tech.elements.common.util.datastructure.Pair;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.message.Message;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SharedResourceProvider extends SingletonResourceProvider {
    private Observer observer;
    private Object proxy = null;
    private Map<Method, String> methods = new ConcurrentHashMap<>();
    private JaxRSServer server;

    SharedResourceProvider(JaxRSServer server, Object instance, Observer observer) {
        super(instance, true);
        this.server = server;
        this.observer = observer;
    }

    @Override
    @SuppressWarnings({"squid:S1188", "squid:S3776"})
    public Object getInstance(Message m) {
        JaxMessageLocal.set(l -> l.server(server).message(m));
        if (proxy == null) {
            Observer cloneObserver = (observer !=  null) ? observer.clone(): null;
            proxy = server.getInterceptor().newInterceptor(super.getInstance(null), frame -> {
                Message message = JaxMessageLocal.get().getMessage();
                try {
                    server.checkInvocation(frame.getMethod(), frame.getArguments());
                    if (cloneObserver != null && message != null) {
                        Pair<HttpServletRequest, HttpServletResponse> pair = server.getServletRequestResponse(message);
                        server.getProvision().inject(cloneObserver);
                        CachedOutputStream cachedOutputStream = message.getContent(CachedOutputStream.class);
                        if (cachedOutputStream != null) {
                            pair.key().setAttribute("Content", cachedOutputStream.getBytes());
                        }
                        cloneObserver.beforeInvocation(pair.key(), pair.value(), frame.getTarget(), frame.getMethod(), frame.getArguments());
                    }
                    long start = System.currentTimeMillis();

                    Object result = frame.invoke();

                    long duration = System.currentTimeMillis() - start;
                    server.computePerformance(frame.getMethod(), methods, duration);
                    if (cloneObserver != null)
                        cloneObserver.afterInvocation(result);

                    return result;
                } catch (Exception th) {
                    if (cloneObserver != null)
                        cloneObserver.onException(th);
                    server.recordFailure(frame.getMethod(), methods);
                    server.getProvision().log(JaxRSServer.getLogger(), LogLevel.DEBUG, th.getMessage(), th);
                    server.handleException(message, frame, th);
                } finally {
                    JaxMessageLocal.remove();
                }
                return null;
            });
        }
        return proxy;
    }
}