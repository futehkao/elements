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

import javax.ws.rs.NotSupportedException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class Async<U> {

    Registry registry;
    String qualifier;
    long timeout = 5000L;
    CompletionStage completionStage;
    U proxy;

    public Async(Registry registry, String qualifier, Class<U> interfaceClass, long timeout) {
        this.registry = registry;
        this.qualifier = qualifier;
        this.timeout = timeout;
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");
        proxy = (U) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[] {interfaceClass}, new MyHandler());
    }

    public U proxy() {
        return proxy;
    }

    public <T extends Serializable, R> CompletionStage<R> apply(Function<U, Function<T,R>> function, T argument) {
        completionStage = null;
        Function<T,R> function2 = function.apply(proxy);
        function2.apply(argument);
        return completionStage;
    }

    public <T extends Serializable, R> CompletionStage<R> accept(Function<U, Consumer<T>> function, T argument) {
        completionStage = null;
        Consumer<T> consumer = function.apply(proxy);
        consumer.accept(argument);
        return completionStage;
    }

    private class MyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                return Async.this.hashCode();
            } else if (method.getName().equals("equals") && method.getParameterCount() == 1) {
                return Async.this.equals(args[0]);
            } else if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                return Async.this.toString();
            }

            if (method.getParameterCount() == 1) {
                Function<Object, CompletionStage> function = registry.route(qualifier, (Class) method.getParameterTypes()[0],
                        (Class) method.getReturnType(), timeout);
                completionStage = function.apply(args[0]);
                return Primitives.defaultValue(method.getReturnType());
            } else {
                throw new NotSupportedException();
            }
        }
    }
}
