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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Accessors<T extends Accessor> {

    public static final Function<Field, Accessor> DEFAULT_FIELD_ACCESSOR_FACTORY = Accessor::new;
    public static final BiFunction<PropertyDescriptor, Accessor, Accessor> DEFAULT_PROPERTY_ACCESSOR_FACTORY =
            (desc, accessor) -> {
                if (desc.getName().equals("class"))
                    return null;
                if (accessor != null)
                    return accessor.descriptor(desc);
                else
                    return new Accessor(desc);
            };

    private Map<String, T> map = new HashMap<>(100);

    public static Accessors<Accessor> simple(Class cls) {
        return new Accessors<>(cls,
                DEFAULT_FIELD_ACCESSOR_FACTORY,
                DEFAULT_PROPERTY_ACCESSOR_FACTORY);
    }

    public Accessors(Class targetClass, Function<Field, T> fieldFactory,
                     BiFunction<PropertyDescriptor, T, T> descriptorFactory)  {
        analyzeFields(targetClass, fieldFactory);
        analyzedDescriptors(targetClass, descriptorFactory);
    }

    private void analyzeFields(Class targetClass, Function<Field, T> fieldFactory) {
        Class cls = targetClass;
        if (fieldFactory != null) {
            while (cls != null && cls != Object.class) {
                Field[] fields = cls.getDeclaredFields();
                for (Field field : fields) {
                    if (Modifier.isStrict(field.getModifiers()))
                        continue;
                    T t = fieldFactory.apply(field);
                    if (t != null && !map.containsKey(t.getName()))
                        map.put(t.getName(), t);
                }
                cls = cls.getSuperclass();
            }
        }
    }

    @SuppressWarnings("squid:S3776")
    private void analyzedDescriptors(Class targetClass, BiFunction<PropertyDescriptor, T, T> descriptorFactory) {
        if (descriptorFactory != null) {
            try {
                for (PropertyDescriptor desc : Introspector.getBeanInfo(targetClass).getPropertyDescriptors()) {
                    T t = map.get(desc.getName());
                    String name = (t == null) ? null : t.getName();
                    t = descriptorFactory.apply(desc, t);
                    if (t != null)
                        map.put(t.getName(), t);
                    else {
                        if (name != null)
                            map.remove(name);
                    }
                }
            } catch (IntrospectionException e) {
                throw new SystemException(e);
            }
        }
    }

    public Map<String, T> getAccessors() {
        return map;
    }

    public Object get(Object target, String property) {
        T t = map.get(property);
        if (t != null)
            return t.get(target);
        else
            throw new IllegalArgumentException("Target class " + target.getClass() + " does not have a property named " + property);
    }

    public void set(Object target, String property, Object value) {
        T t = map.get(property);
        if (t != null)
            t.set(target, value);
        else
            throw new IllegalArgumentException("Target class " + target.getClass() + " does not have a property named " + property);
    }
}
