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
import net.e6tech.elements.common.inject.Named;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S134")
public class InjectorImpl implements Injector {

    private static Map<Class, WeakReference<List<InjectionPoint>>> injectionPoints = Collections.synchronizedMap(new WeakHashMap<>());

    private ModuleImpl module;
    private InjectorImpl parentInjector;

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
        return privateGetNamedInstance(boundClass, name).map(entry -> (T) entry.value).orElse(null);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S3776"})
    private Optional<Entry> privateGetNamedInstance(Type boundClass, String name) {
        Type type = boundClass;
        Binding binding = module.getBinding(type, name);
        if (binding == null && type instanceof ParameterizedType) {
            type = ((ParameterizedType) type).getRawType();
            binding = module.getBinding(type, name);
        }

        Entry entry = null;
        if (binding != null) {
            if (binding.isSingleton()) {
                entry = new Entry(binding.getValue());
            } else {
                try {
                    entry = new Entry(binding.getImplementation().newInstance());
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
            if (!binding.isSingleton()) {
                inject(entry.value);
            }
        } else if (parentInjector != null) {
            entry = parentInjector.privateGetNamedInstance(boundClass, name).orElse(null);
        }
        return Optional.ofNullable(entry);
    }

    public void inject(Object instance) {
        if (instance == null)
            return;
        Class instanceClass = instance.getClass();
        WeakReference<List<InjectionPoint>> ref = injectionPoints.get(instanceClass);

        List<InjectionPoint> points = (ref == null) ? null : ref.get();
        if (points == null) {
            points = injectionProperties(instanceClass);
            points.addAll(injectionFields(instanceClass));
            injectionPoints.put(instanceClass, new WeakReference<>(points));
        }
        points.forEach(pt ->{
            boolean injected = inject(pt, instance);
            if (!injected) {
                throw new SystemException("Cannot inject " + pt + "; no instances bound to " + pt.getType());
            }
        });
    }

    protected boolean inject(InjectionPoint point, Object instance) {
        InjectionAttempt attempt = point.inject(this, instance);
        if (attempt == InjectionAttempt.INJECTED)
            return true;

        // if parent exist delegate to it
        if (parentInjector != null)
            return parentInjector.inject(point, instance);
        else {
            return attempt != InjectionAttempt.ERROR;
        }
    }

    private List<InjectionPoint> injectionFields(Class instanceClass) {
        Class cls = instanceClass;
        List<InjectionPoint> list = new ArrayList<>();
        while (cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                InjectionPoint injectionPoint = injectionPoint(field, () -> new InjectionField(field)).orElse(null);
                if (injectionPoint != null) {
                    list.add(injectionPoint);
                }
            }
            cls = cls.getSuperclass();
        }
        return list;
    }

    private List<InjectionPoint> injectionProperties(Class instanceClass) {
        List<InjectionPoint> list = new ArrayList<>();
        BeanInfo beanInfo = Reflection.getBeanInfo(instanceClass);
        for (PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
            InjectionPoint injectionPoint = injectionPoint(prop.getWriteMethod(), () -> new InjectionMethod(prop.getWriteMethod()))
                    .orElseGet(() -> injectionPoint(prop.getReadMethod(), () -> new InjectionMethod(prop.getWriteMethod())).orElse(null));

            if (injectionPoint != null) {
                list.add(injectionPoint);
            }
        }
        return list;
    }

    private Optional<InjectionPoint> injectionPoint(AccessibleObject accessibleObject, Supplier<InjectionPoint> supplier) {
        if (accessibleObject == null)
            return Optional.empty();
        Inject inject = accessibleObject.getDeclaredAnnotation(Inject.class);
        InjectionPoint injectionPoint = null;
        if (inject != null) {
            injectionPoint = supplier.get();
            injectionPoint.optional = inject.optional();
            injectionPoint.type = inject.type();
            injectionPoint.property = inject.property();
        } else {
            javax.inject.Inject jInject = accessibleObject.getDeclaredAnnotation(javax.inject.Inject.class);
            if (jInject != null) {
                injectionPoint = supplier.get();
                injectionPoint.optional = false;
            }
        }

        if (injectionPoint != null) {
            Named named = accessibleObject.getDeclaredAnnotation(Named.class);
            if (named != null){
                injectionPoint.name = named.value();
            } else {
                javax.inject.Named jNamed = accessibleObject.getDeclaredAnnotation(javax.inject.Named.class);
                if (jNamed != null) {
                    injectionPoint.name = jNamed.value();
                }
            }
        }

        return Optional.ofNullable(injectionPoint);
    }

    //
    private static class BoundInstance {
        Map<String, Entry> namedInstances = new HashMap<>();
        Entry unnamedInstance;

        Entry get(String name) {
            if (name == null)
                return unnamedInstance;
            return namedInstances.get(name);
        }

        Entry put(String name, Object instance) {
            Entry entry = new Entry(instance);
            if (name == null)
                unnamedInstance = entry;
            else namedInstances.put(name, entry);
            return entry;
        }
    }

    private static class Entry {
        Object value;

        Entry(Object value) {
            this.value = value;
        }
    }

    private  enum InjectionAttempt {
        ERROR,
        INJECTED,
        NOT_INJECTED;
    }

    private abstract static class InjectionPoint {
        protected String name;
        protected boolean optional;
        protected Class type = void.class;
        protected String property = "";

        abstract InjectionAttempt inject(InjectorImpl injector, Object target);

        abstract Type getType();
    }

    private static class InjectionField extends InjectionPoint {
        private Field field;

        InjectionField(Field field) {
            this.field = field;
            if (!Modifier.isPublic(field.getModifiers()))
                field.setAccessible(true);
        }

        InjectionAttempt inject(InjectorImpl injector, Object target) {
            Type t = (type != void.class) ? type : field.getGenericType();
            Optional<Entry> opt = injector.privateGetNamedInstance(t, name);

            if (!opt.isPresent() && !optional) {
                return InjectionAttempt.ERROR;
            }

            if (opt.isPresent()) {
                try {
                    Object value = opt.get().value;
                    if (property.length() > 0 && value != null) {
                        value = Reflection.getProperty(value, property);
                    }
                    field.set(target, value);
                } catch (IllegalAccessException e) {
                    throw new SystemException(e);
                }
                return InjectionAttempt.INJECTED;
            } else {
                return InjectionAttempt.NOT_INJECTED;
            }
        }

        Type getType() {
            return (type != void.class && type != Void.class) ? type : field.getGenericType();
        }

        public String toString() {
            return field.toString();
        }
    }

    private static class InjectionMethod extends InjectionPoint {
        private Method setter;

        InjectionMethod(Method setter) {
            this.setter = setter;
        }

        InjectionAttempt inject(InjectorImpl injector, Object target) {
            Type t = getType();
            Optional<Entry> opt = injector.privateGetNamedInstance(t, name);

            if (!opt.isPresent() && !optional) {
                return InjectionAttempt.ERROR;
            }

            if (opt.isPresent()) {
                try {
                    Object value = opt.get().value;
                    if (property.length() > 0 && value != null) {
                        value = Reflection.getProperty(value, property);
                    }
                    setter.invoke(target, value);
                } catch (IllegalAccessException e) {
                    throw new SystemException(e);
                }catch (InvocationTargetException e) {
                    throw new SystemException(e.getTargetException());
                }
                return InjectionAttempt.INJECTED;
            } else {
                return InjectionAttempt.NOT_INJECTED;
            }
        }

        Type getType() {
            return (type != void.class && type != Void.class) ? type : setter.getGenericParameterTypes()[0];
        }

        public String toString() {
            return setter.toString();
        }
    }
}
