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

import com.datastax.driver.mapping.annotations.Frozen;
import com.datastax.driver.mapping.annotations.FrozenKey;
import com.datastax.driver.mapping.annotations.FrozenValue;
import com.datastax.oss.driver.api.mapper.annotations.*;
import com.datastax.oss.driver.internal.mapper.processor.entity.BuiltInNameConversions;
import net.e6tech.elements.cassandra.driver.v3.GeneratorV3;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.reflection.Accessor;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import static com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention.SNAKE_CASE_INSENSITIVE;

public class GeneratorV4 extends Generator {

    private Generator v3 = new GeneratorV3();

    @Override
    public String toCassandraName(String javaPropertyName) {
        if (v3 != null)
            return v3.toCassandraName(javaPropertyName);

        return BuiltInNameConversions.toCassandraName(javaPropertyName, SNAKE_CASE_INSENSITIVE);
    }

    public Class<? extends Annotation> tableAnnotation() {
        if (v3 != null)
            return v3.tableAnnotation();
        return Entity.class;
    }

    public Annotation tableAnnotation(Class sourceClass) {
        if (v3 != null)
            return v3.tableAnnotation(sourceClass);

        return sourceClass.getAnnotation(Entity.class);
    }

    public String tableKeyspace(Class sourceClass) {
        if (v3 != null)
            return v3.tableKeyspace(sourceClass);

        Entity table = (Entity) sourceClass.getAnnotation(Entity.class);
        if (table != null)
            return table.defaultKeyspace();
        return null;
    }

    public String tableName(Class sourceClass) {
        if (v3 != null)
            return v3.tableName(sourceClass);

        Entity table = (Entity) sourceClass.getAnnotation(Entity.class);
        if (table == null)
            throw new IllegalArgumentException("Class " + sourceClass + " is not annotated with @Table");
        CqlName name = (CqlName) sourceClass.getAnnotation(CqlName.class);
        if (name != null)
            return name.value();
        return toCassandraName(sourceClass.getSimpleName());
    }

    public boolean hasColumnAnnotation(AccessibleObject field) {
        if (v3 != null)
            return v3.hasColumnAnnotation(field);

        return Accessor.getAnnotation(field, CqlName.class) != null;
    }

    public boolean hasColumnAnnotation(PropertyDescriptor desc) {
        if (v3 != null)
            return v3.hasColumnAnnotation(desc);

        return Accessor.getAnnotation(desc, CqlName.class) != null;
    }

    public String getColumnName(Field field) {
        if (v3 != null)
            return v3.getColumnName(field);

        CqlName column = Accessor.getAnnotation(field, CqlName.class);
        String columnName;
        if (column == null || column.value().isEmpty()) {
            columnName = toCassandraName(field.getName());
        } else {
            columnName = column.value();
        }
        return columnName;
    }

    public String getColumnName(PropertyDescriptor descriptor) {
        if (v3 != null)
            return v3.getColumnName(descriptor);

        CqlName column = Accessor.getAnnotation(descriptor, CqlName.class);
        String columnName;
        if (column == null || column.value().isEmpty()) {
            columnName = toCassandraName(descriptor.getName());
        } else {
            columnName = column.value();
        }
        return columnName;
    }

    public int partitionKeyIndex(AccessibleObject field) {
        if (v3 != null)
            return v3.partitionKeyIndex(field);

        PartitionKey annotation = Accessor.getAnnotation(field, PartitionKey.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public int partitionKeyIndex(PropertyDescriptor descriptor) {
        if (v3 != null)
            return v3.partitionKeyIndex(descriptor);

        PartitionKey annotation = Accessor.getAnnotation(descriptor, PartitionKey.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public int clusteringColumnIndex(AccessibleObject field) {
        if (v3 != null)
            return v3.clusteringColumnIndex(field);

        ClusteringColumn annotation = Accessor.getAnnotation(field, ClusteringColumn.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public int clusteringColumnIndex(PropertyDescriptor descriptor) {
        if (v3 != null)
            return v3.clusteringColumnIndex(descriptor);

        ClusteringColumn annotation = Accessor.getAnnotation(descriptor, ClusteringColumn.class);
        if (annotation != null)
            return annotation.value();
        else
            return -1;
    }

    public boolean isTransient(AccessibleObject field) {
        if (v3 != null)
            return v3.isTransient(field);

        return Accessor.getAnnotation(field, Transient.class) != null;
    }

    public boolean isTransient(PropertyDescriptor descriptor) {
        if (v3 != null)
            return v3.isTransient(descriptor);

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
