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

package net.e6tech.elements.cassandra.generator;

import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;
import net.e6tech.elements.cassandra.annotations.Mapping;
import net.e6tech.elements.cassandra.annotations.Mappings;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.TextSubstitution;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class Codec<T> extends TypeCodec<T> {

    private final TypeCodec<UDTValue> innerCodec;
    private List<MappingDescriptor> mappingDescriptors;

    public Codec(TypeCodec<UDTValue> innerCodec, Class<T> javaType) {
        super(innerCodec.getCqlType(), javaType);
        this.innerCodec = innerCodec;
        mappingDescriptors = analyze(getClass());
    }

    public List<MappingDescriptor> getMappingDescriptors() {
        return mappingDescriptors;
    }

    protected TypeCodec<UDTValue> getInnerCodec() {
        return innerCodec;
    }

    protected UserType getUserType() {
        return (UserType) innerCodec.getCqlType();
    }

    @Override
    public ByteBuffer serialize(T value, ProtocolVersion protocolVersion) {
        if (value == null)
            return null;
        UDTValue udtValue = getUserType().newValue();
        serialize(udtValue, value);
        return getInnerCodec().serialize(udtValue, protocolVersion);
    }

    @Override
    public T deserialize(ByteBuffer bytes, ProtocolVersion protocolVersion) {
        if (bytes == null)
            return null;
        return deserialize(getInnerCodec().deserialize(bytes, protocolVersion));
    }

    @Override
    public T parse(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("NULL"))
            return null;
        return deserialize(getInnerCodec().parse(value));
    }

    @Override
    public String format(T value) {
        if (value == null)
            return null;
        UDTValue udtValue = getUserType().newValue();
        serialize(udtValue, value);
        return getInnerCodec().format(udtValue);
    }

    @SuppressWarnings("unchecked")
    public T deserialize(UDTValue udtValue) {
        T t = null;
        try {
            t = (T) getJavaType().getRawType().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
        for (MappingDescriptor descriptor : mappingDescriptors) {
            if (descriptor.deserialize != null) {
                descriptor.deserialize.accept(udtValue, t);
            }
        }
        return t;
    }

    public void serialize(UDTValue udtValue, T value) {
        for (MappingDescriptor descriptor : mappingDescriptors) {
            if (descriptor.serialize != null) {
                descriptor.serialize.accept(udtValue, value);
            }
        }
    }

    public static List<MappingDescriptor> analyze(Class<? extends Codec> codecClass) {
        Class mappedClass = Reflection.getParametrizedType(codecClass, 0);
        List<MappingDescriptor> descriptors = new ArrayList<>();
        Mappings mappings = codecClass.getAnnotation(Mappings.class);
        if (mappings == null)
            return descriptors;
        String[] implicit = mappings.value();
        StringBuilder builder = new StringBuilder();
        for (String columnName : implicit) {
            builder.setLength(0);
            String[] parts = columnName.split("_");
            boolean first = true;
            for (String s : parts) {
                if (first) {
                    builder.append(s);
                    first = false;
                } else {
                    builder.append(TextSubstitution.capitalize(s));
                }
            }
            PropertyDescriptor descriptor;
            try {
                descriptor = new PropertyDescriptor(builder.toString(), mappedClass);
                descriptors.add(new MappingDescriptor(null, columnName, descriptor));
            } catch (IntrospectionException e) {
                throw new SystemException(e);
            }
        }

        for (Mapping mapping : mappings.mappings()) {
            mapping.property();
            PropertyDescriptor descriptor;
            try {
                descriptor = new PropertyDescriptor(mapping.property(), mappedClass);
                descriptors.add(new MappingDescriptor(mapping, mapping.value(), descriptor));
            } catch (IntrospectionException e) {
                throw new SystemException(e);
            }
        }
        return descriptors;
    }

    @SuppressWarnings("squid:S3776")
    public static class MappingDescriptor implements TypeDescriptor {
        private String columnName;
        private PropertyDescriptor descriptor;
        private BiConsumer<UDTValue, Object> deserialize;
        private BiConsumer<UDTValue, Object> serialize;
        private ParameterizedType parameterizedType;
        private Mapping mapping;

        @SuppressWarnings("unchecked")
        MappingDescriptor(Mapping mapping, String columnName, PropertyDescriptor descriptor) {
            this.mapping = mapping;
            this.columnName = columnName;
            this.descriptor = descriptor;

            if (descriptor.getReadMethod() != null) {
                Class pType = descriptor.getReadMethod().getReturnType();
                Type genericType = descriptor.getReadMethod().getGenericReturnType();
                if (genericType instanceof ParameterizedType) {
                    parameterizedType = (ParameterizedType) genericType;
                }
                if (List.class.isAssignableFrom(pType)) {
                    deserialize = (udt, o) ->
                        set(o, udt.getList(this.columnName, (Class) parameterizedType.getActualTypeArguments()[0]));
                } else if (Set.class.isAssignableFrom(pType)) {
                    serialize = (udt, o) ->
                        udt.setSet(this.columnName, get(o));
                } else if (Map.class.isAssignableFrom(pType)) {
                    deserialize = (udt, o) ->
                        set(o, udt.getMap(this.columnName, (Class) parameterizedType.getActualTypeArguments()[0],
                                (Class) parameterizedType.getActualTypeArguments()[1]));
                } else if (Enum.class.isAssignableFrom(pType)) {
                    deserialize = (udt, o) -> {
                        String str = udt.getString(this.columnName);
                        if (str != null) {
                            Enum e = Enum.valueOf((Class) descriptor.getPropertyType(), str);
                            set(o, e);
                        } else {
                            set(o, null);
                        }
                    };
                } else if (byte[].class.isAssignableFrom(pType)) {
                    deserialize = (udt, o) -> {
                        ByteBuffer byteBuffer = udt.getBytes(this.columnName);
                        if (byteBuffer != null) {
                            byte[] bytes = byteBuffer.array();
                            set(o, bytes);
                        } else {
                            set(o, null);
                        }
                    };
                } else {
                    deserialize = (udt, o) ->
                        set(o, udt.get(this.columnName, descriptor.getPropertyType()));
                }
            }

            if (descriptor.getWriteMethod() != null) {
                Class pType = descriptor.getWriteMethod().getParameterTypes()[0];
                if (List.class.isAssignableFrom(pType)) {
                    serialize = (udt, o) ->
                        udt.setList(this.columnName, get(o));
                } else if (Set.class.isAssignableFrom(pType)) {
                    serialize = (udt, o) ->
                        udt.setSet(this.columnName, get(o));
                } else if (Map.class.isAssignableFrom(pType)) {
                    serialize = (udt, o) ->
                        udt.setMap(this.columnName, get(o));
                } else if (Enum.class.isAssignableFrom(pType)) {
                    serialize = (udt, o) -> {
                        Enum e = get(o);
                        String str = (e == null) ? null : e.toString();
                        udt.setString(this.columnName, str);
                    };
                } else if (byte[].class.isAssignableFrom(pType)) {
                    serialize = (udt, o) -> {
                        byte[] bytes = get(o);
                        if (bytes != null) {
                            udt.setBytes(this.columnName, ByteBuffer.wrap(bytes));
                        } else {
                            udt.setToNull(this.columnName);
                        }
                    };
                } else {
                    serialize = (udt, o) ->
                        udt.set(this.columnName, get(o), descriptor.getPropertyType());
                }
            }
        }

        public PropertyDescriptor getPropertyDescriptor() {
            return descriptor;
        }

        @SuppressWarnings("unchecked")
        <U> U get(Object target) {
            try {
                return (U) descriptor.getReadMethod().invoke(target);
            } catch (Exception e) {
               throw new SystemException(e);
            }
        }

        void set(Object target, Object value) {
            try {
                descriptor.getWriteMethod().invoke(target, value);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        @Override
        public boolean isFrozen() {
            return (mapping != null) && mapping.frozen();
        }

        @Override
        public boolean isFrozenKey() {
            return (mapping != null) && mapping.frozenKey();
        }

        @Override
        public boolean isFrozenValue() {
            return (mapping != null) && mapping.frozenValue();
        }

        @Override
        public boolean isTimeBased() {
            return (mapping != null) && mapping.timeBased();
        }

        @Override
        public String getColumnName() {
            return columnName;
        }
    }
}
