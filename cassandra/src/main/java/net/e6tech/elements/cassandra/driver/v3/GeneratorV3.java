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

package net.e6tech.elements.cassandra.driver.v3;

import com.datastax.driver.mapping.DefaultNamingStrategy;
import com.datastax.driver.mapping.NamingConventions;
import com.datastax.driver.mapping.NamingStrategy;
import com.datastax.driver.mapping.annotations.*;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.reflection.Accessor;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

public class GeneratorV3 extends Generator {

    private NamingStrategy namingStrategy = new DefaultNamingStrategy(NamingConventions.LOWER_CAMEL_CASE, NamingConventions.LOWER_SNAKE_CASE);

    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public String toCassandraName(String javaPropertyName) {
        return namingStrategy.toCassandraName(javaPropertyName);
    }

    public Class<? extends Annotation> tableAnnotation() {
        return Table.class;
    }

    @SuppressWarnings("unchecked")
    public Annotation tableAnnotation(Class sourceClass) {
        return sourceClass.getAnnotation(Table.class);
    }

    @SuppressWarnings("unchecked")
    public String tableKeyspace(Class sourceClass) {
        Table table = (Table) sourceClass.getAnnotation(Table.class);
        if (table != null)
            return table.keyspace();
        return null;
    }

    @SuppressWarnings("unchecked")
    public String tableName(Class sourceClass) {
        Table table = (Table) sourceClass.getAnnotation(Table.class);
        if (table == null)
            throw new IllegalArgumentException("Class " + sourceClass + " is not annotated with @Table");
        return table.name();
    }

    public boolean hasColumnAnnotation(AccessibleObject field) {
        return Accessor.getAnnotation(field, Column.class) != null;
    }

    public boolean hasColumnAnnotation(PropertyDescriptor desc) {
        return Accessor.getAnnotation(desc, Column.class) != null;
    }

    public String getColumnName(Field field) {
        Column column = Accessor.getAnnotation(field, Column.class);
        String columnName;
        if (column == null || column.name().isEmpty()) {
            columnName = toCassandraName(field.getName());
        } else {
            columnName = column.name();
        }
        return columnName;
    }

    public String getColumnName(PropertyDescriptor descriptor) {
        Column column = Accessor.getAnnotation(descriptor, Column.class);
        String columnName;
        if (column == null || column.name().isEmpty()) {
            columnName = toCassandraName(descriptor.getName());
        } else {
            columnName = column.name();
        }
        return columnName;
    }

    public int partitionKeyIndex(AccessibleObject field) {
        PartitionKey annotation = Accessor.getAnnotation(field, PartitionKey.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public int partitionKeyIndex(PropertyDescriptor descriptor) {
        PartitionKey annotation = Accessor.getAnnotation(descriptor, PartitionKey.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public int clusteringColumnIndex(AccessibleObject field) {
        ClusteringColumn annotation = Accessor.getAnnotation(field, ClusteringColumn.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public int clusteringColumnIndex(PropertyDescriptor descriptor) {
        ClusteringColumn annotation = Accessor.getAnnotation(descriptor, ClusteringColumn.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public boolean isTransient(AccessibleObject field) {
        return Accessor.getAnnotation(field, Transient.class) != null;
    }

    public boolean isTransient(PropertyDescriptor descriptor) {
        return Accessor.getAnnotation(descriptor, Transient.class) != null;
    }

    @Override
    public boolean isFrozen(AccessibleObject field) {
        return Accessor.getAnnotation(field, Frozen.class) != null;
    }

    @Override
    public boolean isFrozen(PropertyDescriptor descriptor) {
        return Accessor.getAnnotation(descriptor, Frozen.class) != null;
    }

    @Override
    public boolean isFrozenKey(AccessibleObject field) {
        return Accessor.getAnnotation(field, FrozenKey.class) != null;
    }

    @Override
    public boolean isFrozenKey(PropertyDescriptor descriptor) {
        return Accessor.getAnnotation(descriptor, FrozenKey.class) != null;
    }

    @Override
    public boolean isFrozenValue(AccessibleObject field) {
        return Accessor.getAnnotation(field, FrozenValue.class) != null;
    }

    @Override
    public boolean isFrozenValue(PropertyDescriptor descriptor) {
        return Accessor.getAnnotation(descriptor, FrozenValue.class) != null;
    }
}
