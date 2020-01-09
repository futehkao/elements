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

import net.e6tech.elements.cassandra.annotations.Checkpoint;
import net.e6tech.elements.cassandra.annotations.Index;
import net.e6tech.elements.cassandra.annotations.Indexes;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class IndexGenerator extends AbstractGenerator{
    private Map<String, Checkpoint> implicitIndexes = new LinkedHashMap<>();
    private Set<String> partitionKeys = new HashSet<>();
    private Map<String, Index> indexes = new LinkedHashMap<>();

    @SuppressWarnings({"unchecked", "squid:CommentedOutCodeLine", "squid:S3626", "squid:S3776", "squid:S135"})
    IndexGenerator(Generator generator, Class entityClass) throws IntrospectionException {
        super(generator);
        LinkedList<Class> classHierarchy = analyze(entityClass);
        Set<String> transientNames = new HashSet<>(50);

        for (Class cls : classHierarchy) {
            Indexes indexList = (Indexes) cls.getAnnotation(Indexes.class);
            if (indexList != null) {
                for (Index index : indexList.value()) {
                    if (index.name().isEmpty()) {
                        indexes.put(index.column() + "_idx", index);
                    } else {
                        indexes.put(index.name(), index);
                    }
                }
            }
        }

        for (Class cls : classHierarchy) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStrict(field.getModifiers()))
                    continue;
                if (generator.isTransient(field)) {
                    transientNames.add(field.getName());
                    continue;
                }

                int pk = generator.partitionKeyIndex(field);
                if (pk >= 0) {
                    partitionKeys.add(generator.getColumnName(field));
                    partitionKeys.add(field.getName());
                    continue;
                }

                /*
                Checkpoint checkpoint = field.getAnnotation(Checkpoint.class);
                if (checkpoint != null) {
                    implicitIndexes.put(generator.getColumnName(column, field), checkpoint);
                    implicitIndexes2.put(field.getName(), generator.getColumnName(column, field));
                } */
            }
        }

        for (PropertyDescriptor desc : Introspector.getBeanInfo(entityClass).getPropertyDescriptors()) {
            Method method = null;
            if (desc.getReadMethod() != null)
                method = desc.getReadMethod();

            if (method == null)
                throw new IllegalArgumentException("Entity class does not have a get method for property " + desc.getName());
            if (!desc.getName().equals("class")) {
                if (generator.isTransient(desc)) {
                    transientNames.add(desc.getName());
                    continue;
                }

                if (transientNames.contains(desc.getName()))
                    continue;

                int pk = generator.partitionKeyIndex(desc);
                if (pk >= 0) {
                    partitionKeys.add(generator.getColumnName(desc));
                    partitionKeys.add(desc.getName());
                    continue;
                }

                if (partitionKeys.contains(desc.getName())
                    || partitionKeys.contains(generator.getColumnName(desc)))
                    continue;

                /*
                Checkpoint checkpoint = method.getAnnotation(Checkpoint.class);
                if (checkpoint != null) {
                    implicitIndexes.remove(generator.getColumnName(column, method));
                    String columnName = implicitIndexes2.get(desc.getName());
                    implicitIndexes.remove(columnName);
                    implicitIndexes.put(generator.getColumnName(column, method), checkpoint);
                } */
            }
        }
    }

    public List<String> generate() {
        List<String> statements = new ArrayList<>();
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            Index index = entry.getValue();
            builder.append("CREATE INDEX IF NOT EXISTS ");
            builder.append(getTableName()).append("__").append(entry.getKey());
            builder.append(" ON ");
            builder.append(fullyQualifiedTableName());
            builder.append("(");
            if (index.keys()) {
                builder.append("KEYS(");
            }
            builder.append(index.column());
            if (index.keys()) {
                builder.append(")");
            }
            builder.append(")");
            statements.add(builder.toString());
            builder.setLength(0);
        }

        for (String column : implicitIndexes.keySet()) {
            builder.append("CREATE CUSTOM INDEX IF NOT EXISTS ");
            builder.append(getTableName()).append("__").append(column).append("_idx");
            builder.append(" ON ");
            builder.append(fullyQualifiedTableName());
            builder.append("(");
            builder.append(column);
            builder.append(") USING 'org.apache.cassandra.index.sasi.SASIIndex' WITH OPTIONS = {'mode' : 'SPARSE'} ");
            statements.add(builder.toString());
            builder.setLength(0);
        }

        return statements;
    }
}
