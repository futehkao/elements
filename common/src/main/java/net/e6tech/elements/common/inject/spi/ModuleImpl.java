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
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by futeh.
 */
public class ModuleImpl implements Module {
    private ModuleFactory factory;
    private final Map<Type, BindingMap> directory = new ConcurrentHashMap<>();
    private final Set<Binding> singletons = Collections.synchronizedSet(new HashSet<>());

    public ModuleImpl(ModuleFactory factory) {
        this.factory = factory;
    }

    public Binding getBinding(Type boundClass, String name) {
        BindingMap bindingMap = directory.get(boundClass);
        if (bindingMap == null) {
            return null;
        }
        return bindingMap.get(name);
    }

    @Override
    public ModuleFactory getFactory() {
        return factory;
    }

    @Override
    public synchronized void add(Module module) {
        ModuleImpl moduleImpl = (ModuleImpl) module;
        ConcurrentHashMap<Type, BindingMap> dir;
        dir = new ConcurrentHashMap<>(moduleImpl.directory);

        // dir contains directory from module argument
        // we don't deal with singletons because the external module should've handle it.
        for (Map.Entry<Type, BindingMap> entry: dir.entrySet()) {
            BindingMap existing = directory.get(entry.getKey());
            if (existing != null) {
                existing.merge(entry.getValue());
            } else {
                directory.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void bindClass(Class cls, Class implementation) {
        Type[] types = getBindTypes(cls);
        for (Type type : types) {
            BindingMap bindList = directory.computeIfAbsent(type, t -> new BindingMap());
            bindList.bind(null, new Binding(implementation));
        }
    }

    @Override
    public Class getBoundClass(Class cls) {
        BindingMap bindList;
        bindList = directory.get(cls);
        return (bindList == null) ? null : bindList.get(null).getImplementation();
    }

    @Override
    public Object bindInstance(Class cls, Object inst) {
        return bindInstance(cls, inst, false);
    }

    @Override
    public Object rebindInstance(Class cls, Object inst) {
        return bindInstance(cls, inst, true);
    }

    private Object bindInstance(Class cls, Object inst, boolean rebind) {
        Object instance = newInstance(inst);
        Type[] types = getBindTypes(cls);
        Binding binding = new Binding(instance);
        for (Type type : types) {
            if (!directory.containsKey(type) || rebind) {
                BindingMap bindingMap = directory.computeIfAbsent(type, t -> new BindingMap());
                bindingMap.bind(null, binding);
            }
        }
        singletons.add(binding);
        bindProperties(cls, null, inst, rebind);
        return instance;
    }

    private void bindProperties(Class cls, String name, Object inst, boolean rebind) {
        for (String propName : getBindProperties(cls)) {
            PropertyDescriptor desc = Reflection.getPropertyDescriptor(cls, propName);
            Object propertyValue = getProperty(cls, propName, inst);
            if (propertyValue == null)
                continue;

            Class propType = desc.getPropertyType();
            Type[] propTypes = getBindTypes(propType);
            Binding binding = new Binding(propertyValue);
            for (Type type : propTypes) {
                if (!directory.containsKey(type)
                        || (directory.get(type).get(name) == null)
                        || rebind) {
                    BindingMap bindingMap = directory.computeIfAbsent(type, t -> new BindingMap());
                    bindingMap.bind(name, binding);
                }
            }
            singletons.add(binding);
        }
    }

    private Object getProperty(Class cls, String propName, Object inst) {
        PropertyDescriptor desc = Reflection.getPropertyDescriptor(cls, propName);
        Object propertyValue = null;
        if (desc != null && desc.getReadMethod() != null) {
            try {
                propertyValue = desc.getReadMethod().invoke(inst);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Logger.suppress(e);
            }
        }
        return propertyValue;
    }

    @Override
    public Object bindNamedInstance(Class cls, String name, Object inst) {
        return bindNamedInstance(cls, name, inst, false);
    }

    @Override
    public Object rebindNamedInstance(Class cls, String name, Object inst) {
        return bindNamedInstance(cls, name, inst, true);
    }

    private Object bindNamedInstance(Class cls, String name, Object inst, boolean rebind) {
        Object instance = newInstance(inst);
        Type[] types = getBindTypes(cls);
        Binding binding = new Binding(instance);
        synchronized (directory) {
            for (Type type : types) {
                BindingMap bindMap = directory.computeIfAbsent(type, t -> new BindingMap());
                if (bindMap.get(name) == null || rebind) {
                    bindMap.bind(name, binding);
                }
            }
            singletons.add(binding);
            bindProperties(cls, name, inst, rebind);
        }
        return instance;
    }

    public Object unbindInstance(Class cls) {
        return unbindNamedInstance(cls, null);
    }

    @SuppressWarnings({"squid:S135", "squid:S135"})
    public Object unbindNamedInstance(Class cls, String name) {
        Type[] types = getBindTypes(cls);
        Object ret = null;
        for (Type type : types) {
            BindingMap bindList = directory.get(type);
            if (bindList == null)
                continue;

            Object value = null;
            Binding binding = bindList.unbind(name);
            if (binding == null)
                continue;

            singletons.remove(binding);
            value = binding.getValue();
            if (bindList.size() == 0) {
                directory.remove(type);
            }
            if (value != null) {
                ret = value;
                unbindProperties(cls, name, value);
            }
        }
        return ret;
    }

    private void unbindProperties(Class cls, String name, Object inst) {
        for (String propName : getBindProperties(cls)) {
            PropertyDescriptor desc = Reflection.getPropertyDescriptor(cls, propName);
            Object propertyValue = getProperty(cls, propName, inst);
            if (propertyValue == null)
                continue;

            Type[] propTypes = getBindTypes(desc.getPropertyType());
            for (Type type : propTypes) {
                BindingMap bindingMap = directory.get(type);
                if (bindingMap == null)
                    continue;

                Binding binding = bindingMap.unbind(name);
                if (binding != null) {
                    singletons.remove(binding);
                }
                if (bindingMap.size() == 0)
                    directory.remove(type);
            }
        }
    }

    private Object newInstance(Object instance) {
        if (instance instanceof Class) {
            try {
                return ((Class) instance).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
        } else {
            return instance;
        }
    }

    public <T> T getBoundNamedInstance(Class<T> cls, String name) {
        BindingMap bindList = directory.get(cls);

        if (bindList == null)
            return null;
        Binding binding = bindList.get(name);
        if (binding == null)
            return null;
        return (T) binding.getValue();
    }

    public <T> T getBoundInstance(Class<T> cls) {
        return getBoundNamedInstance(cls, null);
    }

    public boolean hasInstance(Class cls) {
        return directory.containsKey(cls);
    }

    public boolean hasBinding(Class cls) {
        return directory.containsKey(cls);
    }

    @Override
    public Injector build(Module... components) {
        return build(true, components);
    }

    /**
     *
     * @param strict true mean all registered but un-injected instances need to have all of their dependencies resolved.  These type of instances are stored in
     *               singletons.
     *
     * @param components    additional module to add
     * @return
     */
    @Override
    public Injector build(boolean strict, Module... components) {
        Injector parent = null;
        if (components != null && components.length > 0) {
            Module[] remaining = new Module[components.length - 1];
            if (remaining.length > 0)
                System.arraycopy(components, 1, remaining, 0, components.length - 1);
            parent = components[0].build(remaining);
        }

        // Go through every singleton and inject it.  This is needed because
        // we allow binding of a singleton that has unresolved injection points.
        // The idea is that when creating an injector the singleton's dependencies should
        // be resolved via injection.
        InjectorImpl injector = new InjectorImpl(this, (InjectorImpl) parent);

        List<Binding> list = null;
        synchronized (singletons) {
            if (!singletons.isEmpty()) {
                list = new ArrayList<>(singletons);
                singletons.clear();
            }
        }

        if (list != null)
            for (Binding binding : list) {
                injector.inject(binding.getValue(), strict);
            }

        return injector;
    }

    private static class BindingMap {
        private static final String NULL_KEY = "";
        private Map<String, Binding> bindings = new ConcurrentHashMap<>();

        Binding get(String name) {
            return bindings.get((name == null) ? NULL_KEY : name);
        }

        void bind(String name, Binding binding) {
            bindings.put((name == null) ? NULL_KEY : name, binding);
        }

        Binding unbind(String name) {
            return bindings.remove((name == null) ? NULL_KEY : name);
        }

        int size() {
            return bindings.size();
        }

        void merge(BindingMap bindingMap) {
            Map<String, Binding> copy = new ConcurrentHashMap<>(bindingMap.bindings);
            for (Map.Entry<String, Binding> entry: copy.entrySet()) {
                if (!bindings.containsKey(entry.getKey())) {
                    bindings.put(entry.getKey(), copy.get(entry.getKey()));
                }
            }
        }
    }
}
