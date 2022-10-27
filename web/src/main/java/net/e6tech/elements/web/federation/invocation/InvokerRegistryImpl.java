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

package net.e6tech.elements.web.federation.invocation;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.federation.Frequency;
import net.e6tech.elements.common.federation.Member;
import net.e6tech.elements.common.federation.Registry;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.NotAvailableException;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.Local;
import net.e6tech.elements.web.federation.CollectiveImpl;
import net.e6tech.elements.web.federation.Service;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class InvokerRegistryImpl implements InvokerRegistry, Initializable {

    private static Cache<String, Set<String>> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(100)
            .expireAfterWrite(10 * 60 * 1000L, TimeUnit.MILLISECONDS)
            .build();

    private CollectiveImpl collective;
    private ConcurrentMap<String, Function<Object[], Object>> registrations = new ConcurrentHashMap<>();
    private volatile int roundRobin = new Random().nextInt(Integer.MAX_VALUE / 2);
    private Executor executor = runnable -> new Thread(runnable).start();


    @Override
    public void start() {
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        if (executor != null)
            this.executor = executor;
        else
            this.executor = runnable -> new Thread(runnable).start();
    }

    public CollectiveImpl getCollective() {
        return collective;
    }

    public void setCollective(CollectiveImpl collective) {
        this.collective = collective;
        InvokerRegistryAPI api = new InvokerRegistryAPI();
        api.setRegistry(this);
        api.setSubZero(collective.getSubZero());
        Service<InvokerRegistry, InvokerRegistryAPI> service = new Service(this, InvokerRegistryAPI.class, api);
        collective.addService(service);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void initialize(Resources resources) {
        if (collective == null) {
            throw new IllegalStateException("Federation not set.");
        }
    }

    /**
     *
     * @param path fully qualified path, this is the unique key. registering to the cluster and self.
     * @param invoker takes arguments and execute method call to return a value
     * @return value based on provided arguments
     */
    protected String register(String path, Function<Object[], Object> invoker) {
        registrations.put(path, invoker);
        return path;
    }

    public synchronized  <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation) {
        return register(qualifier, interfaceClass, implementation, null);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public synchronized  <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation, InvocationHandler customizedInvoker) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass " + interfaceClass.getName() + " needs to be an interface.");
        if (!Modifier.isPublic(interfaceClass.getModifiers()))
            throw new IllegalArgumentException("interfaceClass " + interfaceClass.getName() + " needs to be public.");

        ArrayList<String> list = new ArrayList<>();
        for (Method method : interfaceClass.getMethods()) {
            Annotation local = method.getAnnotation(Local.class);
            if (local != null)
                continue;

            String methodName = method.getName();
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0
                    || "equals".equals(methodName) && method.getParameterCount() == 1
                    || "toString".equals(methodName) && method.getParameterCount() == 0) {
                // ignored
            } else if (implementation == null)  {
                list.add(register(fullyQualify(qualifier, interfaceClass, method), null));
            } else {
                if (customizedInvoker == null) {
                    customizedInvoker = (target, meth, args) -> meth.invoke(target, args);
                }
                InvocationHandler invoker = customizedInvoker;
                list.add(register(fullyQualify(qualifier, interfaceClass, method), args -> {
                    try {
                        return invoker.invoke(implementation, method, args);
                    } catch (Throwable e) {
                        if (e instanceof RuntimeException)
                            throw (RuntimeException) e;
                        throw new SystemException(e);
                    }
                }));
            }
        }

        for (Member m : collective.getHostedMembers().values())
            cache.invalidate(m.getMemberId());
        return list;
    }

    String fullyQualify(String qualifier, Class interfaceClass, Method method) {
        return Registry.fullyQualify(qualifier, interfaceClass, method);
    }

    /**
     * Return a Function.  When applied with args, the function returns a CompletableFuture which will contain the result of the call.
     * @param qualifier route qualifier, this allows more than one identifical interfaces to be register
     * @param interfaceClass interface class to be registered
     * @param method method to be called.
     * @return a function that converts args to Future.
     */
    @Override
    public <R> Function<Object[], CompletableFuture<R>> route(String qualifier, Class interfaceClass, Method method, Routing routing) {
        String path = fullyQualify(qualifier, interfaceClass, method);
        Collection<Frequency> frequencies = collective.frequencies();
        List<Frequency> applicable = new LinkedList<>();
        for (Frequency f : frequencies) {
            Set<String> paths = routes(f);
            if (paths.contains(path)) {
                applicable.add(f);
            }
        }

        if (applicable.isEmpty()) {
            throw new NotAvailableException("No route for path=" + path);
        }

        return args -> {
            if (routing != Routing.local) {
                if (roundRobin == Integer.MAX_VALUE / 2)
                    roundRobin = 0;
                roundRobin++;
            }

            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
                if (routing == Routing.local ) {
                    for (int i = 0; i < applicable.size(); i++) {
                        Frequency frequency = applicable.get(i);
                        if (collective.getHostedMembers().containsKey(frequency.memberId())) { // local call, if true
                            InvokerRegistry registry = collective.getServiceProvider(InvokerRegistry.class);
                            return (R) registry.invoke(path, args);
                        }
                    }
                }
                int select = roundRobin % applicable.size();
                for (int i = 0; i < applicable.size(); i++) {
                    Frequency frequency = applicable.get(select);

                    // if routing is remote and current frequency is local, skip unless this is the last item.
                    if (collective.getHostedMembers().containsKey(frequency.memberId()) && routing == Routing.remote && i < applicable.size() - 1) {
                        continue;
                    }

                    if (collective.getHostedMembers().containsKey(frequency.memberId())) { // local call, if true
                        InvokerRegistry registry = collective.getServiceProvider(InvokerRegistry.class);
                        return (R) registry.invoke(path, args);
                    } else {
                        InvokerRegistryAPI api = frequency.getService(InvokerRegistryAPI.class);
                        InvokerRegistryAPI.Request request = new InvokerRegistryAPI.Request(path, args, collective.getSubZero());
                        InvokerRegistryAPI.Response response = api.invoke(request);
                        if (response == null) {
                            select = (select + 1) % applicable.size();
                            continue;
                        }
                        return collective.getSubZero().thaw(response.getFrozen());
                    }
                }
                throw new NotAvailableException("No service found for qualifier=" + qualifier + " class=" + interfaceClass + " method=" + method);
            }, executor);
            return future;
        };
    }

    private Set<String> routes(Frequency frequency) {
        Set<String> paths = cache.getIfPresent(frequency.memberId());
        if (paths == null) {
            paths = Collections.emptySet();
            InvokerRegistryAPI api = frequency.getService(InvokerRegistryAPI.class);
            if (api != null) {
                try {
                    Set<String> routes = api.routes();
                    if (!routes.isEmpty())
                        cache.put(frequency.memberId(), routes);
                    paths = routes;
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
        return paths;
    }

    @Override
    public Collection routes(String qualifier, Class interfaceClass) {
        if (!interfaceClass.isInterface())
            throw new IllegalArgumentException("interfaceClass needs to be an interface");

        Collection<Frequency> frequencies = collective.frequencies();
        List<Frequency> applicable = new LinkedList<>();
        for (Frequency f : frequencies) {
            for (Method method : interfaceClass.getMethods()) {
                Annotation local = method.getAnnotation(Local.class);
                if (local != null)
                    continue;
                String methodName = method.getName();
                if ("hashCode".equals(methodName) && method.getParameterCount() == 0
                        || "equals".equals(methodName) && method.getParameterCount() == 1
                        || "toString".equals(methodName) && method.getParameterCount() == 0) {
                    continue;
                }

                String path = fullyQualify(qualifier, interfaceClass, method);
                Set<String> paths = routes(f);
                if (paths.contains(path)) {
                    applicable.add(f);
                    break;
                }
            }
        }
        return applicable;
    }

    @Override
    public Set<String> routes() {
        return registrations.keySet();
    }

    @Override
    public Object invoke(String path, Object[] arguments) {
        Function<Object[], Object> func = registrations.get(path);
        if (func == null)
            throw new NotAvailableException("No service found for path=" + path);
        return func.apply(arguments);
    }

    @Override
    public <R> Async<R> async(String qualifier, Class<R> interfaceClass) {
        return new AsyncImpl<>(this, qualifier, interfaceClass, collective.getReadTimeout(), executor);
    }

    @Override
    public <R> Async<R> async(String qualifier, Class<R> interfaceClass, long timeout, Routing routing) {
        long t = timeout > 0 ? timeout : collective.getReadTimeout();
        AsyncImpl impl = new AsyncImpl<>(this, qualifier, interfaceClass, t, executor);
        impl.setRouting(routing);
        return impl;
    }

}
