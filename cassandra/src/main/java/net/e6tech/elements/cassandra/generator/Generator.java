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

import com.datastax.driver.mapping.NamingStrategy;
import com.datastax.driver.mapping.annotations.Column;
import net.e6tech.elements.common.util.SystemException;

import java.beans.IntrospectionException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Generator {

    private Map<String, Class> dataNames = new HashMap<>();
    private Map<Type, String> dataTypes = new HashMap<>();
    private NamingStrategy namingStrategy;

    public Generator() {
        dataTypes.put(Boolean.class, "boolean");
        dataTypes.put(Boolean.TYPE, "boolean");
        dataTypes.put(Long.class, "bigint");
        dataTypes.put(Long.TYPE, "bigint");
        dataTypes.put(Integer.class, "int");
        dataTypes.put(Integer.TYPE, "int");
        dataTypes.put(BigDecimal.class, "decimal");
        dataTypes.put(Double.class, "double");
        dataTypes.put(Double.TYPE, "double");
        dataTypes.put(Float.class, "float");
        dataTypes.put(Float.TYPE, "float");
        dataTypes.put(String.class, "text");
        dataTypes.put(UUID.class, "uuid");
        dataTypes.put(byte[].class, "blob");

        dataNames.put("boolean", Boolean.class);
        dataNames.put("bigint", Long.class);
        dataNames.put("int", Integer.class);
        dataNames.put("decimal", BigDecimal.class);
        dataNames.put("double", Double.class);
        dataNames.put("float", Float.class);
        dataNames.put("text", String.class);
        dataNames.put("uuid", UUID.class);
        dataNames.put("blob", byte[].class);
    }

    public String createTable(String keyspace, Class cls) {
        TableGenerator gen = null;
        try {
            gen = new TableGenerator(this, cls);
            gen.setKeyspace(keyspace);
        } catch (IntrospectionException e) {
            throw new SystemException(e);
        }
        return gen.generate();
    }

    public List<String> createIndexes(String keyspace, Class cls) {
        IndexGenerator gen = null;
        try {
            gen = new IndexGenerator(this, cls);
            gen.setKeyspace(keyspace);
        } catch (IntrospectionException e) {
            throw new SystemException(e);
        }
        return gen.generate();
    }

    public String createCodecs(String keyspace, String userType, Class<? extends Codec> codecClass) {
        CodecGenerator gen = new CodecGenerator(this, userType, codecClass);
        gen.setKeyspace(keyspace);
        return gen.generate();
    }

    public String getDataType(Type type) {
        return dataTypes.get(type);
    }

    public void setDataType(Type type, String dataType) {
        dataTypes.put(type, dataType);
        if (type instanceof Class)
            dataNames.put(dataType, (Class) type);
    }

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

    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public String toCassandraName(String javaPropertyName) {
        return namingStrategy.toCassandraName(javaPropertyName);
    }

    public String getColumnName(Column column, Field field) {
        String columnName;
        if (column == null || column.name().isEmpty()) {
            columnName = toCassandraName(field.getName());
        } else {
            columnName = column.name();
        }
        return columnName;
    }

    public String getColumnName(Column column, Method method) {
        String columnName;
        if (column == null || column.name().isEmpty()) {
            String name = method.getName();
            if (name.startsWith("is"))
                name = name.substring(2);
            else if (name.startsWith("set") || name.startsWith("get"))
                name = name .substring(3);
            columnName = toCassandraName(name);
        } else {
            columnName = column.name();
        }
        return columnName;
    }
}
