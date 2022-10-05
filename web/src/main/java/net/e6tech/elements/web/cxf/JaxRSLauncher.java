/*
 * Copyright 2015-2020 Futeh Kao
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

package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.resources.Configuration;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This class is used to launch JaxRSServer programmatically
 */
public class JaxRSLauncher {
    private Provision provision;
    private JaxRSServer server;
    private int instanceId = 1;
    private Map<String, Object> instances = new HashMap<>();
    private Configuration.Resolver resolver = key -> instances.get(key);

    public static JaxRSLauncher create(ResourceManager resourceManager, String url) {
        Provision provision = resourceManager.getInstance(Provision.class);
        return create(provision, url);
    }

    public static JaxRSLauncher create(Provision provision, String url) {
        JaxRSLauncher launcher = new JaxRSLauncher();
        if (provision != null) {
            launcher.server = provision.newInstance(JaxRSServer.class);
        } else {
            launcher.server = new JaxRSServer();
        }
        launcher.provision = provision;
        try {
            launcher.server.setAddresses(Arrays.asList(url));
            launcher.resolver(launcher.resolver);
        } catch (MalformedURLException e) {
            throw new SystemException(e);
        }
        return launcher;
    }

    public Provision getProvision() {
        return provision;
    }

    public JaxRSServer getServer() {
        return server;
    }

    public JaxRSLauncher headerObserver(Observer observer) {
        server.setHeaderObserver(observer);
        return this;
    }

    public JaxRSLauncher resolver(Configuration.Resolver r) {
        Configuration.Resolver wrap = key -> {
            Object found = instances.get(key);
            if (found != null)
                return found;
            return r.resolve(key);
        };
        server.setResolver(wrap);
        return this;
    }

    public JaxRSLauncher add(JaxResource resource) {
        server.add(resource);
        return this;
    }

    public <T> JaxRSLauncher sharedService(Class<T> cls) {
        T api = provision.newInstance(cls);
        add(new JaxResource(cls)
                .prototype(Integer.toString(instanceId))
                .singleton());
        instances.put(Integer.toString(instanceId), api);
        instanceId ++;
        return this;
    }

    public <T> JaxRSLauncher shareInstanceService(T prototype) {
        add(new JaxResource(prototype.getClass())
                .prototypeInstance(prototype)
                .singleton());
        return this;
    }

    public <T> JaxRSLauncher perInstanceService(Class<T> cls) {
        add(new JaxResource(cls));
        return this;
    }

    public <T> JaxRSLauncher perInstanceService(T prototype) {
        add(new JaxResource(prototype.getClass())
                .prototype(Integer.toString(instanceId)));
        instances.put(Integer.toString(instanceId), prototype);
        instanceId ++;
        return this;
    }

    public JaxRSLauncher accept(Consumer<JaxRSLauncher> consumer) {
        consumer.accept(this);
        return this;
    }

    public JaxRSLauncher accept(BiConsumer<JaxRSLauncher, JaxResource> consumer) {
        JaxResource last = null;
        if (!server.getJaxResources().isEmpty())
            last = server.getJaxResources().get(server.getJaxResources().size() - 1);
        consumer.accept(this, last);
        return this;
    }

    public boolean isStarted() {
        if (server == null)
            return false;
        return server.isStarted();
    }

    public JaxRSLauncher start() {
        if (provision != null) {
            provision.open().accept(Resources.class, res -> {
                server.initialize(res);
                server.start();
            });
        } else {
            server.initialize(null);
            server.start();
        }
        return this;
    }

    public JaxRSLauncher stop() {
        server.stop();
        return this;
    }
}
