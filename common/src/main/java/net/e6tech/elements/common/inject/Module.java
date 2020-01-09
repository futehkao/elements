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

package net.e6tech.elements.common.inject;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.TypeToken;
import net.e6tech.elements.common.resources.BindClass;
import net.e6tech.elements.common.resources.BindProperties;
import net.e6tech.elements.common.resources.Provision;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S1214")
public interface Module {

    LoadingCache<Class<?>, String[]> bindProperties = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .initialCapacity(100)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Class<?>, String[]>() {
        public String[] load(Class<?> cls)  {
            Objects.requireNonNull(cls);
            Class<?> c = cls;
            Set<String> set = new LinkedHashSet<>();
            while (c != null && !c.equals(Object.class)) {
                BindProperties annotation = c.getAnnotation(BindProperties.class);
                if (annotation != null) {
                    Collections.addAll(set, annotation.value());
                }
                c = c.getSuperclass();
            }
            return set.toArray(new String[set.size()]);
        }
    });

    @SuppressWarnings({"unchecked","squid:CommentedOutCodeLine"})
    LoadingCache<Class<?>, Type[]> bindTypes = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .initialCapacity(100)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Class<?>, Type[]>() {
        public Type[] load(Class<?> cls)  {
            Objects.requireNonNull(cls);
            Class<?> c = cls;
            Class prev = cls;
            Class bindClass = cls;
            Set<Type> list = new LinkedHashSet<>();
            list.add(cls);
            boolean found = false;
            // try to find BindClass annotation from class hierarchy
            while (c != null && !c.equals(Object.class)) {
                BindClass bind = c.getAnnotation(BindClass.class);
                if (bind != null) {
                    if (bind.generics()) {
                        found = true;
                        list.add(prev.getGenericSuperclass());
                    } else {
                        if (bind.value().equals(void.class))
                            bindClass = c;
                        else
                            bindClass = bind.value();
                    }
                    break;
                }
                prev = c;
                c = c.getSuperclass();
            }

            if (!found) {
                if (bindClass.getGenericSuperclass() instanceof ParameterizedType
                        && bindClass.getTypeParameters().length == 0) {
                    // this is for anonymous class
                    // for example, new CacheFacade<String, SecretKey>(KeyServer, "clientKeys") {}
                    list.add(bindClass.getGenericSuperclass());
                } else {
                    if (!bindClass.equals(cls))
                        list.add(bindClass);
                }
            }

            // try to find using interfaces
            for (Class i : TypeToken.of(cls).getTypes().interfaces().rawTypes()) {
                BindClass bind = (BindClass) i.getAnnotation(BindClass.class);
                if (bind != null) {
                    if (bind.value().equals(void.class))
                        list.add(i);
                    else
                        list.add(bind.value());
                }
            }

            list.removeIf(type -> type instanceof Class && ((Class) type).isSynthetic());
            return list.toArray(new Type[list.size()]);
        }
    });

    default String[] getBindProperties(Class cls) {
        try {
            return bindProperties.get(cls);
        } catch (ExecutionException e) {
            return new String[0];
        }
    }

    default Type[] getBindTypes(Class<?> cls) {
        try {
            return bindTypes.get(cls);
        } catch (ExecutionException e) {
            return new Type[0];
        }
    }

    ModuleFactory getFactory();

    void add(Module module);

    Map<String, Object> listBindings(Class cls);

    Map<Type, Map<String, Object>> listBindings();

    void bindClass(Class cls, Class service);

    Class getBoundClass(Class cls);

    Object bindInstance(Class cls, Object instance);

    Object rebindInstance(Class cls, Object instance);

    Object unbindInstance(Class cls);

    Object unbindNamedInstance(Class cls, String name);

    <T> T getBoundInstance(Class<T> cls);

    Object bindNamedInstance(Class cls, String name, Object instance);

    Object rebindNamedInstance(Class cls, String name, Object instance);

    <T> T getBoundNamedInstance(Class<T> cls, String name);

    Injector build(Module... components);

    Injector build(boolean strict, Module... components);
}
