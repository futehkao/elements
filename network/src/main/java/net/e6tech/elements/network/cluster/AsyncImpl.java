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

package net.e6tech.elements.network.cluster;

import net.e6tech.elements.common.reflection.Primitives;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.invocation.InvocationEvents;
import net.e6tech.elements.network.cluster.invocation.RegistryActor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class AsyncImpl<U> implements Async<U> {

    private Class<U> interfaceClass;
    private RegistryActor registry;
    private String qualifier;
    private long timeout;
    private CompletionStage<InvocationEvents.Response> completionStage;
    private U proxy;

    @SuppressWarnings("unchecked")
    public AsyncImpl(RegistryActor registry, String qualifier, Class<U> interfaceClass, long timeout) {
        this.registry = registry;
        this.qualifier = qualifier;
        this.timeout = timeout;
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");
        this.interfaceClass = interfaceClass;
        proxy = (U) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[] {interfaceClass}, new MyHandler());
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
        completionStage = null;
        function.apply(proxy);
        return completionStage.thenApply(response -> (R) response.getValue());
    }

    @SuppressWarnings("squid:S2259")
    public CompletionStage<Void> accept(Consumer<U> consumer) {
        completionStage = null;
        consumer.accept(proxy);
        return completionStage.thenApply(response -> null);
    }

    @SuppressWarnings("unchecked")
    private class MyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return AsyncImpl.this.invoke(AsyncImpl.this, method, args, () -> {
                Function<Object[], CompletionStage<InvocationEvents.Response>> function = registry.route(qualifier, interfaceClass, method, timeout);
                completionStage = function.apply(args);
                return Primitives.defaultValue(method.getReturnType());
            });
        }
    }
}
