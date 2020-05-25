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

/**
 * This class is used to launch JaxRSServer programmatically
 */
public class JaxRSLauncher {
    Provision provision;
    JaxRSServer server;

    public static JaxRSLauncher create(ResourceManager resourceManager, String url) {
        Provision provision = resourceManager.getInstance(Provision.class);
        return create(provision, url);
    }

    public static JaxRSLauncher create(Provision provision, String url) {
        JaxRSLauncher launcher = new JaxRSLauncher();
        launcher.server = provision.newInstance(JaxRSServer.class);
        launcher.provision = provision;
        try {
            launcher.server.setAddresses(Arrays.asList(url));
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

    public JaxRSLauncher setHeaderObserver(Observer observer) {
        server.setHeaderObserver(observer);
        return this;
    }

    public JaxRSLauncher setResolver(Configuration.Resolver resolver) {
        server.setResolver(resolver);
        return this;
    }

    public JaxRSLauncher add(JaxResource resource) {
        server.add(resource);
        return this;
    }

    public boolean isStarted() {
        if (server == null)
            return false;
        return server.isStarted();
    }

    public JaxRSLauncher start() {
        provision.open().accept(Resources.class, res -> {
            server.initialize(res);
            server.start();
        });
        return this;
    }

    public JaxRSLauncher stop() {
        server.stop();
        return this;
    }
}
