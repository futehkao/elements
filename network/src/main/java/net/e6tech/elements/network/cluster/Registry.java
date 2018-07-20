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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.Patterns;
import net.e6tech.elements.common.actor.pool.WorkerPool;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.ThreadPool;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    long timeout = 5000L;
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

    public <R> void register(String path, Function<Object[], R> function) {
        Patterns.ask(registrar, new Events.Registration(path, (Function<Object[], Object>) function, 0L), this.timeout);
    }

    public <R> void register(String path, Function<Object[], R> function, long timeout) {
        Patterns.ask(registrar, new Events.Registration(path, (Function<Object[], Object>) function, timeout), this.timeout);
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
    public <T> void register(String qualifier, Class<T> interfaceClass, T implementation, long timeout) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");

        for (Method method : interfaceClass.getMethods()) {
            String methodName = method.getName();
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0
                    || "equals".equals(methodName) && method.getParameterCount() == 1
                    || "toString".equals(methodName) && method.getParameterCount() == 0) {
                // ignored
            } else {
                register(fullyQualify(qualifier, interfaceClass, method),
                        t -> {
                            try {
                                return method.invoke(implementation, t);
                            } catch (IllegalAccessException e) {
                                throw new SystemException(e);
                            } catch (InvocationTargetException e) {
                                Logger.suppress(e);
                                throw new SystemException(e.getCause());
                            }
                        }, timeout);
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
    public Function<Object[], CompletionStage> route(String qualifier, Class interfaceClass, Method method, long timeout) {
        return route(fullyQualify(qualifier, interfaceClass, method), timeout);
    }

    public Function<Object[], CompletionStage> route(String path, long timeout) {
        return (Function<Object[], CompletionStage>) arguments -> {
            Future future = Patterns.ask(registrar, new Events.Invocation(path, arguments), timeout);
            return FutureConverters.toJava(future).thenApplyAsync(ret -> {
                Events.Response response = (Events.Response) ret;
                return response.getValue();
            });
        };
    }

    public <T> Async<T> async(String qualifier, Class<T> interfaceClass) {
        return new Async<>(this, qualifier, interfaceClass, getTimeout());
    }

    public <T> Async<T> async(String qualifier, Class<T> interfaceClass, long timeout) {
        return new Async<>(this, qualifier, interfaceClass, timeout);
    }
}
