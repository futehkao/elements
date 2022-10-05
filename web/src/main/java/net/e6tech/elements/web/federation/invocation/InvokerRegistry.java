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
import net.e6tech.elements.network.cluster.Registry;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface InvokerRegistry extends Registry {

    void start();

    void shutdown() ;

    long getTimeout();

    void setTimeout(long timeout);

    <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation, Invoker customizedInvoker);

    <R> Function<Object[], CompletableFuture<R>> route(String qualifier, Class interfaceClass, Method method);

    /** returns a collection of frequencies e*/
    @Override
    Collection routes(String qualifier, Class interfaceClass);

    Set<String> routes();

    Object invoke(String path, Object[] arguments);

    <R> Async<R> async(String qualifier, Class<R> interfaceClass);

    @Override
    public <R> Async<R> async(String qualifier, Class<R> interfaceClass, long timeout);
}
