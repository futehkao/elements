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

import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Transient;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

public class TableGenerator extends AbstractGenerator {

    private Map<String, ColumnGenerator> columnGenerators = new LinkedHashMap<>();
    private Map<String, String> columnGenerators2 = new LinkedHashMap<>();
    private List<KeyColumn> clusterKeys = new ArrayList<>();
    private List<KeyColumn> partitionKeys = new ArrayList<>();

    @SuppressWarnings({"squid:S3776", "squid:S135"})
    TableGenerator(Generator generator, Class entityClass) throws IntrospectionException {
        super(generator);
        LinkedList<Class> classHierarchy = analyze(entityClass);

        for (Class cls : classHierarchy) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                Column column = field.getAnnotation(Column.class);
                Transient trans = field.getAnnotation(Transient.class);
                if (trans != null)
                    continue;

                PartitionKey pk = field.getAnnotation(PartitionKey.class);
                if (pk != null)
                    partitionKeys.add(new KeyColumn(generator.getColumnName(column, field), pk.value()));
                ClusteringColumn cc = field.getAnnotation(ClusteringColumn.class);
                if (cc != null)
                    clusterKeys.add(new KeyColumn(generator.getColumnName(column, field), cc.value()));
                ColumnGenerator fieldGen = new ColumnGenerator(generator, field.getGenericType(), new AnnotatedTypeDescriptor(generator, field));
                columnGenerators.put(generator.getColumnName(column, field), fieldGen);
                columnGenerators2.put(field.getName(), generator.getColumnName(column, field));
            }
        }

        for (PropertyDescriptor desc : Introspector.getBeanInfo(entityClass).getPropertyDescriptors()) {
            Method method = null;
            Type type = null;
            if (desc.getReadMethod() != null) {
                method = desc.getReadMethod();
                type = method.getGenericReturnType();
            }

            if (method != null && !method.getName().equals("getClass")) {
                Transient trans = method.getAnnotation(Transient.class);
                if (trans != null)
                    continue;
                Column column = method.getAnnotation(Column.class);
                PartitionKey pk = method.getAnnotation(PartitionKey.class);
                if (pk != null)
                    partitionKeys.add(new KeyColumn(generator.getColumnName(column, method), pk.value()));
                ClusteringColumn cc = method.getAnnotation(ClusteringColumn.class);
                if (cc != null)
                    clusterKeys.add(new KeyColumn(generator.getColumnName(column, method), cc.value()));

                ColumnGenerator colGen = columnGenerators.get(generator.getColumnName(column, method));
                if (colGen == null) {
                    String columnName = columnGenerators2.get(desc.getName());
                    if (columnName != null) {
                        colGen = columnGenerators.get(columnName);
                        if (colGen != null) {
                            columnGenerators.remove(columnName);
                        }
                    }
                }
                AnnotatedTypeDescriptor fieldTypeDescriptor = (colGen != null) ? (AnnotatedTypeDescriptor) colGen.getTypeDescriptor() : null;
                ColumnGenerator methGen = new ColumnGenerator(generator, type, new AnnotatedTypeDescriptor(generator, method, fieldTypeDescriptor));
                columnGenerators.put(generator.getColumnName(column, method), methGen);
            }
        }
    }

    public String generate() {
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TABLE IF NOT EXISTS ");
        builder.append(fullyQualifiedTableName());
        builder.append(" (\n");
        for (ColumnGenerator gen : columnGenerators.values()) {
            builder.append(gen.generate());
            builder.append(",\n");
        }

        Collections.sort(partitionKeys, Comparator.comparingInt(KeyColumn::getPosition));
        Collections.sort(clusterKeys, Comparator.comparingInt(KeyColumn::getPosition));
        boolean first = true;
        builder.append("PRIMARY KEY (");
        if (partitionKeys.size() > 1)
            builder.append("(");
        for (KeyColumn pk : partitionKeys) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(pk.getName());
        }
        if (partitionKeys.size() > 1)
            builder.append(")");

        for (KeyColumn cc : clusterKeys) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(cc.getName());
        }
        builder.append(")\n");
        builder.append(")");
        return builder.toString();
    }
}
