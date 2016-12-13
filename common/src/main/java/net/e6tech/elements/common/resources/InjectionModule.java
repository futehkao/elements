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

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import net.e6tech.elements.common.logging.Logger;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Created by futeh.
 */
public class InjectionModule extends AbstractModule {

    private static Logger logger = Logger.getLogger();

    Map<Type, Entry> bindInstances = new HashMap<>();
    Map<String, Entry> bindNamedInstances = new HashMap<>();
    Map<Type, Class> bindClasses = new HashMap<>();
    Map<Type, InjectionListener> listeners = new LinkedHashMap<>();

    private static Type[] getBindClass(Class cls) {
        Class c = cls;
        Class prev = cls;
        Class bindClass = cls;
        while (c != null && !c.equals(Object.class)) {
            BindClass bind = (BindClass) c.getAnnotation(BindClass.class);
            if (bind != null) {
                if (bind.generics()) {
                    return new Type[] {cls, prev.getGenericSuperclass()};
                } else {
                    bindClass = bind.value();
                }
                break;
            }
            prev = c;
            c = c.getSuperclass();
        }

        if (bindClass.getGenericSuperclass() instanceof ParameterizedType
                && bindClass.getTypeParameters().length == 0
                && bindClass.isAnonymousClass())
            return new Type[] {cls, bindClass.getGenericSuperclass()};
        else {
            if (bindClass.equals(cls)) return new Type[] {cls};
            return new Type[] {cls, bindClass};
        }
    }

    public Object bindInstance(Class cls, Object instance) {
        instance = newInstance(instance);
        Type[] types = getBindClass(cls);
        Entry entry = new Entry(types, instance);
        for (Type type : types) {
            bindInstances.put(type, entry);
        }

        return instance;
    }

    public Object unbindInstance(Class cls) {
        Type[] types = getBindClass(cls);
        Entry entry = null;
        for (Type type : types) {
            entry = bindInstances.remove(type);
        }
        if (entry != null) return entry.instance;
        return null;
    }

    public Object bindNamedInstance(String name, Class cls, Object instance) {
        instance = newInstance(instance);
        bindNamedInstances.put(name, new Entry(getBindClass(cls), instance));
        return instance;
    }

    private Object newInstance(Object instance) {
        if (instance instanceof Class) {
            try {
                instance = ((Class) instance).newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw logger.runtimeException(e);
            }
        }
        return instance;
    }

    public <T> T getBoundNamedInstance(String name) {
        Entry entry = bindNamedInstances.get(name);
        if (entry == null) return null;
        return (T) entry.instance;
    }

    public <T> T getBoundInstance(Class<T> cls) {
        Entry entry = bindInstances.get(cls);
        if (entry == null) return null;
        return (T) entry.instance;
    }

    public boolean hasInstance(Class cls) {
        return bindInstances.containsKey(cls);
    }
    public Object getInstance(Class cls) {
        Entry entry = bindInstances.get(cls);
        if (entry == null) return null;
        return entry.instance;
    }

    public void bindClass(Class cls, Class service) {
        Type[] types = getBindClass(cls);
        for (Type type : types) {
            bindClasses.put(type, service);
        }
    }

    public Class getBoundClass(Class cls) {
        return bindClasses.get(cls);
    }

    public boolean hasBinding(Class cls) {
        return bindInstances.containsKey(cls) || bindClasses.containsKey(cls);
    }

    void bindListener(Class cls, InjectionListener listener) {
        Type[] types = getBindClass(cls);
        for (Type type : types) {
            listeners.put(type, listener);
        }
    }

    boolean hasListener(Class cls) {
        return listeners.containsKey(cls);
    }

    public void add(InjectionModule module) {

        BiConsumer<Map, Map> copy = (from, to) -> {
            for (Object key: from.keySet()) {
                if (!to.containsKey(key)) {
                    to.put(key, from.get(key));
                }
            }
        };

        copy.accept(module.bindClasses, bindClasses);
        copy.accept(module.bindNamedInstances, bindNamedInstances);
        copy.accept(module.bindInstances, bindInstances);
        copy.accept(module.listeners, listeners);
    }

    @Override
    protected void configure() {

        binder().requireExplicitBindings();

        for (Type type : bindClasses.keySet()) {
            if (type instanceof Class) {
                bind((Class) type).to(bindClasses.get(type));
            } else {
                bind(TypeLiteral.get(type)).to(bindClasses.get(type));
            }
        }

        for (String name : bindNamedInstances.keySet()) {
            Entry entry = bindNamedInstances.get(name);
            for (Type type : entry.types) {
                if (type instanceof Class) {
                    bind((Class) type).annotatedWith(Names.named(name)).toInstance(entry.instance);
                } else {
                    bind((TypeLiteral<Object>) TypeLiteral.get(type)).annotatedWith(Names.named(name)).toInstance(entry.instance);
                }
            }
        }

        for (Type type : bindInstances.keySet()) {
            Entry entry = bindInstances.get(type);
            try {
                if (type instanceof Class) {
                    if (entry != null) {
                        if (entry.instance != null) bind((Class) type).toInstance(entry.instance);
                        else bind((Class) type).toProvider(() -> null);
                    } else {
                        bind((Class) type).toProvider(() -> null);
                    }
                } else {
                    if (entry != null) {
                        if (entry.instance != null) bind((TypeLiteral<Object>) TypeLiteral.get(type)).toInstance(entry.instance);
                        else  bind((TypeLiteral<Object>) TypeLiteral.get(type)).toProvider(() -> null);
                    } else {
                        bind((TypeLiteral<Object>) TypeLiteral.get(type)).toProvider(() -> null);
                    }
                }
            } catch (Throwable th) {
                logger.error("Cannot bind " + type + " to " + entry);
                throw th;
            }
        }

        for (Type key : listeners.keySet()) {
            InjectionListener listener = listeners.get(key);
            TypeListener lis = new TypeListener() {
                @Override
                public void hear(TypeLiteral type, TypeEncounter encounter) {
                    encounter.register(listener);
                }
            };
            bindListener(new AbstractMatcher<TypeLiteral>() {
                @Override
                public boolean matches(TypeLiteral typeLiteral) {
                    if (typeLiteral.getType().equals(key)) {
                        return true;
                    }
                    return false;
                }
            }, lis);
        }
    }

    private class Entry {
        Type[] types;
        Object instance;

        Entry (Type[] c, Object i) {
            types = c;
            instance = i;
        }
    }
}
