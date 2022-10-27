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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface InvokerRegistry extends Registry {

    void start();

    void shutdown() ;

    <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation, InvocationHandler customizedInvoker);

    <R> Function<Object[], CompletableFuture<R>> route(String qualifier, Class interfaceClass, Method method, Routing routing);

    Set<String> routes();

    Object invoke(String path, Object[] arguments);

}
