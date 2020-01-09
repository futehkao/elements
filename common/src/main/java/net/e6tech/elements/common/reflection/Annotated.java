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

package net.e6tech.elements.common.reflection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.datastructure.Pair;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * This class is used to extract Java bean methods or fields that are annotated.
 */
@SuppressWarnings("unchecked")
public class Annotated<R, A extends Annotation> {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    private static LoadingCache<Pair<Class<?>, Class<? extends Annotation>>, Annotated> annotatedCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .initialCapacity(100)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Pair<Class<?>, Class<? extends Annotation>>, Annotated>() {
                public Annotated load(Pair<Class<?>, Class<? extends Annotation>> pair)  {
                    return new Annotated(pair.key(), pair.value());
                }
            });

    private Class<A> annotationClass;
    private List<Entry<A>> entries = new ArrayList<>();
    private Cache<Pair<String,?>, Lookup<A, ?, ?>> lookups = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .maximumSize(1000)
            .initialCapacity(16)
            .build();

    public Annotated(Class<R> clazz, Class<A> annotationClass) {
        this.annotationClass = annotationClass;
        properties(clazz);
        fields(clazz);
        entries = Collections.unmodifiableList(entries);
    }

    public static <A extends Annotation, E, V> Accessor<A, E, V> accessor(Object target, Class<A> annotationClass, Function<A, E> function, Class<V> valueType) {
        Lookup<A, E, V> lookup = lookup(target.getClass(), annotationClass, function, valueType);
        return lookup.accessor(target);
    }

    public static <A extends Annotation, E, V> Lookup<A, E, V> lookup(Class clazz, Class<A> annotationClass, Function<A, E> function, Class<V> valueType) {
        try {
            return annotatedCache.get(new Pair<>(clazz, annotationClass)).lookup(function, valueType);
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    public <E, V> Lookup<A, E, V> lookup(Function<A, E> function, Class<V> valueType) {
        MyInvocationHandler handler = new MyInvocationHandler();
        A proxy = (A) Proxy.newProxyInstance(annotationClass.getClassLoader(), new Class[]{ annotationClass }, handler);
        function.apply(proxy);
        Method method = handler.method;
        if (method == null) {
            throw new IllegalArgumentException("Null annotation method for " + annotationClass);
        }
        try {
            return (Lookup) lookups.get(new Pair<>(method.getName(), valueType), () -> new Lookup<>(this, method, valueType));
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    private void fields(Class<R> clazz) {
        Class cls = clazz;
        while (cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                A e = field.getDeclaredAnnotation(annotationClass);
                if (e != null) {
                    entries.add(new Entry(e, field));
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private void properties(Class<R> clazz) {
        BeanInfo beanInfo = Reflection.getBeanInfo(clazz);
        for (PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
            Annotation e = null;
            if (prop.getWriteMethod() != null)
                e = prop.getWriteMethod().getDeclaredAnnotation(annotationClass);

            if (e == null && prop.getReadMethod() != null)
                e = prop.getReadMethod().getDeclaredAnnotation(annotationClass);

            if (e != null)
                entries.add(new Entry(e, prop.getName(), prop.getReadMethod(), prop.getWriteMethod()));
        }
    }

    public static class Entry<A> {
        private String name;
        private MethodHandle setter;
        private MethodHandle getter;
        private Function lambdaGetter;
        private BiConsumer lambdaSetter;
        private A annotation;
        private Type type;
        private Class<?> rawType;

        Entry(A annotation, String name, Method getter, Method setter) {
            this.annotation = annotation;
            this.name= name;
            try {
                if (setter != null) {
                    this.setter = lookup.unreflect(setter);
                    type = setter.getGenericParameterTypes()[0];
                    rawType = setter.getParameterTypes()[0];
                    lambdaSetter = Lambda.reflectSetter(lookup, setter);
                }

                if (getter != null) {
                    this.getter = lookup.unreflect(getter);
                    type = getter.getGenericReturnType();
                    rawType = getter.getReturnType();
                    lambdaGetter = Lambda.reflectGetter(lookup, getter);
                }

            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        Entry(A annotation, Field field) {
            this.annotation = annotation;
            this.name = field.getName();
            this.type = field.getGenericType();
            this.rawType = field.getType();
            if (!Modifier.isPublic(field.getModifiers()))
                field.setAccessible(true);

            try {
                this.setter = lookup.unreflectSetter(field);
                this.getter = lookup.unreflectGetter(field);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        public Object get(Object target) {
            Object value = null;
            if (lambdaGetter != null)
                value = lambdaGetter.apply(target);
            else if (getter != null) {
                try {
                    value = getter.invoke(target);
                } catch (Throwable throwable) {
                    // ignore
                }
            }
            return value;
        }

        public void set(Object target, Object value) {
            if (lambdaSetter != null)
                lambdaSetter.accept(target, value);
            else if (setter != null) {
                try {
                    setter.invoke(target, value);
                } catch (Throwable throwable) {
                    // ignore
                }
            }
        }

        public String getName() {
            return name;
        }

        public <T extends Annotation> T getAnnotation() {
            return (T) annotation;
        }

        public Type getType() {
            return type;
        }

        public Class<?> getRawType() {
            return rawType;
        }
    }

    public static class Lookup<A extends Annotation, E, V> {
        private Map<E, List<Entry<A>>> annotationValues;
        private List<Entry<A>> entries = new ArrayList<>();

        Lookup(Annotated<?, A> annotated, Method method, Class<V> valueClass) {
            annotationValues = new HashMap<>();
            for (Entry<A> e : annotated.entries) {
                if (valueClass != null && !valueClass.isAssignableFrom(e.getRawType()))
                    continue;
                E value = null;
                try {
                    value = (E) method.invoke(e.annotation);
                } catch (Exception e1) {
                    // ignored
                }
                if (value != null) {
                    List<Entry<A>> list = annotationValues.computeIfAbsent(value, key2 -> new ArrayList<>());
                    list.add(e);
                    entries.add(e);
                }
            }
            entries = Collections.unmodifiableList(entries);
            annotationValues = Collections.unmodifiableMap(annotationValues);
        }

        public List<Entry<A>> entries() {
            return entries;
        }

        public Map<E, List<Entry<A>>> annotationValues() {
            return annotationValues;
        }

        public Map<E, List<Entry<A>>> find(E ... searchValues) {
            Map<E, List<Entry<A>>> result = new HashMap<>();
            if (searchValues != null) {
                for (E searchValue : searchValues) {
                    List<Entry<A>> found = annotationValues.get(searchValue);
                    if (found != null)
                        result.put(searchValue, found);
                }
            } else {
                result.putAll(annotationValues);
            }
            return result;
        }

        public Accessor<A, E, V> accessor(Object target) {
            return new Accessor<>(this, target);
        }
    }

    public static class Accessor<A extends Annotation, E, V> {
        private Object target;
        private Lookup<A, E, V> lookup;

        Accessor(Lookup<A, E, V> lookup, Object target) {
            this.lookup = lookup;
            this.target = target;
        }

        public V get(E annotatedValue) {
            Map<E, List<Entry<A>>> result = lookup.find(annotatedValue);
            List<Entry<A>> list = result.get(annotatedValue);
            if (list != null)
                return (V) list.get(0).get(target);
            return null;
        }

        public Map<E, V> get(E ... annotatedValues) {
            Map<E, V> map = new HashMap<>();
            Map<E, List<Entry<A>>> result = lookup.find(annotatedValues);
            for (Map.Entry<E, List<Entry<A>>> e : result.entrySet()) {
                map.put(e.getKey(), (V) e.getValue().get(0).get(target));
            }
            return map;
        }

        public Map<E, V> getAll() {
            Map<E, V> map = new HashMap<>();
            for (Map.Entry<E, List<Entry<A>>> e : lookup.annotationValues().entrySet()) {
                map.put(e.getKey(), (V) e.getValue().get(0).get(target));
            }
            return map;
        }

        public Accessor<A, E, V> set(E annotatedValue, Object value) {
            Map<E, List<Entry<A>>> result = lookup.find(annotatedValue);
            List<Entry<A>> list = result.get(annotatedValue);
            if (list != null && !list.isEmpty()) {
                list.get(0).set(target, value);
            }
            return this;
        }
    }

    private static class MyInvocationHandler implements InvocationHandler {
        Method method;
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Annotator.objectMethods.containsKey(method.getName())
                    && method.getParameterCount() == Annotator.objectMethods.get(method.getName())) {
                // skip
            } else {
                this.method = method;
            }

            if (method.getReturnType().isPrimitive()) {
                return Primitives.defaultValue(method.getReturnType());
            } else {
                return null;
            }
        }
    }
}
