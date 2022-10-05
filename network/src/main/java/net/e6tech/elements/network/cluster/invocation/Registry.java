/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.network.cluster.invocation;

import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.RouteListener;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Registry extends net.e6tech.elements.network.cluster.Registry {

    void start(Guardian guardian);

    void shutdown() ;

    long getTimeout();

    void setTimeout(long timeout);

    void addRouteListener(RouteListener listener);

    void removeRouteListener(RouteListener listener);

    Collection routes(String path);

    Collection routes(String qualifier, Class interfaceClass);

    default void waitLoop(BooleanSupplier test, long timeout) throws TimeoutException {
        Object monitor = new Object();
        RouteListener listener = new RouteListener() {
            @Override
            public void onAnnouncement(String path) {
                synchronized (monitor) {
                    monitor.notifyAll();
                }
            }
        };
        try {
            addRouteListener(listener);
            long start = System.currentTimeMillis();
            boolean first = true;
            synchronized (monitor) {
                while (!test.getAsBoolean()) {
                    if (!first && System.currentTimeMillis() - start > timeout) {
                        throw new TimeoutException();
                    }
                    if (first)
                        first = false;
                    try {
                        long wait = timeout - (System.currentTimeMillis() - start);
                        if (wait > 0)
                            monitor.wait(timeout);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } finally {
            removeRouteListener(listener);
        }
    }

    default void waitForRoutes(String qualifier, Predicate<Collection> predicate, long timeout) throws TimeoutException {
        waitLoop(() -> predicate.test(routes(qualifier)), timeout);
    }

    default void waitForRoutes(String qualifier, Class interfaceClass, Predicate<Collection> predicate, long timeout) throws TimeoutException {
        waitLoop(() -> predicate.test(routes(qualifier, interfaceClass)), timeout);
    }

    <T, U> CompletionStage<List<U>> register(String qualifier, Class<T> interfaceClass, T implementation);

    // discover when other nodes have register the interfaceClass
    <T, U> CompletionStage<List<U>> discover(String qualifier, Class<T> interfaceClass);

    <T, U> CompletionStage<List<U>> register(String qualifier, Class<T> interfaceClass, T implementation, Invoker customizedInvoker);

    Function<Object[], CompletionStage<InvocationEvents.Response>> route(String qualifier, Class interfaceClass, Method method, long timeout);

    Function<Object[], CompletionStage<InvocationEvents.Response>> route(String path, long timeout);

    <T> Async<T> async(String qualifier, Class<T> interfaceClass);

    @Override
    <T> Async<T> async(String qualifier, Class<T> interfaceClass, long timeout);
}
