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

package net.e6tech.elements.common.reflection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("squid:S00112")
public class Lambda {
    private static final Cache<Method, Function> GETTERS = CacheBuilder.newBuilder().weakValues().build();
    private static final Cache<Method, BiConsumer> SETTERS = CacheBuilder.newBuilder().weakValues().build();

    private static Function createGetter(final MethodHandles.Lookup lookup,
                                         final MethodHandle getter) throws Exception {
        final CallSite site = LambdaMetafactory.metafactory(lookup, "apply",
                MethodType.methodType(Function.class),
                MethodType.methodType(Object.class, Object.class), //signature of method Function.apply after type erasure
                getter,
                wrapUnboxed(getter.type())); //actual signature of getter
        try {
            return (Function) site.getTarget().invokeExact();
        } catch (final Exception e) {
            throw e;
        } catch (final Throwable e) {
            throw new Error(e);
        }
    }

    private static BiConsumer createSetter(final MethodHandles.Lookup lookup,
                                           final MethodHandle setter) throws Exception {
        final CallSite site = LambdaMetafactory.metafactory(lookup,
                "accept",
                MethodType.methodType(BiConsumer.class),
                MethodType.methodType(void.class, Object.class, Object.class), //signature of method BiConsumer.accept after type erasure
                setter,
                wrapUnboxed(setter.type())); //actual signature of setter
        try {
            return (BiConsumer) site.getTarget().invokeExact();
        } catch (final Exception e) {
            throw e;
        } catch (final Throwable e) {
            throw new Error(e);
        }
    }

    private static MethodType wrapUnboxed(MethodType methodType) {
        List<Class<?>> actualParamsBoxed = methodType.parameterList().stream()
                .map(Primitives::getReferenceType)
                .collect(Collectors.toList());

        return MethodType.methodType(Primitives.getReferenceType(methodType.returnType()), actualParamsBoxed);
    }

    public static Function reflectGetter(final MethodHandles.Lookup lookup, final Method getter) throws ReflectiveOperationException {
        try {
            return GETTERS.get(getter, () -> createGetter(lookup, lookup.unreflect(getter)));
        } catch (final ExecutionException e) {
            throw new ReflectiveOperationException(e.getCause());
        }
    }

    public static BiConsumer reflectSetter(final MethodHandles.Lookup lookup, final Method setter) throws ReflectiveOperationException {
        try {
            return SETTERS.get(setter, () -> createSetter(lookup, lookup.unreflect(setter)));
        } catch (final ExecutionException e) {
            throw new ReflectiveOperationException(e.getCause());
        }
    }

    private Lambda() {
    }
}
