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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
public class UnitOfWork implements Transactional {
    ResourceManager resourceManager;
    List<ResourceProvider> resourceProviders = new LinkedList<>();
    Consumer<Resources> preOpen;
    Resources resources;
    List<ConsumerWithException<? extends Resources>> unitOfWork = new LinkedList<>();

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

    public <Res extends Resources> Res open() {
        if (resources != null && resources.isOpened()) return (Res) resources;
        resources = resourceManager.open((r) -> {
            if (preOpen != null) preOpen.accept(r);
            for (ResourceProvider p : resourceProviders) {
                r.addResourceProvider(p);
            }
        });
        return (Res) resources;
    }

    public void commit() {
        if (resources == null || !resources.isOpened()) throw new IllegalStateException("Resources not opened");
        resources.commit();
        resources = null;
    }

    public void abort() {
        if (resources == null || resources.isAborted() || resources.isClosed()) return;
        resources.abort();
    }

    public void submit(Transactional.RunnableWithException work) {
        if (resources == null || !resources.isOpened()) throw new IllegalStateException("Resources not opened");
        resources.submit((Transactional.ConsumerWithException<Resources>)(res)-> work.run());
    }

    public <Res extends Resources> void submit(Transactional.ConsumerWithException<Res> work) {
        if (resources == null || !resources.isOpened()) throw new IllegalStateException("Resources not opened");
        resources.submit(work);
    }

    public <R> R submit(Callable<R> work) {
        if (resources == null || !resources.isOpened()) throw new IllegalStateException("Resources not opened");
        return resources.submit(res-> { return work.call();});
    }

    public <Res extends Resources, R> R submit(Transactional.FunctionWithException<Res, R> work) {
        if (resources == null || !resources.isOpened()) throw new IllegalStateException("Resources not opened");
        return resources.submit(work);
    }

    public boolean isOpened() {
        if (resources == null) return false;
        return resources.isOpened();
    }

    public boolean isAborted() {
        if (resources == null) return false;
        return resources.isAborted();
    }
}
