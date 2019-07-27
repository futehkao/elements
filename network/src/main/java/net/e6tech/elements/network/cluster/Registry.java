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

import akka.actor.*;
import akka.pattern.Patterns;
import net.e6tech.elements.common.actor.pool.WorkerPool;
import net.e6tech.elements.common.util.concurrent.ThreadPool;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A registry that contains cluster wide services.
 *
 * Created by futeh.
 */
public class Registry {

    private static String path = "registry";
    public static final String REGISTRY_DISPATCHER = "registry-dispatcher";

    private static ThreadPool threadPool = ThreadPool.cachedThreadPool("Cluster-Registry");

    ActorSystem system;
    ActorRef registrar;
    ActorRef workerPool;
    long timeout = ClusterNode.DEFAULT_TIME_OUT;
    List<RouteListener> listeners = new ArrayList<>();

    public static ThreadPool getThreadPool() {
        return threadPool;
    }

    public static String getPath() {
        return path;
    }

    public static void setPath(String path) {
        Registry.path = path;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void addRouteListener(RouteListener listener) {
        listeners.add(listener);
    }

    public void removeRouteListener(RouteListener listener) {
        listeners.remove(listener);
    }

    void onAnnouncement(String path) {
        system.dispatcher().execute(() -> {
            for (RouteListener l : listeners) l.onAnnouncement(path);
        });
    }

    void onTerminated(String path, ActorRef actor) {
        system.dispatcher().execute(() -> {
            for (RouteListener l : listeners) l.onTerminated(path, actor.path().toString());
        });
    }

    void onRouteRemoved(String path) {
        system.dispatcher().execute(() -> {
            for (RouteListener l : listeners) l.onRouteRemoved(path);
        });
    }

    public ActorRef getWorkerPool() {
        return workerPool;
    }

    public void setWorkerPool(ActorRef workerPool) {
        this.workerPool = workerPool;
    }

    public void start(ActorSystem system) {
        this.system = system;
        if (workerPool == null)
            workerPool = system.actorOf(Props.create(WorkerPool.class));
        registrar = system.actorOf(Props.create(RegistrarActor.class, () -> new RegistrarActor(this, workerPool)), getPath());
    }

    public void shutdown() {
        Patterns.ask(registrar, PoisonPill.getInstance(), timeout);
    }

    public <R> void register(String path, BiFunction<Actor, Object[], R> function, long timeout) {
        Patterns.ask(registrar, new Events.Registration(path, (BiFunction<Actor, Object[], Object>) function, timeout), this.timeout);
    }

    public <T> Collection routes(String qualifier, Class<T> interfaceClass) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");
        for (Method method : interfaceClass.getMethods()) {
            Local local = method.getAnnotation(Local.class);
            if (local != null)
                continue;
            String methodName = method.getName();
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0
                    || "equals".equals(methodName) && method.getParameterCount() == 1
                    || "toString".equals(methodName) && method.getParameterCount() == 0) {
                // ignored
            } else {
                String p = fullyQualify(qualifier, interfaceClass, method);
                return routes(p);
            }
        }
        return Collections.emptyList();
    }

    public Collection routes(String path) {
        Future<Events.Response> future = (Future) Patterns.ask(registrar, new Events.Routes(path), this.timeout);
        return FutureConverters.toJava(future)
                .toCompletableFuture()
                .thenApply(response -> (Collection)response.getValue())
                .join();
    }

    public <T> void register(String qualifier, Class<T> interfaceClass, T implementation, long timeout) {
        register(qualifier, interfaceClass, implementation, null, timeout);
    }

    /**
     *
     * @param qualifier a unique name for the service
     * @param interfaceClass Interface class.  Its methods will be registered and, therefore, it is important
     *                       for the qualifier to be unique.
     * @param implementation implementation of the interface
     * @param <T> type of implementation
     * @param timeout timout period
     */
    @SuppressWarnings({"squid:S1067", "squid:S3776"})
    public <T> void register(String qualifier, Class<T> interfaceClass, T implementation, Invoker customizedInvoker, long timeout) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");

        for (Method method : interfaceClass.getMethods()) {
            Local local = method.getAnnotation(Local.class);
            if (local != null)
                continue;
            String methodName = method.getName();
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0
                    || "equals".equals(methodName) && method.getParameterCount() == 1
                    || "toString".equals(methodName) && method.getParameterCount() == 0) {
                // ignored
            } else {
                if (customizedInvoker == null) {
                    customizedInvoker = new Invoker();
                }
                Invoker invoker = customizedInvoker;
                register(fullyQualify(qualifier, interfaceClass, method),
                        (actor, args) -> invoker.invoke(actor, implementation, method, args), timeout);
            }
        }
    }

    String fullyQualify(String qualifier, Class interfaceClass, Method method) {
        StringBuilder builder = new StringBuilder();
        String normalizedQualifier = (qualifier == null) ? "" : qualifier.trim();
        if (normalizedQualifier.length() > 0) {
            builder.append(normalizedQualifier);
            builder.append("@");
        }
        builder.append(interfaceClass.getName());
        builder.append("::");
        builder.append(method.getName());
        builder.append("(");
        boolean first = true;
        for (Class param : method.getParameterTypes()) {
            if (first)
                first = false;
            else {
                builder.append(",");
            }
            builder.append(param.getTypeName());
        }
        builder.append(")");
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    public Function<Object[], CompletionStage<Events.Response>> route(String qualifier, Class interfaceClass, Method method, long timeout) {
        return route(fullyQualify(qualifier, interfaceClass, method), timeout);
    }

    public Function<Object[], CompletionStage<Events.Response>> route(String path, long timeout) {
        return arguments -> {
            Future future = Patterns.ask(registrar, new Events.Invocation(path, arguments), timeout);
            return FutureConverters.toJava(future).thenApplyAsync(ret -> ret);
        };
    }

    public <T> ClusterAsync<T> async(String qualifier, Class<T> interfaceClass) {
        return new AsyncImpl<>(this, qualifier, interfaceClass, getTimeout());
    }

    public <T> ClusterAsync<T> async(String qualifier, Class<T> interfaceClass, long timeout) {
        return new AsyncImpl<>(this, qualifier, interfaceClass, timeout);
    }
}
