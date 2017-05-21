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


import net.e6tech.elements.common.reflection.Annotator;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by futeh on 12/19/15.
 */
public class ResourcesFactory {

    @Inject
    protected Provision provision;

    private ResourceProviderConfigurator configurator = new ResourceProviderConfigurator();

    List<Consumer<Resources>> configurations = new LinkedList<>();

    public ResourceProviderConfigurator configurator() {
        return configurator;
    }

    // for programmatic configuration
    public <T extends Annotation> ResourcesFactory configure(Class<T> cls, BiConsumer<Annotator.AnnotationValue, T> consumer) {
        configurator.configure(cls, consumer);
        return this;
    }

    // for programmatic configuration
    public <T extends Annotation> ResourcesFactory configure(String key, Object value) {
        configurator.configure(key, value);
        return this;
    }

    // for groovy configuration
    public ResourcesFactory add(Consumer<Resources> consumer) {
        configurations.add(consumer);
        return this;
    }

    public UnitOfWork open() {
        UnitOfWork uow = provision.preOpen((resources) -> {
            if (configurations != null) {
                for (Consumer<Resources> c : configurations) c.accept(resources);
            }
        });
        uow.open(configurator.configuration());
        return uow;
    }

    public Provision getProvision() {
        return provision;
    }

    @Merge
    public ResourcesFactory merge(ResourcesFactory instance) {
        configurations.addAll(instance.configurations);
        return this;
    }
}
