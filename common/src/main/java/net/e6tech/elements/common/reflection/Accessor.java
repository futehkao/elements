/*
 * Copyright 2015-2019 Futeh Kao
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

import net.e6tech.elements.common.util.SystemException;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Accessor {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    private MethodHandle setter;
    private MethodHandle getter;
    private BiConsumer lambdaSetter;
    private Function lambdaGetter;
    private String name;
    private Class type;

    public Accessor(Field field) {
        field(field);
    }

    public Accessor(PropertyDescriptor descriptor) {
        descriptor(descriptor);
    }

    public static <A extends Annotation> A getAnnotation(PropertyDescriptor desc, AccessibleObject accessibleObject,
                                                         Class<A> annotationClass) {
        A a = null;
        if (desc != null) {
            Method m = null;
            if (desc.getReadMethod() != null) {
                m = desc.getReadMethod();
            } else if (desc.getWriteMethod() != null) {
                m = desc.getWriteMethod();
            }
            if (m != null)
                a = m.getAnnotation(annotationClass);
        }

        if (a == null && accessibleObject != null)
            a = accessibleObject.getAnnotation(annotationClass);
        return a;
    }

    public static <A extends Annotation> A getAnnotation(PropertyDescriptor desc, Class<A> annotationClass) {
        return getAnnotation(desc, null, annotationClass);
    }

    public static <A extends Annotation> A getAnnotation(AccessibleObject accessibleObject, Class<A> annotationClass) {
        return getAnnotation(null, accessibleObject, annotationClass);
    }

    public static Map<Class<? extends Annotation>, Annotation> getAnnotations(PropertyDescriptor desc ) {
        Map<Class<? extends Annotation>, Annotation> map = new HashMap<>();
        Method m = null;
        if (desc.getReadMethod() != null) {
            m = desc.getReadMethod();
            for (Annotation a : m.getAnnotations()) {
                map.put(a.annotationType(), a);
            }
        } if (desc.getWriteMethod() != null) {
            m = desc.getWriteMethod();
            for (Annotation a : m.getAnnotations()) {
                map.put(a.annotationType(), a);
            }
        }
        return map;
    }

    public Accessor descriptor(PropertyDescriptor descriptor) {
        try {
            name = descriptor.getName();
            type = descriptor.getPropertyType();
            if (descriptor.getWriteMethod() != null) {
                setter = lookup.unreflect(descriptor.getWriteMethod());
                lambdaSetter = Lambda.reflectSetter(lookup, descriptor.getWriteMethod());
            }
        } catch (Exception ex) {
            throw new SystemException(ex);
        }

        try {
            if (descriptor.getReadMethod() != null) {
                getter = lookup.unreflect(descriptor.getReadMethod());
                lambdaGetter = Lambda.reflectGetter(lookup, descriptor.getReadMethod());
            }
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
        return this;
    }

    public Accessor field(Field field) {
        if (setter != null)
            return this; // already set via property descriptor
        name = field.getName();
        type = field.getType();

        if (!Modifier.isPublic(field.getModifiers()))
            field.setAccessible(true);

        try {
            this.setter = lookup.unreflectSetter(field);
            this.getter = lookup.unreflectGetter(field);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return this;
    }

    public String getName() {
        return name;
    }

    public Object get(Object target) {
        try {
            if (lambdaGetter != null)
                return lambdaGetter.apply(target);
            else
                return getter.invoke(target);
        } catch (InvocationTargetException e) {
            throw new SystemException(e.getTargetException());
        } catch (Throwable e) {
            throw new SystemException(e);
        }
    }

    public void set(Object target, Object value) {
        try {
            if (lambdaSetter != null)
                lambdaSetter.accept(target, value);
            else
                setter.invoke(target, value);
        } catch (InvocationTargetException e) {
            throw new SystemException(e.getTargetException());
        } catch (Throwable e) {
            throw new SystemException(e);
        }
    }

    public Class getType() {
        return type;
    }
}
