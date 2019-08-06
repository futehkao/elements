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

package net.e6tech.elements.cassandra.driver.metadata;

import java.util.LinkedList;
import java.util.List;

public class AbstractTableMetadata implements TableMetadata {
    private String name;
    private List<ColumnMetadata> columns = new LinkedList<>();
    private List<ColumnMetadata> primaryKey = new LinkedList<>();
    private List<ColumnMetadata> partitionKey = new LinkedList<>();
    private List<ColumnMetadata> clusteringColumns = new LinkedList<>();

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMetadata> columns) {
        this.columns = columns;
    }

    @Override
    public List<ColumnMetadata> getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(List<ColumnMetadata> primaryKey) {
        this.primaryKey = primaryKey;
    }

    @Override
    public List<ColumnMetadata> getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(List<ColumnMetadata> partitionKey) {
        this.partitionKey = partitionKey;
    }

    @Override
    public List<ColumnMetadata> getClusteringColumns() {
        return clusteringColumns;
    }

    public void setClusteringColumns(List<ColumnMetadata> clusteringColumns) {
        this.clusteringColumns = clusteringColumns;
    }
}
