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

import net.e6tech.elements.common.reflection.Annotator;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class Configurator {
    Map configuration = new HashMap();

    public static <T extends Annotation> Configurator create(Class<T> cls, BiConsumer<Annotator.AnnotationValue, T> consumer) {
        Configurator configurator = new Configurator();
        return configurator.annotate(cls, consumer);
    }

    public <T extends Annotation> Configurator annotate(Class<T> cls, BiConsumer<Annotator.AnnotationValue, T> consumer) {
        T existing = (T) configuration.get(cls);
        configuration.put(cls, Annotator.create(cls, existing, consumer));
        return this;
    }

    public <T extends Annotation, R> R annotatedValue(Class<T> key, Function<T, R> function) {
        T t = (T) configuration.get(key);
        if (t == null)
            t = Annotator.create((Class<Annotation>)key, null);
        return function.apply(t);
    }

    public <T extends Annotation> Optional<T> annotation(Class<T> key) {
        T t = (T) configuration.get(key);
        return Optional.ofNullable(t);
    }

    public <T> T get(String key) {
        return (T) configuration.get(key);
    }

    public <T> T get(String key, T defval) {
        T t = (T) configuration.get(key);
        return (t == null) ? defval : t;
    }

    public <T> T get(Class<T> key) {
        return (T) configuration.get(key);
    }

    public <T> T get(Class<T> key, T defval) {
        T t = (T) configuration.get(key);
        return (t == null) ? defval : t;
    }

    public <T> Map<String, T> map(Class<T> key) {
        return (Map<String, T>) configuration.get(key);
    }

    public <T> List<T> list(Class<T> key) {
        return (List<T>) configuration.get(key);
    }

    public <T> T computeIfAbsent(String key, Function<String, T> mappingFunction) {
        return (T) configuration.computeIfAbsent(key, mappingFunction);
    }

    public <T> T computeIfAbsent(Class<T> key, Function<Class<T>, T> mappingFunction) {
        return (T) configuration.computeIfAbsent(key, mappingFunction);
    }

    public <T> Map<String, T> computeMapIfAbsent(Class<T> key) {
        return (Map<String, T>) configuration.computeIfAbsent(key, k -> new LinkedHashMap<>());
    }

    public <T> List<T> computeListIfAbsent(Class<T> key) {
        return (List<T>) configuration.computeIfAbsent(key, k -> new LinkedList<>());
    }

    public <T> Configurator put(Class<T> cls, T instance) {
        configuration.put(cls, instance);
        return this;
    }

    public Configurator put(String key, Object value) {
        configuration.put(key, value);
        return this;
    }

    public Configurator putAll(Configurator configurator) {
        if (configurator != null)
            configuration.putAll(configurator.configuration);
        return this;
    }

    public Configurator putAll(Map map) {
        if (map != null)
            configuration.putAll(map);
        return this;
    }

    public Configurator clear() {
        configuration.clear();
        return this;
    }
}
