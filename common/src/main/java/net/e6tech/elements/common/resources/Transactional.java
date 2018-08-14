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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.util.function.*;

import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
@FunctionalInterface
@SuppressWarnings({"squid:S00112", "squid:S1602"})
public interface Transactional {

    <T extends Resources> T open();

    default <R> R apply(Callable<R> callable) {
        return commit(callable);
    }

    default <R> R call(Callable<R> callable) {
        return commit(callable);
    }

    default <R> R commit(Callable<R> callable) {
        Resources resources = open();
        resources.submit(r -> {
            return callable.call();
        });
        return resources.commit();
    }

    default void run(RunnableWithException runnable) {
        commit(runnable);
    }

    default void accept(RunnableWithException runnable) {
        commit(runnable);
    }

    default void commit(RunnableWithException runnable) {
        Resources resources = open();
        resources.submit(r -> {
            runnable.run();
        });
        resources.commit();
    }

    default <T, R, E extends Exception> R apply(Class<T> cls, FunctionWithException<T, R, E> function) {
        return commit(cls, function);
    }

    default <T, R, E extends Exception> R commit(Class<T> cls, FunctionWithException<T, R, E> function) {
        Resources resources = open();
        resources.submit(r -> {
            return function.apply(r.getInstance(cls));
        });
        return resources.commit();
    }

    default <T, E extends Exception> void accept(Class<T> cls, ConsumerWithException<T, E> consumer) {
            commit(cls, consumer);
    }

    default <T, E extends Exception> void commit(Class<T> cls, ConsumerWithException<T, E> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            consumer.accept(r.getInstance(cls));
        });
        resources.commit();
    }

    default <S, T, R, E extends Exception> R apply(Class<S> cls, Class<T> cls2, BiFunctionWithException<S, T, R, E> function) {
        return commit(cls, cls2, function);
    }

    default <S, T, R, E extends Exception> R commit(Class<S> cls, Class<T> cls2, BiFunctionWithException<S, T, R, E> function) {
        Resources resources = open();
        resources.submit(r -> {
            return function.apply(r.getInstance(cls), r.getInstance(cls2));
        });
        return resources.commit();
    }

    default <S, T, E extends Exception> void accept(Class<S> cls, Class<T> cls2, BiConsumerWithException<S, T, E> consumer) {
        commit(cls, cls2, consumer);
    }

    default <S, T, E extends Exception> void commit(Class<S> cls, Class<T> cls2, BiConsumerWithException<S, T, E> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            consumer.accept(r.getInstance(cls), r.getInstance(cls2));
        });
        resources.commit();
    }

    default <S, T, U, R, E extends Exception> R apply(Class<S> cls, Class<T> cls2, Class<U> cls3, TriFunctionWithException<S, T, U, R, E> function) {
        return commit(cls, cls2, cls3, function);
    }

    default <S, T, U, R, E extends Exception> R commit(Class<S> cls, Class<T> cls2, Class<U> cls3, TriFunctionWithException<S, T, U, R, E> function) {
        Resources resources = open();
        resources.submit(r -> {
            return function.apply(r.getInstance(cls), r.getInstance(cls2), r.getInstance(cls3));
        });
        return resources.commit();
    }

    default <S, T, U, E extends Exception> void accept(Class<S> cls, Class<T> cls2, Class<U> cls3, TriConsumerWithException<S, T, U, E> consumer) {
        commit(cls, cls2, cls3, consumer);
    }

    default <S, T, U, E extends Exception> void commit(Class<S> cls, Class<T> cls2, Class<U> cls3, TriConsumerWithException<S, T, U, E> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            consumer.accept(r.getInstance(cls), r.getInstance(cls2), r.getInstance(cls3));
        });
        resources.commit();
    }

    default <E extends Exception> void accept(Class[] classes, ConsumerWithException<Object[], E> consumer) {
        commit(classes, consumer);
    }

    default <E extends Exception> void commit(Class[] classes, ConsumerWithException<Object[], E> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            Object[] arguments = new Object[classes.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = r.getInstance(classes[i]);
            }
            consumer.accept(arguments);
        });
        resources.commit();
    }

    default <R, E extends Exception> void apply(Class[] classes, FunctionWithException<Object[], R, E> function) {
        commit(classes, function);
    }

    default <R, E extends Exception> R commit(Class[] classes, FunctionWithException<Object[], R, E> function) {
        Resources resources = open();
        resources.submit(r -> {
            Object[] arguments = new Object[classes.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = r.getInstance(classes[i]);
            }
            return function.apply(arguments);
        });
        return resources.commit();
    }

}
