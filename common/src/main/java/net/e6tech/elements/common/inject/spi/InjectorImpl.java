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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Named;
import net.e6tech.elements.common.reflection.Lambda;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S134")
public class InjectorImpl implements Injector {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    private static LoadingCache<Class<?>, List<InjectionPoint>> injectionPoints = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .initialCapacity(200)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .expireAfterWrite(360 * 60 * 1000L, TimeUnit.MILLISECONDS)
            .build(new CacheLoader<Class<?>, List<InjectionPoint>>() {
        public List<InjectionPoint> load(Class<?> instanceClass)  {
            List<InjectionPoint> points = injectionProperties(instanceClass);
            points.addAll(injectionFields(instanceClass));
            return points;
        }
    });

    private ModuleImpl module;
    private InjectorImpl parentInjector;

    InjectorImpl(ModuleImpl module, InjectorImpl parentInjector) {
        this.module = module;
        this.parentInjector = parentInjector;
    }

    @Override
    public <T> T getInstance(Class<T> cls) {
        return getNamedInstance(cls, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getNamedInstance(Class<T> boundClass, String name) {
        return privateGetNamedInstance(boundClass, name).map(binding -> (T) binding.getValue()).orElse(null);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S3776"})
    private Optional<Binding> privateGetNamedInstance(Type boundClass, String name) {
        Type type = boundClass;
        Binding binding = module.getBinding(type, name);

        if (binding == null) {
            if (type instanceof ParameterizedType) {
                type = ((ParameterizedType) type).getRawType();
                binding = module.getBinding(type, name);
            } else if (type instanceof TypeVariable) {
                TypeVariable typeVariable = (TypeVariable) type;
                Type[] bounds = typeVariable.getBounds();
                for (Type bound : bounds) {
                    binding = module.getBinding(bound, name);
                    if (binding != null)
                        break;
                }
            }
        }

        if (binding != null) {
            binding = binding.getInstance(this);
        } else if (parentInjector != null) {
            binding = parentInjector.privateGetNamedInstance(boundClass, name).orElse(null);
        }
        return Optional.ofNullable(binding);
    }

    @Override
    public void inject(Object instance, boolean strict) {
        if (instance == null)
            return;
        Class instanceClass = instance.getClass();
        List<InjectionPoint> points;
        try {
            points = injectionPoints.get(instanceClass);
            points.forEach(pt ->{
                boolean injected = inject(pt, instance);
                if (!injected && strict) {
                    throw new SystemException("Cannot inject " + pt + "; no instances bound to " + pt.getType());
                }
            });
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
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

    @SuppressWarnings("squid:S3398")
    private static List<InjectionPoint> injectionFields(Class instanceClass) {
        Class cls = instanceClass;
        List<InjectionPoint> list = new ArrayList<>();
        while (cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                InjectionPoint injectionPoint = injectionPoint(field, () -> new InjectionPoint(field)).orElse(null);
                if (injectionPoint != null) {
                    list.add(injectionPoint);
                }
            }
            cls = cls.getSuperclass();
        }
        return list;
    }

    @SuppressWarnings("squid:S3398")
    private static List<InjectionPoint> injectionProperties(Class instanceClass) {
        List<InjectionPoint> list = new ArrayList<>();
        BeanInfo beanInfo = Reflection.getBeanInfo(instanceClass);
        for (PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
            InjectionPoint injectionPoint = injectionPoint(prop.getWriteMethod(), () -> new InjectionPoint(prop.getWriteMethod()))
                    .orElseGet(() -> injectionPoint(prop.getReadMethod(), () -> new InjectionPoint(prop.getWriteMethod())).orElse(null));

            if (injectionPoint != null) {
                list.add(injectionPoint);
            }
        }
        return list;
    }

    private static Optional<InjectionPoint> injectionPoint(AccessibleObject accessibleObject, Supplier<InjectionPoint> supplier) {
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

    private  enum InjectionAttempt {
        ERROR,
        INJECTED,
        NOT_INJECTED
    }

    private static class InjectionPoint {
        protected String name;
        protected boolean optional;
        protected Class type = void.class;
        protected String property = "";

        private BiConsumer lambdaSetter;
        private MethodHandle setter;
        private Type setterType;
        private AccessibleObject accessible;

        InjectionPoint(Method setter) {
            try {
                accessible = setter;
                setterType = setter.getGenericParameterTypes()[0];
                this.setter = lookup.unreflect(setter);
                lambdaSetter = Lambda.reflectSetter(lookup, setter);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        InjectionPoint(Field field) {
            accessible = field;
            this.setterType = field.getGenericType();
            if (!Modifier.isPublic(field.getModifiers()))
                field.setAccessible(true);

            try {
                this.setter = lookup.unreflectSetter(field);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        @SuppressWarnings({"unchecked", "squid:S3776"})
        InjectionAttempt inject(InjectorImpl injector, Object target) {
            Type t = getType();
            Optional<Binding> opt = injector.privateGetNamedInstance(t, name);

            if (!opt.isPresent() && !optional) {
                return InjectionAttempt.ERROR;
            }

            if (opt.isPresent()) {
                try {
                    Object value = opt.get().getValue();
                    if (property.length() > 0 && value != null) {
                        value = Reflection.getProperty(value, property);
                    }
                    if (lambdaSetter != null)
                        lambdaSetter.accept(target, value);
                    else
                        setter.invoke(target, value);
                } catch (InvocationTargetException e) {
                    throw new SystemException(e.getTargetException());
                } catch (Throwable e) {
                    throw new SystemException(e);
                }
                return InjectionAttempt.INJECTED;
            } else {
                return InjectionAttempt.NOT_INJECTED;
            }
        }

        Type getType() {
            return (type != void.class && type != Void.class) ? type : setterType;
        }

        public String toString() {
            return accessible.toString();
        }
    }
}
