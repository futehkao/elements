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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.annotations.TimeBased;
import net.e6tech.elements.common.reflection.Accessor;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@SuppressWarnings("squid:S1192")
public abstract class Generator {
    private static final String NULL_KEYSPACE = "";
    private Map<String, Class> dataNames = new HashMap<>();
    private Map<Type, String> dataTypes = new HashMap<>();
    private Map<String, Cache<Class, TableGenerator>> tables = new ConcurrentHashMap<>();

    public Generator() {
        dataTypes.put(Boolean.class, "boolean");
        dataTypes.put(Boolean.TYPE, "boolean");
        dataTypes.put(Byte.class, "tinyint");
        dataTypes.put(Byte.TYPE, "tinyint");
        dataTypes.put(Long.class, "bigint");
        dataTypes.put(Long.TYPE, "bigint");
        dataTypes.put(Integer.class, "int");
        dataTypes.put(Integer.TYPE, "int");
        dataTypes.put(BigDecimal.class, "decimal");
        dataTypes.put(Double.class, "double");
        dataTypes.put(Double.TYPE, "double");
        dataTypes.put(Float.class, "float");
        dataTypes.put(Float.TYPE, "float");
        dataTypes.put(Short.class, "smallint");
        dataTypes.put(Short.TYPE, "smallint");
        dataTypes.put(UUID.class, "uuid");
        dataTypes.put(ByteBuffer.class, "blob");
        dataTypes.put(BigInteger.class, "varint");
        dataTypes.put(LocalTime.class, "time");
        dataTypes.put(InetAddress.class, "inet");
        dataTypes.put(LocalDate.class, "date");
        dataTypes.put(String.class, "varchar");

        dataNames.put("boolean", Boolean.class);
        dataNames.put("tinyint", Byte.class);
        dataNames.put("bigint", Long.class);
        dataNames.put("int", Integer.class);
        dataNames.put("decimal", BigDecimal.class);
        dataNames.put("double", Double.class);
        dataNames.put("float", Float.class);
        dataNames.put("smallint", Short.class);
        dataNames.put("text", String.class);
        dataNames.put("varchar", String.class);
        dataNames.put("uuid", UUID.class);
        dataNames.put("blob", ByteBuffer.class);
        dataNames.put("varint", BigInteger.class);
        dataNames.put("time", LocalTime.class);
        dataNames.put("inet", InetAddress.class);
        dataNames.put("date", LocalDate.class);
    }

    public TableGenerator getTable(String keyspace, Class cls) {
        String s = (keyspace == null) ? NULL_KEYSPACE : keyspace.trim();
        Cache<Class, TableGenerator> generators = tables.computeIfAbsent(s,
                space -> CacheBuilder.newBuilder()
                        .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
                        .initialCapacity(200)
                        .expireAfterAccess(600, TimeUnit.SECONDS)
                        .maximumSize(1000)
                        .build());
        try {
            return generators.get(cls, () -> {
                TableGenerator gen;
                try {
                    gen = new TableGenerator(this, cls);
                    String ks = (keyspace == null) ? null : keyspace.trim();
                    gen.setKeyspace(ks);
                } catch (IntrospectionException e) {
                    throw new SystemException(e);
                }
                return gen;
            });
        } catch (ExecutionException e) {
            throw new SystemException(e);
        }
    }

    public IndexGenerator createIndexes(String keyspace, Class cls) throws IntrospectionException {
        IndexGenerator gen = null;
        gen = new IndexGenerator(this, cls);
        gen.setKeyspace(keyspace);
        return gen;
    }

    public String getDataType(Type type) {
        return dataTypes.get(type);
    }

    public void setDataType(Type type, String dataType) {
        dataTypes.put(type, dataType);
        if (type instanceof Class)
            dataNames.put(dataType, (Class) type);
    }

    @SuppressWarnings("unchecked")
    public Object getDataValue(String type, String value) {
        Class cls = dataNames.get(type);
        if (UUID.class.equals(cls)) {
            return UUID.fromString(value);
        } else if (byte[].class.isAssignableFrom(cls)) {
            throw new UnsupportedOperationException("byte[] is not supported");
        } else {
            try {
                return cls.getDeclaredConstructor(String.class).newInstance(value);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
    }

    public abstract String toCassandraName(String javaPropertyName);

    public abstract Class<? extends Annotation> tableAnnotation();

    public abstract Annotation tableAnnotation(Class sourceClass);

    public abstract String tableKeyspace(Class sourceClass);

    public abstract String tableName(Class sourceClass);

    public abstract boolean hasColumnAnnotation(AccessibleObject field);

    public abstract boolean hasColumnAnnotation(PropertyDescriptor desc);

    public abstract String getColumnName(Field field);

    public abstract String getColumnName(PropertyDescriptor descriptor);

    public abstract boolean partitionKeyIndex(AccessibleObject field, IntConsumer consumer);

    public abstract boolean partitionKeyIndex(PropertyDescriptor descriptor, IntConsumer consumer);

    public abstract boolean clusteringColumnIndex(AccessibleObject field, IntConsumer consumer);

    public abstract boolean clusteringColumnIndex(PropertyDescriptor descriptor, IntConsumer consumer);

    public abstract boolean isTransient(AccessibleObject field);

    public abstract boolean isTransient(PropertyDescriptor descriptor);

    public abstract boolean isFrozen(AccessibleObject field);

    public abstract boolean isFrozen(PropertyDescriptor descriptor);

    public abstract boolean isFrozenKey(AccessibleObject field);

    public abstract boolean isFrozenKey(PropertyDescriptor descriptor);

    public abstract boolean isFrozenValue(AccessibleObject field);

    public abstract boolean isFrozenValue(PropertyDescriptor descriptor);

    public boolean isTimeBased(AccessibleObject field) {
        return Accessor.getAnnotation(field, TimeBased.class) != null;
    }

    public boolean isTimeBased(PropertyDescriptor descriptor) {
        return Accessor.getAnnotation(descriptor, TimeBased.class) != null;
    }
}
