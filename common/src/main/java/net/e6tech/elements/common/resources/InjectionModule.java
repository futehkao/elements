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

import com.google.inject.*;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import net.e6tech.elements.common.logging.Logger;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Created by futeh.
 */
public class InjectionModule extends AbstractModule implements Cloneable {

    private static Logger logger = Logger.getLogger();

    private Map<Type, Entry> bindInstances = new HashMap<>();
    private Map<String, Entry> bindNamedInstances = new HashMap<>();
    private Map<Type, Class> bindClasses = new HashMap<>();
    private Map<Type, InjectionListener> listeners = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public InjectionModule clone() {
        try {
            InjectionModule clone = (InjectionModule) super.clone();
            clone.bindInstances = (Map) ((HashMap) bindInstances).clone();
            clone.bindNamedInstances = (Map) ((HashMap) bindNamedInstances).clone();
            clone.bindClasses = (Map) ((HashMap) bindClasses).clone();
            clone.listeners = (Map) ((LinkedHashMap) listeners).clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

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

    public synchronized Object bindInstance(Class cls, Object instance) {
        instance = newInstance(instance);
        Type[] types = getBindClass(cls);
        Entry entry = new Entry(types, instance);
        for (Type type : types) {
            bindInstances.put(type, entry);
        }

        return instance;
    }

    public synchronized Object unbindInstance(Class cls) {
        Type[] types = getBindClass(cls);
        Entry entry = null;
        for (Type type : types) {
            entry = bindInstances.remove(type);
        }
        if (entry != null) return entry.instance;
        return null;
    }

    public synchronized Object bindNamedInstance(String name, Class cls, Object instance) {
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

    public synchronized <T> T getBoundNamedInstance(String name) {
        Entry entry = bindNamedInstances.get(name);
        if (entry == null) return null;
        return (T) entry.instance;
    }

    public synchronized <T> T getBoundInstance(Class<T> cls) {
        Entry entry = bindInstances.get(cls);
        if (entry == null) return null;
        return (T) entry.instance;
    }

    public synchronized boolean hasInstance(Class cls) {
        return bindInstances.containsKey(cls);
    }

    public synchronized Object getInstance(Class cls) {
        Entry entry = bindInstances.get(cls);
        if (entry == null) return null;
        return entry.instance;
    }

    public synchronized void bindClass(Class cls, Class service) {
        Type[] types = getBindClass(cls);
        for (Type type : types) {
            bindClasses.put(type, service);
        }
    }

    public synchronized Class getBoundClass(Class cls) {
        return bindClasses.get(cls);
    }

    public synchronized boolean hasBinding(Class cls) {
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

    public synchronized void add(InjectionModule module) {
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

    public Injector createInjector(InjectionModule ... additional) {
        List<InjectionModule> moduleList = new ArrayList<>();
        if (additional != null) {
            for (InjectionModule m : additional) moduleList.add(m);
        }
        return createInjector(moduleList);
    }

    public Injector createInjector(Iterable<? extends InjectionModule> additional) {
        // need to remove duplicate
        List<InjectionModule> moduleList = new ArrayList<>();
        moduleList.add(this);
        additional.forEach(m -> moduleList.add(m));
        Collections.reverse(moduleList);

        Map<Type, Entry> bindInstances = new HashMap<>();
        Map<String, Entry> bindNamedInstances = new HashMap<>();
        Map<Type, Class> bindClasses = new HashMap<>();

        for (InjectionModule m : moduleList) {
            bindInstances.putAll(m.bindInstances);
            bindNamedInstances.putAll(m.bindNamedInstances);
            bindClasses.putAll(m.bindClasses);
        }

        InjectionModule resultingModule = new InjectionModule();
        resultingModule.bindInstances = bindInstances;
        resultingModule.bindNamedInstances = bindNamedInstances;
        resultingModule.bindClasses = bindClasses;
        resultingModule.listeners = this.listeners;

        Injector injector = Guice.createInjector(resultingModule);
        inject(injector);
        return injector;
    }

    private void inject(Injector injector) {
        for (String name : this.bindNamedInstances.keySet()) {
            Entry entry = bindNamedInstances.get(name);
            if (entry.instance != null) injector.injectMembers(entry.instance);
        }

        for (Type type : this.bindInstances.keySet()) {
            Entry entry = bindInstances.get(type);
            if (entry != null && entry.instance != null) injector.injectMembers(entry.instance);
        }
    }

    /**
     * We replaced toInstance with toProvider because for singletons Guice uses a global lock during configure.
     * The instance would have SingletonScope and during injection in configure a global lock is acquired.
     * This kills performance if we have to create a lot of injectors.
     */
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
                    bind((Class) type).annotatedWith(Names.named(name)).toProvider(newInstanceProvider(entry.instance));
                    // bind((Class) type).annotatedWith(Names.named(name)).toInstance(entry.instance);
                } else {
                    bind((TypeLiteral<Object>) TypeLiteral.get(type)).annotatedWith(Names.named(name)).toProvider(newInstanceProvider(entry.instance));
                    // bind((TypeLiteral<Object>) TypeLiteral.get(type)).annotatedWith(Names.named(name)).toInstance(entry.instance);
                }
            }
        }

        for (Type type : bindInstances.keySet()) {
            Entry entry = bindInstances.get(type);
            try {
                if (type instanceof Class) {
                    if (entry != null) {
                        if (entry.instance != null) {
                            bind((Class) type).toProvider(newInstanceProvider(entry.instance));
                            // bind((Class) type).toInstance(entry.instance);
                        }
                        else bind((Class) type).toProvider(() -> null);
                    } else {
                        bind((Class) type).toProvider(() -> null);
                    }
                } else {
                    if (entry != null) {
                        if (entry.instance != null) {
                            bind((TypeLiteral<Object>) TypeLiteral.get(type)).toProvider(newInstanceProvider(entry.instance));
                            // bind((TypeLiteral<Object>) TypeLiteral.get(type)).toInstance(entry.instance);
                        }
                        else  bind((TypeLiteral<Object>) TypeLiteral.get(type)).toProvider(newInstanceProvider(null));
                    } else {
                        bind((TypeLiteral<Object>) TypeLiteral.get(type)).toProvider(newInstanceProvider(null));
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

    private Provider newInstanceProvider(Object instance) {
        InstanceProvider provider = new InstanceProvider();
        provider.instance = instance;
        return provider;
    }

    static class InstanceProvider implements Provider {
        Object instance;
        @Override
        public Object get() {
            return instance;
        }
    }
}
