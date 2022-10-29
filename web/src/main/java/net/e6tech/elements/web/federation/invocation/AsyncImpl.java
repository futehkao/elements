/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.web.federation.invocation;

import net.e6tech.elements.common.federation.Registry;
import net.e6tech.elements.common.reflection.Primitives;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.Async;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class
 * @param <U> return value class.
 */
public class AsyncImpl<U> implements Async<U> {
    private Class<U> interfaceClass;
    private InvokerRegistry registry;
    private String qualifier;
    private long timeout;
    private final Executor executor;
    private U proxy;
    private CompletableFuture future;

    private Registry.Routing routing = Registry.Routing.random;

    @SuppressWarnings("unchecked")
    public AsyncImpl(InvokerRegistry registry, String qualifier, Class<U> interfaceClass, long timeout, Executor executor) {
        this.registry = registry;
        this.qualifier = qualifier;
        this.timeout = timeout;
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");
        this.interfaceClass = interfaceClass;
        proxy = (U) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[] {interfaceClass}, new MyHandler());
        if (executor != null)
            this.executor = executor;
        else
            this.executor = runnable -> new Thread(runnable).start();
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Registry.Routing getRouting() {
        return routing;
    }

    public void setRouting(Registry.Routing routing) {
        this.routing = routing;
    }

    public <R> CompletionStage<R> apply(Function<U, R> function) {
        future = null;
        function.apply(proxy);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (R) future.get();
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }, executor);
    }

    @SuppressWarnings("squid:S2259")
    public CompletionStage<Void> accept(Consumer<U> consumer) {
        future = null;
        consumer.accept(proxy);
        return CompletableFuture.runAsync(() -> {
            try {
                future.join();
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }, executor);
    }

    @SuppressWarnings("unchecked")
    private class MyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return AsyncImpl.this.invoke(AsyncImpl.this, method, args, () -> {
                Function<Object[], CompletableFuture<Object>> function = registry.route(qualifier, interfaceClass, method, routing);
                future = function.apply(args);
                return Primitives.defaultValue(method.getReturnType());
            });
        }
    }
}
