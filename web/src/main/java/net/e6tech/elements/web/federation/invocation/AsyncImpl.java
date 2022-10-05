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

import net.e6tech.elements.common.util.concurrent.Async;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
    private U proxy;
    private final Executor executor;

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

    public U proxy() {
        return proxy;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @SuppressWarnings({"unchecked", "squid:S2259"})
    public <R> CompletionStage<R> apply(Function<U, R> function) {
        return CompletableFuture.supplyAsync(() -> function.apply(proxy), executor);
    }

    @SuppressWarnings("squid:S2259")
    public CompletionStage<Void> accept(Consumer<U> consumer) {
        return CompletableFuture.runAsync(() -> consumer.accept(proxy), executor);
    }

    @SuppressWarnings("unchecked")
    private class MyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0) {
                return AsyncImpl.this.hashCode();
            } else if ("equals".equals(methodName) && method.getParameterCount() == 1) {
                return AsyncImpl.this.equals(args[0]);
            } else if ("toString".equals(methodName) && method.getParameterCount() == 0) {
                return AsyncImpl.this.toString();
            }

            Function<Object[], CompletableFuture<Object>> function = registry.route(qualifier, interfaceClass, method);
            if (timeout > 0) {
                return function.apply(args).get(timeout, TimeUnit.MILLISECONDS);
            } else
                return function.apply(args).get();
        }
    }
}
