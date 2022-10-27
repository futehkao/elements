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

package net.e6tech.elements.common.federation;

import net.e6tech.elements.common.util.concurrent.Async;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public interface Registry {
    enum Routing {
        random,
        local,
        remote;
    }

    static String fullyQualify(String qualifier, Class interfaceClass, Method method) {
        StringBuilder builder = new StringBuilder();
        String normalizedQualifier = (qualifier == null) ? "" : qualifier.trim();
        if (normalizedQualifier.length() > 0) {
            builder.append(normalizedQualifier);
            builder.append("@");
        }
        builder.append(interfaceClass.getName());
        builder.append("::");
        builder.append(method.getName());
        boolean first = true;
        for (Class param : method.getParameterTypes()) {
            if (first) {
                first = false;
                builder.append("+");
            } else {
                builder.append(",");
            }
            builder.append(param.getTypeName());
        }
        return builder.toString();
    }

    <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation);

    <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation, InvocationHandler invoker);

    Collection routes(String qualifier, Class interfaceClass);

    <T> Async<T> async(String qualifier, Class<T> interfaceClass);

    default <T> Async<T> async(String qualifier, Class<T> interfaceClass, long timeout) {
        return async(qualifier, interfaceClass, timeout, Routing.random);
    }

    <T> Async<T> async(String qualifier, Class<T> interfaceClass, long timeout, Routing routing);
}
