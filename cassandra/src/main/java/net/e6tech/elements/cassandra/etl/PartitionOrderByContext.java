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

package net.e6tech.elements.cassandra.etl;

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.TextBuilder;

import java.util.HashMap;
import java.util.Map;

public class PartitionOrderByContext extends PartitionContext {
    private Map<Comparable, Comparable> startIds = new HashMap<>();
    private Map<Comparable, Comparable> endIds = new HashMap<>();

    public String getExtractionQuery() {
        String clusteringKeyColumn = getInspector().getClusteringKeyColumn(0);
        String partitionKeyColumn = getInspector().getPartitionKeyColumn(0);
        return TextBuilder.using("select * from ${table} where ${pk} = :${pk} and ${ck} > :${ck} order by ${ck} asc limit ${batchSize}")
                .build("table", tableName(), "pk", partitionKeyColumn,
                        "ck", clusteringKeyColumn, "batchSize", getBatchSize());
    }

    @Override
    public PartitionOrderByStrategy createStrategy() {
        return new PartitionOrderByStrategy();
    }

    public Comparable getStartId(Comparable partition) {
        Comparable id = startIds.get(partition);
        if (id == null)
            id = 0L;
        return id;
    }

    public void setStartId(Comparable hour, Comparable id) {
        startIds.put(hour, id);
    }

    public Comparable getEndId(Comparable partition) {
        Comparable id = endIds.get(partition);
        if (id == null)
            id = 1L;
        return id;
    }

    public void setEndId(Comparable partition, Comparable id) {
        endIds.put(partition, id);
    }

    @Override
    public void reset() {
        super.reset();
        startIds.clear();
        endIds.clear();
    }

    @Override
    public PartitionOrderByContext run(Class<? extends PartitionStrategy> cls ) {
        if (PartitionOrderByStrategy.class.isAssignableFrom(cls)) {
            try {
                PartitionOrderByStrategy strategy = (PartitionOrderByStrategy) cls.getDeclaredConstructor().newInstance();
                setImportedCount(strategy.run(this));
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        } else {
            super.run(cls);
        }
        return this;
    }
}
