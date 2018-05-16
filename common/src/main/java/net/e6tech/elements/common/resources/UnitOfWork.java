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

import net.e6tech.elements.common.util.function.ConsumerWithException;
import net.e6tech.elements.common.util.function.FunctionWithException;
import net.e6tech.elements.common.util.function.RunnableWithException;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
public class UnitOfWork implements Transactional, Configurable<UnitOfWork> {
    private static final String RESOURCES_NOT_OPEN = "Resources not open.";
    ResourceManager resourceManager;
    List<ResourceProvider> resourceProviders = new LinkedList<>();
    Consumer<Resources> preOpen;
    private Configurator configurator = new Configurator();
    Resources resources;

    public UnitOfWork(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    // used for configuring resourcesManager's resourceProviders before Resources is open
    public UnitOfWork preOpen(Consumer<Resources> consumer) {
        preOpen = consumer;
        return this;
    }

    public UnitOfWork onOpen(OnOpen onOpen) {
        resourceProviders.add(onOpen);
        return this;
    }

    public Resources getResources() {
        return resources;
    }

    public Configurator configurator() {
        return configurator;
    }

    public UnitOfWork configurable() { return  this; }

    public <T extends Resources> T open() {
        if (resources != null && resources.isOpen())
            return (T) resources;
        resources = resourceManager.open(this.configurator, r -> {
            if (preOpen != null)
                preOpen.accept(r);
            for (ResourceProvider p : resourceProviders) {
                r.addResourceProvider(p);
            }
        });
        return (T) resources;
    }

    public void commit() {
        if (resources == null || !resources.isOpen())
            throw new IllegalStateException("Resources not opened");
        resources.commit();
        cleanup();
    }

    public void abort() {
        if (resources == null || !resources.isOpen())
            return;
        resources.abort();
        cleanup();
    }

    protected void cleanup() {
        resourceProviders.clear();
        resources = null;
        configurator.clear();
        preOpen = null;
    }

    public void submit(RunnableWithException work) {
        if (resources == null || !resources.isOpen())
            throw new IllegalStateException(RESOURCES_NOT_OPEN);
        resources.submit((ConsumerWithException<Resources, Exception>) res -> work.run());
    }

    public <T extends Resources> void submit(ConsumerWithException<T, Exception> work) {
        if (resources == null || !resources.isOpen())
            throw new IllegalStateException(RESOURCES_NOT_OPEN);
        resources.submit(work);
    }

    public <T> T submit(Callable<T> work) {
        if (resources == null || !resources.isOpen())
            throw new IllegalStateException(RESOURCES_NOT_OPEN);
        return resources.submit((FunctionWithException<Resources, T, Exception>) res -> work.call());
    }

    public <T extends Resources, R> R submit(FunctionWithException<T, R, Exception> work) {
        if (resources == null || !resources.isOpen())
            throw new IllegalStateException(RESOURCES_NOT_OPEN);
        return resources.submit(work);
    }

    public boolean isOpened() {
        if (resources == null)
            return false;
        return resources.isOpen();
    }

    public boolean isAborted() {
        if (resources == null)
            return false;
        return resources.isAborted();
    }
}
