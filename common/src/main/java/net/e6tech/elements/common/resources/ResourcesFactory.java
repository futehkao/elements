/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package net.e6tech.elements.common.resources;


import net.e6tech.elements.common.inject.Inject;

import java.util.function.Consumer;

/**
 * Created by futeh on 12/19/15.
 */
public class ResourcesFactory implements Configurable<ResourcesFactory> {
    private Provision provision;

    private Configurator configurator = new Configurator();

    Consumer<Resources> preOpen;

    @Override
    public Configurator configurator() {
        return configurator;
    }

    @Override
    public ResourcesFactory configurable() {
        return this;
    }

    // for groovy configuration
    public ResourcesFactory preOpen(Consumer<Resources> consumer) {
        preOpen = consumer;
        return this;
    }

    public UnitOfWork open() {
        UnitOfWork uow = provision.preOpen(preOpen);
        uow.configurator().putAll(configurator);
        return uow;
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }
}
