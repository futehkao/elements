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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.inject.Injector;

import javax.inject.Named;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Created by futeh.
 */
public class InjectorImpl implements Injector {

    private static Map<Class, List<InjectionPoint>> injectionPoints = Collections.synchronizedMap(new WeakHashMap<>());

    private ModuleImpl module;
    private InjectorImpl parentInjector;
    private Map<Type, BoundInstances> instances = new HashMap<>();

    public InjectorImpl(ModuleImpl module) {
        this.module = module;
    }

    public InjectorImpl(ModuleImpl module, InjectorImpl parentInjector) {
        this.module = module;
        this.parentInjector = parentInjector;
    }

    @Override
    public <T> T getInstance(Class<T> cls) {
        return getNamedInstance(cls, null);
    }

    @Override
    public <T> T getNamedInstance(Class<T> boundClass, String name) {
        return _getNamedInstance(boundClass, name).<T>map(entry -> (T) entry.value).orElse(null);
    }

    private Optional<Entry> _getNamedInstance(Type boundClass, String name) {
        BoundInstances boundInstances = instances.get(boundClass);
        Entry entry = null;

        if (boundInstances == null && boundClass instanceof ParameterizedType) {
            boundInstances = instances.get(((ParameterizedType) boundClass).getRawType());
        }

        if (boundInstances != null) {
            entry = boundInstances.getInstance(name);
        }

        // need to get from module
        if (entry == null) {
            Type type = boundClass;
            Binding binding = module.getBinding(type, name);
            if (binding == null && type instanceof ParameterizedType) {
                type = ((ParameterizedType) type).getRawType();
                binding = module.getBinding(type, name);
            }

            if (binding != null) {
                if (boundInstances == null) {
                    boundInstances = new BoundInstances();
                    instances.put(type, boundInstances);
                }

                Object instance = null;
                if (binding.isSingleton()) {
                    instance = binding.getValue();
                } else {
                    try {
                        instance = binding.getImplementation().newInstance();
                        // to be injected later in code.
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                entry = boundInstances.put(name, instance);

                // only inject for non-singleton, this needs to be call after boundInstances has been
                // updated to avoid infinite injection cycle.
                if (!binding.isSingleton()) {
                    inject(instance);
                }
            } else if (parentInjector != null) {
                entry = parentInjector._getNamedInstance(boundClass, name).orElse(null);
            }
        }

        return Optional.ofNullable(entry);
    }

    public void inject(Object instance) {
        if (instance == null) return;
        List<InjectionPoint> points = injectionPoints.computeIfAbsent(instance.getClass(),
                instanceClass -> parseInjectionPoints(instanceClass));
        points.forEach(pt ->{
            boolean injected = inject(pt, instance);
            if (!injected) {
                throw new RuntimeException("Cannot inject " + pt.field + "; no instances bound to " + pt.field.getType());
            }
        });
    }

    protected boolean inject(InjectionPoint point, Object instance) {
        boolean injected = false;
        boolean myAttempt = point.inject(this, instance);
        if (myAttempt) return true;
        if (parentInjector != null) return parentInjector.inject(point, instance);
        return false;
    }

    List<InjectionPoint> parseInjectionPoints(Class instanceClass) {
        Class cls = instanceClass;
        List<InjectionPoint> list = new ArrayList<>();
        while (cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                InjectionPoint injectionPoint = null;
                String name = null;
                boolean optional = false;
                Inject inject = field.getDeclaredAnnotation(Inject.class);

                if (inject != null) {
                    injectionPoint = new InjectionPoint();
                    optional = inject.optional();
                } else {
                    com.google.inject.Inject gInject = field.getDeclaredAnnotation(com.google.inject.Inject.class);
                    if (gInject != null) {
                        injectionPoint = new InjectionPoint();
                        optional = gInject.optional();
                    } else {
                        javax.inject.Inject jInject = field.getDeclaredAnnotation(javax.inject.Inject.class);
                        if (jInject != null) injectionPoint = new InjectionPoint();
                    }
                }

                if (injectionPoint != null) {
                    Named named = field.getDeclaredAnnotation(Named.class);
                    if (named != null){
                        name = named.value();
                    } else {
                        com.google.inject.name.Named gNamed = field.getDeclaredAnnotation(com.google.inject.name.Named.class);
                        if (gNamed != null) {
                            name = gNamed.value();
                        }
                    }
                }

                if (injectionPoint != null) {
                    injectionPoint.field = field;
                    field.setAccessible(true);
                    injectionPoint.optional = optional;
                    injectionPoint.name = name;
                    list.add(injectionPoint);
                }

            }
            cls = cls.getSuperclass();
        }
        return list;
    }

    private static class BoundInstances {
        Map<String, Entry> namedInstances = new HashMap<>();
        Entry unnamedInstance;

        Entry getInstance(String name) {
            if (name == null) return unnamedInstance;
            return namedInstances.get(name);
        }

        Entry put(String name, Object instance) {
            Entry entry = new Entry(instance);
            if (name == null) unnamedInstance = entry;
            else namedInstances.put(name, entry);
            return entry;
        }
    }

    private static class Entry {
        Object value;

        Entry(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }
    }

    private static class InjectionPoint {
        private Field field;
        private String name;
        private boolean optional;

        public boolean inject(InjectorImpl injector, Object target) {
            Optional<Entry> opt = injector._getNamedInstance(field.getGenericType(), name);

            if (!opt.isPresent() && !optional) {
                return false;
            }

            opt.ifPresent(entry -> {
                try {
                    field.set(target, entry.value());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
            return true;
        }
    }
}
