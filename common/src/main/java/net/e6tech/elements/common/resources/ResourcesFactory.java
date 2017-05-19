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


import javax.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by futeh on 12/19/15.
 */
public class ResourcesFactory {

    @Inject
    Provision provision;

    List<Consumer<Resources>> configurations = new LinkedList<>();

    public void addConfiguration(String configName, Object object) {
        add((resources) -> {
            resources.addConfiguration(configName, object); // add 15 sec to tx timeout
        });
    }

    /*
    public <Res extends Resources, T> T commit(Commitable<Res, T> consumer) {
        if (configurations == null) {
            provision.commit(Resources.class, resources -> {
                return consumer.call((Res) resources);
            });
        }
        return provision.preOpen(config()).commit(Resources.class, resouces -> {
            return consumer.call((Res) resouces);
        });
    }*/

    public UnitOfWork open() {
        return provision.preOpen((resources) -> {
            if (configurations != null) {
                for (Consumer<Resources> c : configurations) c.accept(resources);
            }
        });
    }

    private Consumer<Resources> config() {
        return (resources) -> {
            for (Consumer<Resources> c : configurations) c.accept(resources);
        };
    }

    public void add(Consumer<Resources> consumer) {
        configurations.add(consumer);
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
