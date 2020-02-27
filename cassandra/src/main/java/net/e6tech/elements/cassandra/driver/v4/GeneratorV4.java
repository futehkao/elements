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

package net.e6tech.elements.cassandra.driver.v4;

import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention;
import com.datastax.oss.driver.internal.mapper.processor.entity.BuiltInNameConversions;
import net.e6tech.elements.cassandra.annotations.*;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.reflection.Accessor;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.function.IntConsumer;

import static com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention.SNAKE_CASE_INSENSITIVE;

public class GeneratorV4 extends Generator {

    private NamingConvention namingConvention = SNAKE_CASE_INSENSITIVE;

    public NamingConvention getNamingConvention() {
        return namingConvention;
    }

    public void setNamingConvention(NamingConvention namingConvention) {
        this.namingConvention = namingConvention;
    }

    @Override
    public String toCassandraName(String javaPropertyName) {
        return BuiltInNameConversions.toCassandraName(javaPropertyName, namingConvention);
    }

    @Override
    public Class<? extends Annotation> tableAnnotation() {
        return Table.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Annotation tableAnnotation(Class sourceClass) {
        return sourceClass.getAnnotation(Table.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String tableKeyspace(Class sourceClass) {
        Table table = (Table) sourceClass.getAnnotation(Table.class);
        if (table != null)
            return table.keyspace();
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String tableName(Class sourceClass) {
        Table table = (Table) sourceClass.getAnnotation(Table.class);
        if (table == null)
            throw new IllegalArgumentException("Class " + sourceClass + " is not annotated with @Table");
        return (table.name() == null) ? toCassandraName(sourceClass.getSimpleName()) : table.name();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String tableCompression(Class sourceClass) {
        Table table = (Table) sourceClass.getAnnotation(Table.class);
        if (table != null)
            return table.compression();
        return null;
    }

    @Override
    public boolean hasColumnAnnotation(AccessibleObject field) {
        return Accessor.getAnnotation(field, Column.class) != null;
    }

    @Override
    public boolean hasColumnAnnotation(PropertyDescriptor desc) {
        return Accessor.getAnnotation(desc, Column.class) != null;
    }

    @Override
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

    @Override
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

    @Override
    public boolean partitionKeyIndex(AccessibleObject field, IntConsumer consumer) {
        PartitionKey annotation = Accessor.getAnnotation(field, PartitionKey.class);
        if (annotation != null)
           consumer.accept(annotation.value());
        return annotation != null;
    }

    @Override
    public boolean partitionKeyIndex(PropertyDescriptor descriptor, IntConsumer consumer) {
        PartitionKey annotation = Accessor.getAnnotation(descriptor, PartitionKey.class);
        if (annotation != null)
            consumer.accept(annotation.value());
        return annotation != null;
    }

    @Override
    public boolean clusteringColumnIndex(AccessibleObject field, IntConsumer consumer) {
        ClusteringColumn annotation = Accessor.getAnnotation(field, ClusteringColumn.class);
        if (annotation != null)
            consumer.accept(annotation.value());
        return annotation != null;
    }

    @Override
    public boolean clusteringColumnIndex(PropertyDescriptor descriptor, IntConsumer consumer) {
        ClusteringColumn annotation = Accessor.getAnnotation(descriptor, ClusteringColumn.class);
        if (annotation != null)
            consumer.accept(annotation.value());
        return annotation != null;
    }

    @Override
    public boolean isTransient(AccessibleObject field) {
        return Accessor.getAnnotation(field, Transient.class) != null;
    }

    @Override
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
