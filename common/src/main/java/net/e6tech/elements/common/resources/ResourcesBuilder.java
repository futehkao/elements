/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.resources;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResourcesBuilder<T extends Annotation> {
    private Provision provision;
    private Class<T> annotationClass;
    private List<Consumer<Resources>> consumers = new ArrayList<>();

    ResourcesBuilder(Provision provision, Class<T> annotationClass) {
        this.provision = provision;
        this.annotationClass = annotationClass;
    }

    public <K extends Annotation> ResourcesBuilder another(Class<K> anotherClass) {
        ResourcesBuilder newInstance = new ResourcesBuilder(provision, anotherClass);
        newInstance.consumers = this.consumers;
        return newInstance;
    }

    public UnitOfWork preOpen(Consumer<Resources> consumer) {
        if (consumers.isEmpty()) {
            return provision.preOpen(consumer);
        } else {
            return provision.preOpen(res -> {
                consumers.forEach(c -> c.accept(res));
                if (consumer != null)
                    consumer.accept(res);
            });
        }
    }

    public UnitOfWork open() {
        if (consumers.isEmpty()) {
            return provision.preOpen(null);
        } else {
            return provision.preOpen(res -> consumers.forEach(c -> c.accept(res)));
        }
    }

    public <U> ResourcesBuilder<T> annotate(Function<T, Callable<U>> func, U value) {
        consumers.add(res -> res.configurator().annotate(annotationClass, ((annotationValue, annotation) -> annotationValue.set(func.apply(annotation), value))));
        return this;
    }
}
