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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableGenerator extends AbstractGenerator {

    private static Logger logger = Logger.getLogger();
    private TableAnalyzer analyzer;
    private Map<String, ColumnGenerator> columnGenerators = new LinkedHashMap<>();

    TableGenerator(Generator generator, Class entityClass) throws IntrospectionException {
        super(generator);
        analyze(entityClass);
        analyzer = new TableAnalyzer(generator, entityClass);
        analyzer.getColumns().forEach((columnName, column) -> {
            if (column.getPropertyDescriptor() != null) {
                AnnotatedTypeDescriptor typeDescriptor = new AnnotatedTypeDescriptor(generator, column.getPropertyDescriptor());
                if (column.getField() != null) {
                    typeDescriptor.setParent(new AnnotatedTypeDescriptor(generator, column.getField()));
                }
                columnGenerators.put(columnName, new ColumnGenerator(generator, column.getPropertyDescriptor(), column.getType(), typeDescriptor));
            } else {
                columnGenerators.put(columnName, new ColumnGenerator(generator, column.getField(), column.getField().getGenericType(), new AnnotatedTypeDescriptor(generator, column.getField())));
            }
        });
    }

    public List<KeyColumn> getPartitionKeys() {
        return analyzer.getPartitionKeys();
    }

    public List<KeyColumn> getClusteringKeys() {
        return analyzer.getClusteringKeys();
    }

    public Map<String, ColumnGenerator> getColumns() {
        return columnGenerators;
    }

    public String generate() {
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TABLE IF NOT EXISTS ")
                .append(fullyQualifiedTableName())
                .append(" (\n");
        for (ColumnGenerator gen : getColumns().values()) {
            builder.append(gen.generate());
            builder.append(",\n");
        }

        boolean first = true;
        builder.append("PRIMARY KEY (");
        if (analyzer.getPartitionKeys().size() > 1)
            builder.append("(");
        for (KeyColumn pk : analyzer.getPartitionKeys()) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("\"" + pk.getName() + "\"");
        }
        if (analyzer.getPartitionKeys().size() > 1)
            builder.append(")");

        for (KeyColumn cc : analyzer.getClusteringKeys()) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("\"" + cc.getName() + "\"");
        }
        builder.append(")\n")
                .append(") ")
                .append(getTableCompression());

        return builder.toString();
    }

    public void diff(Session session, String keyspace, TableMetadata tableMetadata, boolean dropColumns) {
        if (logger.isInfoEnabled())
            logger.info("Diff table {}", fullyQualifiedTableName());
        List<ColumnMetadata> columns = tableMetadata.getColumns();
        Map<String, ColumnGenerator> toAdd = new LinkedHashMap<>();
        Map<String, ColumnMetadata> toRemove = new LinkedHashMap<>();
        for (ColumnGenerator gen : getColumns().values()) {
            toAdd.put(gen.getTypeDescriptor().getColumnName().toLowerCase(), gen);
        }
        for (ColumnMetadata meta : columns) {
            toRemove.put(meta.getName().toLowerCase(), meta);
        }

        for (ColumnMetadata meta : columns) {
            toAdd.remove(meta.getName().toLowerCase());
        }

        for (ColumnGenerator gen : getColumns().values()) {
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
                builder.append("\"" + col.getName() + "\"");
                session.execute(keyspace, builder.toString());
                builder.setLength(0);
            }
        }
    }
}
