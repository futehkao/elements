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
    List<RouteListener> listeners = new ArrayList<>();

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

    public void start(ActorSystem system) {
        this.system = system;
        registrar = system.actorOf(Props.create(RegistrarActor.class, () -> new RegistrarActor(this)), PATH);
    }

    public void shutdown() {
        Patterns.ask(registrar, PoisonPill.getInstance(), 5000L);
    }

    public <T, R> void register(String qualifier, Class<T> messageType, Class<R> returnType, Function<T, R> function) {
        Patterns.ask(registrar, new Events.Registration(qualifier, messageType, returnType, function), 5000L);
    }

    public <T> void register(String qualifier, Class<T> interfaceClass, T implementation) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");
        qualifier = (qualifier == null) ? "" : qualifier.trim();
        for (Method method : interfaceClass.getMethods()) {
            if (method.getName().equals("hashCode") && method.getParameterCount() == 0
                    || method.getName().equals("equals") && method.getParameterCount() == 1
                    || method.getName().equals("toString") && method.getParameterCount() == 0) {
                // ignored
            } else {
                if (method.getParameterCount() == 1) {
                    register(fullyQualify(qualifier, interfaceClass, method), method.getParameterTypes()[0], (Class) method.getReturnType(),
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
    }

    @SuppressWarnings("unchecked")
    Function route(String qualifier, Class interfaceClass, Method method, long timeout) {
        return route(fullyQualify(qualifier, interfaceClass, method), (Class) method.getParameterTypes()[0], method.getReturnType(), timeout);
    }

    private String fullyQualify(String qualifier, Class interfaceClass, Method method) {
        qualifier = (qualifier == null) ? "" : qualifier.trim();
        return qualifier + "@" + interfaceClass.getName() + "." +  method.getName();
    }

    @SuppressWarnings("unchecked")
    public <T, R> Function<T, CompletionStage<R>> route(String qualifier, Class<T> messageClass, Class<R> returnType, long timeout) {
        Function<T, CompletionStage<R>> function = (message) -> {
            Future future = Patterns.ask(registrar, new Events.Invocation(qualifier, messageClass, message, returnType), timeout);
            return FutureConverters.toJava(future).thenApply((ret) -> {
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
