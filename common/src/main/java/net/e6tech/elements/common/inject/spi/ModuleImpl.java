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

package net.e6tech.elements.common.inject.spi;

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.inject.ModuleFactory;
import scala.collection.immutable.IntMap;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Created by futeh.
 */
public class ModuleImpl implements Module {

    private ModuleFactory factory;
    private Map<Type, BindingList> directory = new HashMap<>();

    public ModuleImpl(ModuleFactory factory) {
        this.factory = factory;
    }

    public Binding getBinding(Type boundClass, String name) {
        BindingList bindingList = directory.get(boundClass);
        if (bindingList == null) {
            return null;
        }
        Binding binding = bindingList.getBinding(name);
        binding = (binding == null) ? null : binding.clone();
        return binding;
    }

    @Override
    public ModuleFactory getFactory() {
        return factory;
    }

    @Override
    public synchronized void add(Module module) {
        ModuleImpl moduleImpl = (ModuleImpl) module;
        Map<Type, BindingList> dir = new HashMap<>();
        synchronized (moduleImpl.directory) {
            dir.putAll(moduleImpl.directory);
        }
        for (Type type: dir.keySet()) {
            BindingList bindingList = dir.get(type);
            BindingList existing = directory.get(type);
            if (existing != null) {
                existing.merge(bindingList);
            } else {
                directory.put(type, bindingList);
            }
        }
    }

    @Override
    public void bindClass(Class cls, Class implementation) {
        Type[] types = getBindClass(cls);
        synchronized (directory) {
            for (Type type : types) {
                BindingList bindList = directory.computeIfAbsent(type, t -> new BindingList());
                bindList.bindClass(implementation);
            }
        }
    }

    @Override
    public Class getBoundClass(Class cls) {
        BindingList bindList = null;
        synchronized (directory) {
            bindList = directory.get(cls);
        }
        if (bindList == null || bindList.unnamedBinding == null) return null;
        return bindList.unnamedBinding.getImplementation();
    }

    @Override
    public Object bindInstance(Class cls, Object instance) {
        instance = newInstance(instance);
        Type[] types = getBindClass(cls);
        synchronized (directory) {
            for (Type type : types) {
                BindingList bindList = directory.computeIfAbsent(type, t -> new BindingList());
                bindList.bindInstance(null, instance);
            }
        }
        return instance;
    }

    @Override
    public Object bindNamedInstance(Class cls, String name, Object instance) {
        instance = newInstance(instance);
        Type[] types = getBindClass(cls);
        synchronized (directory) {
            for (Type type : types) {
                BindingList bindList = directory.computeIfAbsent(type, t -> new BindingList());
                bindList.bindInstance(name, instance);
            }
        }
        return instance;
    }

    public Object unbindInstance(Class cls) {
        Type[] types = getBindClass(cls);
        synchronized (directory) {
            for (Type type : types) {
                BindingList bindList = directory.get(type);
                if (bindList != null) {
                    Object value = bindList.unbind();
                    if (bindList.namedBindings.size() == 0) directory.remove(type);
                    return value;
                }
            }
        }

        return null;
    }

    private Object newInstance(Object instance) {
        if (instance instanceof Class) {
            try {
                instance = ((Class) instance).newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    public <T> T getBoundNamedInstance(Class<T> cls, String name) {
        BindingList bindList = null;
        synchronized (directory) {
            bindList = directory.get(cls);
        }
        if (bindList == null) return null;
        Binding binding = bindList.getBinding(name);
        if (binding == null) return null;
        return (T) binding.getValue();
    }

    public <T> T getBoundInstance(Class<T> cls) {
        return getBoundNamedInstance(cls, null);
    }

    public boolean hasInstance(Class cls) {
        synchronized (directory) {
            return directory.containsKey(cls);
        }
    }

    public boolean hasBinding(Class cls) {
        synchronized (directory) {
            return directory.containsKey(cls);
        }
    }

    @Override
    public Injector build(Module... components) {
        Injector parent = null;
        if (components != null && components.length > 0) {
            Module[] remaining = new Module[components.length - 1];
            if (remaining.length > 0) System.arraycopy(components, 1, remaining, 0, components.length - 1);
            parent = components[0].build(remaining);
        }

        // go through every singleton and inject them
        InjectorImpl injector = new InjectorImpl(this, (InjectorImpl) parent);
        Map<Type, BindingList> dir = new HashMap<>();
        synchronized (directory) {
            dir.putAll(directory);
        }
        for (Map.Entry<Type, BindingList> entry : dir.entrySet()) {
            entry.getValue().list().forEach(binding -> {
                if (binding.isSingleton()) {
                    injector.inject(binding.getValue());
                }
            });
        }
        return injector;
    }

    private static class BindingList implements Cloneable {
        private Map<String, Binding> namedBindings = new HashMap<>();
        private Binding unnamedBinding;

        Binding getBinding(String name) {
            if (name == null) return unnamedBinding;
            return namedBindings.get(name);
        }

        List<Binding> list() {
            List<Binding> list = new ArrayList<>();
            if (unnamedBinding != null) list.add(unnamedBinding);
            list.addAll(namedBindings.values());
            return list;
        }

        void bindClass(Class implementation) {
            Binding binding = new Binding(implementation);
            binding.setSingleton(false);
            unnamedBinding = binding;
        }

        void bindInstance(String name, Object instance) {
            Binding binding = new Binding(instance);
            binding.setSingleton(true);
            if (name == null) {
                unnamedBinding = binding;
            }  else {
                namedBindings.put(name, binding);
            }
        }

        Object unbind() {
            Object value = unnamedBinding.getValue();
            unnamedBinding = null;
            return value;
        }

        int namedBindingSize() {
            return namedBindings.size();
        }

        void merge(BindingList bindingList) {
            if (unnamedBinding == null) unnamedBinding = bindingList.unnamedBinding;

            Map<String, Binding> copy = new HashMap<>();
            synchronized (bindingList.namedBindings) {
                copy.putAll(bindingList.namedBindings);
            }
            for (String name : copy.keySet()) {
                if (!namedBindings.containsKey(name)) {
                    namedBindings.put(name, bindingList.namedBindings.get(name));
                }
            }
        }
    }
}
