/*
 * Copyright 2015 Futeh Kao
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by futeh.
 */
public class ObjectConverter {

    public static final  ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public static Class loadClass(ClassLoader loader, String name) throws ClassNotFoundException {
        if (Primitives.isPrimitive(name))
            return Primitives.get(name);
        return loader.loadClass(name);
    }

    public Object convert(Object from, Method getterOrSetter, InstanceCreationListener listener) throws IOException {
        Type returnType = getterOrSetter.getGenericReturnType();
        if (!returnType.equals(Void.TYPE)) {
            // getter
            if (getterOrSetter.getParameterTypes().length != 0) {
                throw new IllegalArgumentException("Method " + getterOrSetter.getName() + " must be a getter");
            }
            return convert(from, returnType, listener);
        } else {
            Type[] parameters = getterOrSetter.getGenericParameterTypes();
            if (parameters.length != 1)
                throw new IllegalArgumentException("Method " + getterOrSetter.getName() + " must be a setter");
            Type argumentType = parameters[0];
            return convert(from, argumentType, listener);
        }
    }

    public Object convert(Object from, Field field, InstanceCreationListener listener) throws IOException {
        return convert(from, field.getGenericType(), listener);
    }

    @SuppressWarnings({"squid:S134", "squid:S3776"})
    public Object convert(Object from, Type toType, InstanceCreationListener listener) throws IOException {
        Object converted;
        if (toType instanceof Class) {
            converted = convert(from, (Class) toType, listener);
        } else {
            ParameterizedType parametrized = (ParameterizedType) toType;
            Class enclosedType = (Class) parametrized.getRawType();
            Type type = parametrized.getActualTypeArguments()[0];
            if (type instanceof Class) {
                // for now, we limit ourselves to detecting one level.  A counter example would be
                // List<List<List<X>>> or List<Map<X, Y>>
                Class elementType = (Class) type;
                if (Collection.class.isAssignableFrom(enclosedType)) {
                    converted = convertCollection((Collection) from, enclosedType, elementType, listener);
                } else {
                    converted = convert(from, enclosedType, listener);
                }
            } else if(type instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType) type;
                if (ptype.getRawType() instanceof Class) {
                    Class elementType = (Class) ptype.getRawType();
                    if (Collection.class.isAssignableFrom(enclosedType)) {
                        converted = convertCollection((Collection) from, enclosedType, elementType, listener);
                    } else {
                        converted = convert(from, enclosedType, listener);
                    }
                } else {
                    converted = convert(from, enclosedType, listener);
                }
            } else {
                converted = convert(from, enclosedType, listener);
            }
        }
        return converted;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S3776"})
    private Object convert(Object val, Class toType, InstanceCreationListener instanceCreation) throws IOException {
        Object value = val;
        Class fromType = value.getClass();

        if (toType.isArray()) {
            if (fromType.isArray() && fromType.getComponentType().equals(toType.getComponentType())) {
                return value;
            }

            if (value instanceof Collection) {
                Collection coll = (Collection) value;
                Object array = Array.newInstance(toType.getComponentType(), coll.size());
                Iterator iterator = coll.iterator();
                int index = 0;
                while (iterator.hasNext()) {
                    Object member = iterator.next();
                    Array.set(array, index, convert(member, toType.getComponentType(), instanceCreation));
                    index++;
                }
                return array;
            } else {
                throw new IllegalArgumentException("Cannot convert " + fromType + " to " + toType);
            }
        } else if (toType.isPrimitive() || fromType.isPrimitive()) {
            // converting primitive type
            boolean needConversion;
            if (toType.equals(fromType))
                needConversion = false;
            else if (toType.isPrimitive())
                needConversion = shouldConvertPrimitive(toType, fromType);
            else needConversion = shouldConvertPrimitive(fromType, toType);
            if (!needConversion)
                return value;
        } else if (toType.isAssignableFrom(fromType)) {
            // no conversion
            return value;
        } else if (value instanceof String && ! (toType.isAssignableFrom(Class.class))) {
            // converting from String to other types.
            try {
                // converting from String directly, e.g. mapper can convert from String to BigDecimal
                String str = mapper.writeValueAsString(value);
                value = mapper.readValue(str, toType);
                return value;
            } catch (Exception e) {
                Logger.suppress(e);
                // OK mapper cannot convert String directly so we assume the String is a full
                // class name.  We load the class and create an instance.
                try {
                    Class cls = getClass().getClassLoader().loadClass((String) value);
                    value = cls.newInstance();
                    if (instanceCreation != null)
                        instanceCreation.instanceCreated(value, toType, value);
                    return value;
                } catch (Exception e1) {
                    throw new SystemException(e1);
                }
            }
        }

        // we use the mapper to convert
        String str = mapper.writeValueAsString(value);
        return mapper.readValue(str, toType);
    }

    private Collection convertCollection(Collection value, Class<? extends Collection> collectionType, Class elementType,
                                    InstanceCreationListener instanceCreation) throws IOException {

        CollectionType ctype = TypeFactory.defaultInstance().constructCollectionType(collectionType, elementType);
        String str = mapper.writeValueAsString(value);
        Collection converted = mapper.readValue(str, ctype);

        if (instanceCreation != null) {
            Iterator iter1 = value.iterator();
            Iterator iter2 = converted.iterator();
            while (iter1.hasNext() && iter2.hasNext()) {
                instanceCreation.instanceCreated(iter1.hasNext(), elementType, iter2.next());
            }
        }

        return converted;
    }

    @SuppressWarnings({"squid:S1067", "squid:MethodCyclomaticComplexity"})
    protected boolean shouldConvertPrimitive(Class c1, Class c2) {
        return ! ((c1.equals(Boolean.TYPE) && Boolean.class.equals(c2))
                || (c1.equals(Character.TYPE) && Character.class.equals(c2))
                || (c1.equals(Byte.TYPE) && Byte.class.equals(c2))
                || (c1.equals(Short.TYPE) && Short.class.equals(c2))
                || (c1.equals(Integer.TYPE) && Integer.class.equals(c2))
                || (c1.equals(Long.TYPE) && Long.class.equals(c2))
                || (c1.equals(Float.TYPE) && Float.class.equals(c2))
                || (c1.equals(Double.TYPE) && Double.class.equals(c2)));
    }

    @FunctionalInterface
    public interface InstanceCreationListener {
        void instanceCreated(Object value, Class toType, Object instance);
    }
}
