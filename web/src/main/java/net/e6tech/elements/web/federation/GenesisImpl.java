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

package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.federation.Genesis;
import net.e6tech.elements.common.federation.Registration;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.resources.Configuration;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.AsyncImpl;
import net.e6tech.elements.common.federation.Registry;
import net.e6tech.elements.web.federation.invocation.InvokerRegistry;
import net.e6tech.elements.web.federation.invocation.InvokerRegistryImpl;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class GenesisImpl implements Initializable, Genesis {
    private ClusterImpl cluster = new ClusterImpl();
    private Provision provision;
    private List<Registration> registrations = new LinkedList<>();
    private Configuration.Resolver resolver;

    public String getName() {
        return cluster.getDomainName();
    }

    public void setName(String name) {
        cluster.getDomainName();
    }

    public String getDomainName() {
        return cluster.getDomainName();
    }

    public void setDomainName(String name) {
        cluster.setDomainName(name);
    }

    public ClusterImpl getCluster() {
        return cluster;
    }

    public void setCluster(ClusterImpl cluster) {
        this.cluster = cluster;
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public List<Registration> getRegistrations() {
        return registrations;
    }

    public void setRegistrations(List<Registration> registrations) {
        this.registrations = registrations;
    }

    public Configuration.Resolver getResolver() {
        return resolver;
    }

    @Inject(optional = true)
    public void setResolver(Configuration.Resolver resolver) {
        this.resolver = resolver;
    }

    public void initialize(Resources resources) {
        // start cluster.
        if (!StringUtil.isNullOrEmpty(cluster.getHostAddress())) {
            InvokerRegistryImpl registry = provision.newInstance(InvokerRegistryImpl.class);
            registry.setExecutor(provision.getExecutor());
            registry.setCollective(cluster);
            registry.initialize(null);

            provision.inject(cluster);

            for (Registration reg : registrations) {
                if (resolver != null && reg.getImplementation() instanceof String)
                    reg.setImplementation(resolver.resolve(reg.getImplementation().toString()));
                if (reg.getInterfaceClass() != null) {
                    if (reg.getImplementation() != null)
                        cluster.register(reg.getQualifier(), reg.getInterfaceClass(), reg.getImplementation());
                    else {
                        try {
                            Object instance = reg.getInterfaceClass().newInstance();
                            Type[] types = Module.load(reg.getInterfaceClass());
                            for (Type type : types) {
                                if (type instanceof Class && ((Class<?>) type).isInterface())
                                    cluster.register(reg.getQualifier(), (Class) type, instance);
                            }
                        } catch (InstantiationException e) {
                            throw new SystemException(e);
                        } catch (IllegalAccessException e) {
                            throw new SystemException(e);
                        }
                    }
                } else if (reg.getImplementation() != null) {
                    Type[] types = Module.load(reg.getImplementation().getClass());
                    for (Type type : types) {
                        if (type instanceof Class && ((Class<?>) type).isInterface())
                            cluster.register(reg.getQualifier(), (Class) type, reg.getImplementation());
                    }
                }
            }

            cluster.start();
            provision.getResourceManager().getNotificationCenter().addBroadcast(cluster);
        }
    }

    public Registry getRegistry() {
        return cluster.getServiceProvider(InvokerRegistry.class);
    }

    public void shutdown() {
        cluster.shutdown();
    }

    @Override
    public CompletionStage<Void> async(Runnable runnable) {
        return new AsyncImpl<>(provision.getExecutor(), runnable).accept(Runnable::run);
    }

    @Override
    public <R> CompletionStage<R> async(Supplier<R> supplier) {
        return new AsyncImpl<>(provision.getExecutor(), supplier).apply(Supplier::get);
    }
}
