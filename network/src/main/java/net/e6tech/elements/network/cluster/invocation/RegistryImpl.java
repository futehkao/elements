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

import akka.actor.typed.ActorRef;
import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.cluster.AsyncImpl;
import net.e6tech.elements.network.cluster.ClusterAsync;
import net.e6tech.elements.network.cluster.ClusterNode;
import net.e6tech.elements.network.cluster.RouteListener;
import scala.concurrent.ExecutionContextExecutor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RegistryImpl implements Registry {
    private static String path = "registry";
    public static final String REGISTRY_DISPATCHER = "registry-dispatcher";
    private Guardian guardian;
    private Registrar registrar;
    private ExecutionContextExecutor dispatcher;
    private long timeout = ClusterNode.DEFAULT_TIME_OUT;
    private List<RouteListener> listeners = Collections.synchronizedList(new ArrayList<>());

    public static String getPath() {
        return path;
    }

    public static void setPath(String path) {
        RegistryImpl.path = path;
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
        dispatcher.execute(() -> {
            List<RouteListener> list = new ArrayList<>(listeners);
            for (RouteListener l : list)
                l.onAnnouncement(path);
        });
    }

    void onTerminated(String path, ActorRef actor) {
        dispatcher.execute(() -> {
            List<RouteListener> list = new ArrayList<>(listeners);
            for (RouteListener l : list)
                l.onTerminated(path, actor.path().toString());
        });
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public void start(Guardian guardian) {
        this.guardian = guardian;
        dispatcher = guardian.getContext().getExecutionContext();
        // Create an Akka system
        registrar = guardian.childActor(Registrar.class).withName(getPath()).spawnNow(new Registrar(this));
    }

    public void shutdown() {
        registrar.talk().stop();
    }

    public Collection routes(String path) {
        return (Collection) registrar.talk(timeout).askAndWait(InvocationEvents.Response.class,
                ref -> new InvocationEvents.Routes(ref, path)).getValue();
    }

    public Collection routes(String qualifier, Class interfaceClass) {
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

    @SuppressWarnings("unchecked")
    @Override
    public <R, U> CompletionStage<U> register(String path, BiFunction<ActorRef, Object[], R> function) {
        return registrar.talk(timeout).ask(ref -> new InvocationEvents.Registration(ref, path,  (BiFunction<ActorRef, Object[], Object>)function));
    }

    @Override
    public <T, U> CompletionStage<List<U>> register(String qualifier, Class<T> interfaceClass, T implementation) {
        return register(qualifier, interfaceClass, implementation, null);
    }

    @Override
    public <T, U> CompletionStage<List<U>> discover(String qualifier, Class<T> interfaceClass) {
        return register(qualifier, interfaceClass, null, null);
    }

    /**
     *
     * @param qualifier a unique name for the service
     * @param interfaceClass Interface class.  Its methods will be registered and, therefore, it is important
     *                       for the qualifier to be unique.
     * @param implementation implementation of the interface
     * @param <T> type of implementation
     */
    @SuppressWarnings({"unchecked", "squid:S1067", "squid:S3776"})
    @Override
    public <T, U> CompletionStage<List<U>> register(String qualifier, Class<T> interfaceClass, T implementation, Invoker customizedInvoker) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass " + interfaceClass.getName() + " needs to be an interface.");
        if (!Modifier.isPublic(interfaceClass.getModifiers()))
            throw new IllegalArgumentException("interfaceClass " + interfaceClass.getName() + " needs to be public.");

        ArrayList<CompletableFuture<U>> list = new ArrayList<>();
        for (Method method : interfaceClass.getMethods()) {
            Local local = method.getAnnotation(Local.class);
            if (local != null)
                continue;
            String methodName = method.getName();
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0
                    || "equals".equals(methodName) && method.getParameterCount() == 1
                    || "toString".equals(methodName) && method.getParameterCount() == 0) {
                // ignored
            } else if (implementation == null)  {
                // not expecting
                register(fullyQualify(qualifier, interfaceClass, method), null).toCompletableFuture();
            } else {
                if (customizedInvoker == null) {
                    customizedInvoker = new Invoker();
                }
                Invoker invoker = customizedInvoker;
                list.add((CompletableFuture) register(fullyQualify(qualifier, interfaceClass, method),
                        (actor, args) -> invoker.invoke(actor, implementation, method, args)).toCompletableFuture());
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            List<U> results = new ArrayList<>();
            for (CompletableFuture<U> stage : list) {
                try {
                    results.add(stage.get(getTimeout(), TimeUnit.MILLISECONDS));
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
            return results;
        });
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

    public Function<Object[], CompletionStage<InvocationEvents.Response>> route(String qualifier, Class interfaceClass, Method method, long timeout) {
        return route(fullyQualify(qualifier, interfaceClass, method), timeout);
    }

    public Function<Object[], CompletionStage<InvocationEvents.Response>> route(String path, long timeout) {
        return arguments -> registrar.talk(timeout).ask(ref -> new InvocationEvents.Request(ref, path, timeout, arguments));
    }

    public <T> ClusterAsync<T> async(String qualifier, Class<T> interfaceClass) {
        return new AsyncImpl<>(this, qualifier, interfaceClass, getTimeout());
    }

    public <T> ClusterAsync<T> async(String qualifier, Class<T> interfaceClass, long timeout) {
        return new AsyncImpl<>(this, qualifier, interfaceClass, timeout);
    }
}
