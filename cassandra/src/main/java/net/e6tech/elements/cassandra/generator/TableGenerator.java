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

import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.cassandra.driver.metadata.ColumnMetadata;
import net.e6tech.elements.cassandra.driver.metadata.TableMetadata;
import net.e6tech.elements.common.logging.Logger;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

public class TableGenerator extends AbstractGenerator {

    private static Logger logger = Logger.getLogger();

    private Map<String, ColumnGenerator> columnGenerators = new LinkedHashMap<>();
    private List<KeyColumn> clusterKeys = new ArrayList<>();
    private List<KeyColumn> partitionKeys = new ArrayList<>();

    @SuppressWarnings({"squid:S3776", "squid:S135"})
    TableGenerator(Generator generator, Class entityClass) throws IntrospectionException {
        super(generator);
        LinkedList<Class> classHierarchy = analyze(entityClass);
        Map<String, String> columnGenerators2 = new LinkedHashMap<>();
        Set<String> transientNames = new HashSet<>(50);

        for (Class cls : classHierarchy) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                if (generator.isTransient(field)) {
                    transientNames.add(field.getName());
                    continue;
                }

                int pk = generator.partitionKeyIndex(field);
                if (pk >= 0)
                    partitionKeys.add(new KeyColumn(generator.getColumnName(field), pk));
                int cc = generator.clusteringColumnIndex(field);
                if (cc >= 0)
                    clusterKeys.add(new KeyColumn(generator.getColumnName(field), cc));
                ColumnGenerator fieldGen = new ColumnGenerator(generator, field.getGenericType(), new AnnotatedTypeDescriptor(generator, field));
                columnGenerators.put(generator.getColumnName(field), fieldGen);
                columnGenerators2.put(field.getName(), generator.getColumnName(field));
            }
        }

        for (PropertyDescriptor desc : Introspector.getBeanInfo(entityClass).getPropertyDescriptors()) {
            Method method = null;
            Type type = null;
            if (desc.getReadMethod() != null) {
                method = desc.getReadMethod();
                type = method.getGenericReturnType();
            }
            if (type == null && desc.getWriteMethod() != null) {
                method = desc.getWriteMethod();
                type = method.getGenericParameterTypes()[0];
            }

            if (method != null && !method.getName().equals("getClass")) {
                if (generator.isTransient(desc)) {
                    transientNames.add(desc.getName());
                    continue;
                }

                if (transientNames.contains(desc.getName()))
                    continue;

                int pk = generator.partitionKeyIndex(desc);
                if (pk >= 0)
                    partitionKeys.add(new KeyColumn(generator.getColumnName(desc), pk));
                int cc = generator.clusteringColumnIndex(desc);
                if (cc >= 0)
                    clusterKeys.add(new KeyColumn(generator.getColumnName(desc), cc));

                ColumnGenerator colGen = columnGenerators.get(generator.getColumnName(desc));
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
                ColumnGenerator methGen = new ColumnGenerator(generator, type, new AnnotatedTypeDescriptor(generator, desc, fieldTypeDescriptor));
                columnGenerators.put(generator.getColumnName(desc), methGen);
            }
        }
    }

    public Map<String, ColumnGenerator> columns() {
        return columnGenerators;
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

    public void diff(Session session, String keyspace, TableMetadata tableMetadata, boolean dropColumns) {
        logger.info("Diff table " + fullyQualifiedTableName());
        List<ColumnMetadata> columns = tableMetadata.getColumns();
        Map<String, ColumnGenerator> toAdd = new LinkedHashMap<>();
        Map<String, ColumnMetadata> toRemove = new LinkedHashMap<>();
        for (ColumnGenerator gen : columnGenerators.values()) {
            toAdd.put(gen.getTypeDescriptor().getColumnName().toLowerCase(), gen);
        }
        for (ColumnMetadata metadata : columns) {
            toRemove.put(metadata.getName().toLowerCase(), metadata);
        }

        for (ColumnMetadata meta : columns) {
            toAdd.remove(meta.getName().toLowerCase());
        }

        for (ColumnGenerator gen : columnGenerators.values()) {
            toRemove.remove(gen.getTypeDescriptor().getColumnName().toLowerCase());
        }

        if (toAdd.isEmpty() && toRemove.isEmpty())
            return;

        StringBuilder builder = new StringBuilder();
        for (ColumnGenerator col : toAdd.values()) {
            builder.append("ALTER TABLE ");
            builder.append(fullyQualifiedTableName());
            builder.append(" ADD ");
            builder.append(col.generate());
            session.execute(keyspace, builder.toString());
            builder.setLength(0);
        }

        if (dropColumns) {
            for (ColumnMetadata col : toRemove.values()) {
                builder.append("ALTER TABLE ");
                builder.append(fullyQualifiedTableName());
                builder.append(" DROP ");
                builder.append(col.getName());
                session.execute(keyspace, builder.toString());
                builder.setLength(0);
            }
        }
    }
}
