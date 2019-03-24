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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import net.e6tech.elements.cassandra.async.Async;

import java.util.ArrayList;
import java.util.List;


/**
 * This interface is designed to extract data from a Cassandra table to another Cassandra table.
 * The source table (class) must implement C4HourlyId.
 * the idea is to get distinct creation hours from the source table and import the data in chunks
 * based on order by id.
 *
 */
public class PartitionOrderByStrategy<S extends PartitionOrderBy> extends PartitionStrategy<S, PartitionOrderByContext> {

    @Override
    public List<S> extract(PartitionOrderByContext context) {
        Class<S> sourceClass = context.getSourceClass();
        PreparedStatement pstmt = context.getPreparedStatements().computeIfAbsent("extract",
                key -> context.getSession().prepare(context.getExtractionQuery()));
        Async async = context.createAsync(pstmt);
        for (Comparable partition : context.getPartitions()) {
            Comparable startId = context.getStartId(partition);
            Comparable endId = context.getEndId(partition);
            if (endId.compareTo(startId) > 0) {
                startId = endId;
                context.setStartId(partition, startId);
                async.execute(bound -> bound.set(context.getInspector().getPartitionKeyColumn(0), partition, (Class) partition.getClass())
                        .set(context.getInspector().getClusteringKeyColumn(0), context.getStartId(partition), (Class) partition.getClass()));
            }
        }

        List<S> list = new ArrayList<>();
        async.<ResultSet>inExecutionOrder(rs -> {
            List<S> subList = context.getMapper(sourceClass).map(rs).all();
            if (!subList.isEmpty()) {
                PartitionOrderBy last =  subList.get(subList.size() - 1);
                context.setEndId((Comparable) context.getInspector().getPartitionKey(last, 0), (Comparable) context.getInspector().getClusteringKey(last, 0));
                list.addAll(subList);
            }
        });
        return list;
    }

    @Override
    public int run(List<Comparable> partitions, PartitionOrderByContext context) {
        List<S> batchResults = null;
        int processedCount = 0;
        context.setPartitions(partitions);

        while (!(batchResults = extract(context)).isEmpty()) {
            processedCount += load(context, batchResults);
        }
        logger.info("Processed {} instance of {}", processedCount, context.extractor());
        return processedCount;
    }
}
