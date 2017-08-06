/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package net.e6tech.elements.common.resources;

import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
@FunctionalInterface
@SuppressWarnings({"squid:S00112", "squid:S1602"})
public interface Transactional {

    <T extends Resources> T open();

    default <R> R commit(Callable<R> callable) {
        Resources resources = open();
        resources.submit(r -> {
            return callable.call();
        });
        return resources.commit();
    }

    default void commit(RunnableWithException runnable) {
        Resources resources = open();
        resources.submit(r -> {
            runnable.run();
        });
        resources.commit();
    }

    default <T, R> R commit(Class<T> cls, FunctionWithException<T, R> function) {
        Resources resources = open();
        resources.submit(r -> {
            return function.apply(r.getInstance(cls));
        });
        return resources.commit();
    }

    default <T> void commit(Class<T> cls, ConsumerWithException<T> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            consumer.accept(r.getInstance(cls));
        });
        resources.commit();
    }

    default <S, T, R> R commit(Class<S> cls, Class<T> cls2, BiFunctionWithException<S, T, R> function) {
        Resources resources = open();
        resources.submit(r -> {
            return function.apply(r.getInstance(cls), r.getInstance(cls2));
        });
        return resources.commit();
    }

    default <S,T> void commit(Class<S> cls, Class<T> cls2, BiConsumerWithException<S,T> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            consumer.accept(r.getInstance(cls), r.getInstance(cls2));
        });
        resources.commit();
    }

    default <S, T, U, R> R commit(Class<S> cls, Class<T> cls2, Class<U> cls3, TriFunctionWithException<S, T, U, R> function) {
        Resources resources = open();
        resources.submit(r -> {
            return function.apply(r.getInstance(cls), r.getInstance(cls2), r.getInstance(cls3));
        });
        return resources.commit();
    }

    default <S,T,U> void commit(Class<S> cls, Class<T> cls2, Class<U> cls3, TriConsumerWithException<S,T,U> consumer) {
        Resources resources = open();
        resources.submit(r -> {
            consumer.accept(r.getInstance(cls), r.getInstance(cls2), r.getInstance(cls3));
        });
        resources.commit();
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface ConsumerWithException<T> {
        void accept(T t) throws Exception;
    }

    @FunctionalInterface
    interface BiConsumerWithException<S,T> {
        void accept(S s, T t) throws Exception;
    }

    @FunctionalInterface
    interface TriConsumerWithException<S, T, U> {
        void accept(S s, T t, U u) throws Exception ;
    }

    @FunctionalInterface
    interface FunctionWithException<T, R> {
        R apply(T t) throws Exception;
    }

    @FunctionalInterface
    interface BiFunctionWithException<S, T, R> {
        R apply(S s, T t) throws Exception;
    }

    @FunctionalInterface
    interface TriFunctionWithException<S, T, U, R> {
        R apply(S s, T t, U u) throws Exception;
    }
}
