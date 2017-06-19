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
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class Registry {

    private static String PATH = "registry";

    public static String getPath() {
        return PATH;
    }

    public static void setPath(String PATH) {
        Registry.PATH = PATH;
    }

    ActorSystem system;
    ActorRef registrar;
    ActorRef workerPool;
    long timeout = 5000L;
    List<RouteListener> listeners = new ArrayList<>();

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
        if (workerPool == null) workerPool = system.actorOf(Props.create(WorkerPool.class));
        registrar = system.actorOf(Props.create(RegistrarActor.class, () -> new RegistrarActor(this, workerPool)), PATH);
    }

    public void shutdown() {
        Patterns.ask(registrar, PoisonPill.getInstance(), timeout);
    }

    public <R> void register(String path, Function<Object[], R> function) {
        Patterns.ask(registrar, new Events.Registration(path, function), timeout);
    }

    public <T> void register(String qualifier, Class<T> interfaceClass, T implementation) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");
        for (Method method : interfaceClass.getMethods()) {
            if (method.getName().equals("hashCode") && method.getParameterCount() == 0
                    || method.getName().equals("equals") && method.getParameterCount() == 1
                    || method.getName().equals("toString") && method.getParameterCount() == 0) {
                // ignored
            } else {
                register(fullyQualify(qualifier, interfaceClass, method),
                        t -> {
                            try {
                                return method.invoke(implementation, t);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException();
                            } catch (InvocationTargetException e) {
                                throw new RuntimeException(e.getCause());
                            }
                        });
            }
        }
    }

    String fullyQualify(String qualifier, Class interfaceClass, Method method) {
        StringBuilder builder = new StringBuilder();
        qualifier = (qualifier == null) ? "" : qualifier.trim();
        if (qualifier != null && qualifier.length() > 0) {
            builder.append(qualifier);
            builder.append("@");
        }
        builder.append(method.getName());
        builder.append("(");
        boolean first = true;
        for (Class param : method.getParameterTypes()) {
            if (first) first = false;
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
        Function<Object[], CompletionStage> function = (arguments) -> {
            Future future = Patterns.ask(registrar, new Events.Invocation(path, arguments), timeout);
            return FutureConverters.toJava(future).thenApplyAsync((ret) -> {
                Events.Response response = (Events.Response) ret;
                return response.getValue();
            });
        };
        return function;
    }

    public <T> Async<T> async(String qualifier, Class<T> interfaceClass, long timeout) {
        return new Async<>(this, qualifier, interfaceClass, timeout);
    }
}
