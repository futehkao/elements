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
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.NotAvailableException;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.Local;
import net.e6tech.elements.web.federation.Collective;
import net.e6tech.elements.web.federation.HailingFrequency;
import net.e6tech.elements.web.federation.Service;
import net.e6tech.elements.web.federation.SubZero;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

public class InvokerRegistryImpl implements InvokerRegistry, Initializable {

    private static Cache<String, Set<String>> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(100)
            .expireAfterWrite(10 * 60 * 1000L, TimeUnit.MILLISECONDS)
            .build();

    private Collective collective;
    private long timeout = 10000L;
    private ConcurrentMap<String, Function<Object[], Object>> registrations = new ConcurrentHashMap<>();
    private int readTimeout = 10000;
    private int connectionTimeout = 15000;
    private volatile int roundRobin = new Random().nextInt(Integer.MAX_VALUE / 2);
    private SubZero subZero = new SubZero();

    private Executor executor = runnable -> new Thread(runnable).start();

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public void start() {
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
        if (executor != null)
            this.executor = executor;
        else
            this.executor = runnable -> new Thread(runnable).start();
    }

    public Collective getCollective() {
        return collective;
    }

    public void setCollective(Collective collective) {
        this.collective = collective;
        InvokerRegistryAPI api = new InvokerRegistryAPI();
        api.setRegistry(this);
        api.setSubZero(subZero);
        Service<InvokerRegistry, InvokerRegistryAPI> service = new Service(this, InvokerRegistryAPI.class, api);
        service.setReadTimeout(readTimeout);
        service.setConnectionTimeout(connectionTimeout);
        collective.addService(service);
    }

    @Override
    public void shutdown() {
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
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
     * @param invoker
     * @return
     */
    protected String register(String path, Function<Object[], Object> invoker) {
        registrations.put(path, invoker);
        return path;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public synchronized  <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation, Invoker customizedInvoker) {
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
                    customizedInvoker = new Invoker();
                }
                Invoker invoker = customizedInvoker;
                list.add(register(fullyQualify(qualifier, interfaceClass, method), args -> invoker.invoke(implementation, method, args)));
            }
        }
        return list;
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

    /**
     * Return a Function.  When applied with args, the function returns a CompletableFuture which will contain the result of the call.
     * @param qualifier
     * @param interfaceClass
     * @param method
     * @return
     * @param <R>
     */
    @Override
    public <R> Function<Object[], CompletableFuture<R>> route(String qualifier, Class interfaceClass, Method method) {
        String path = fullyQualify(qualifier, interfaceClass, method);
        Collection<HailingFrequency> frequencies = collective.frequencies();
        List<HailingFrequency> applicable = new LinkedList<>();
        for (HailingFrequency f : frequencies) {
            Set<String> paths = routes(f);
            if (paths != null && paths.contains(path)) {
                applicable.add(f);
            }
        }

        if (applicable.isEmpty()) {
            throw new NotAvailableException("No route for path=" + path);
        }

        return args -> {
            if (roundRobin == Integer.MAX_VALUE / 2)
                roundRobin = 0;
            roundRobin ++;
            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
                int select = roundRobin % applicable.size();
                for (int i = 0; i < applicable.size(); i++) {
                    HailingFrequency frequency = applicable.get(select);
                    if (collective.getHostedMembers().containsKey(frequency.getMember().getMemberId())) { // local call, if true
                        InvokerRegistry registry = collective.getServiceProvider(InvokerRegistry.class);
                        return (R) registry.invoke(path, args);
                    } else {
                        InvokerRegistryAPI api = frequency.getService(InvokerRegistryAPI.class);
                        InvokerRegistryAPI.Request request = new InvokerRegistryAPI.Request(path, args, subZero);
                        InvokerRegistryAPI.Response response = api.invoke(request);
                        if (response == null) {
                            select = (select + 1) % applicable.size();
                            continue;
                        }
                        return subZero.thaw(response.getFrozen());
                    }
                }
                throw new NotAvailableException("No service found for qualifier=" + qualifier + " class=" + interfaceClass + " method=" + method);
            }, executor);
            return future;
        };
    }

    private Set<String> routes(HailingFrequency frequency) {
        Set<String> paths = cache.getIfPresent(frequency.memberId());
        if (paths == null) {
            InvokerRegistryAPI api = frequency.getService(InvokerRegistryAPI.class);
            if (api != null) {
                try {
                    Set<String> routes = api.routes();
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

        Collection<HailingFrequency> frequencies = collective.frequencies();
        List<HailingFrequency> applicable = new LinkedList<>();
        for (HailingFrequency f : frequencies) {
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
                if (paths != null && paths.contains(path)) {
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
        return new AsyncImpl<>(this, qualifier, interfaceClass, getTimeout(), executor);
    }

    @Override
    public <R> Async<R> async(String qualifier, Class<R> interfaceClass, long timeout) {
        long t = timeout > 0 ? timeout : getTimeout();
        return new AsyncImpl<>(this, qualifier, interfaceClass, t, executor);
    }

}
