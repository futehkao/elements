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

import net.e6tech.elements.cassandra.driver.metadata.AbstractTableMetadata;
import net.e6tech.elements.cassandra.generator.Generator;

public class TableMetadataV4 extends AbstractTableMetadata {

    public TableMetadataV4(Generator generator,  com.datastax.oss.driver.api.core.metadata.schema.TableMetadata metadata) {
        for (com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata column : metadata.getColumns().values()) {
            getColumns().add(new ColumnMetadataV4(generator, column));
        }

        for (com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata column : metadata.getPrimaryKey()) {
            getPrimaryKey().add(new ColumnMetadataV4(generator, column));
        }

        for (com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata column : metadata.getPartitionKey()) {
            getPartitionKey().add(new ColumnMetadataV4(generator, column));
        }

        for (com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata column : metadata.getClusteringColumns().keySet()) {
            getClusteringColumns().add(new ColumnMetadataV4(generator, column));
        }
    }
}
