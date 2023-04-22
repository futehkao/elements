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


import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.AsyncPrepared;
import net.e6tech.elements.cassandra.driver.cql.Prepared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This interface is designed to extract data from a Cassandra table to another Cassandra table.
 * The source table (class) must implement C4HourlyId.
 * the idea is to get distinct creation hours from the source table and import the data in chunks
 * based on order by id.
 *
 */
@SuppressWarnings("unchecked")
public class PartitionOrderByStrategy<S extends PartitionOrderBy> extends PartitionStrategy<S, PartitionOrderByContext> {

    /**
     *
     * @param context PartitionOrderByContext
     * @return a list of data ordered by partition key.  It is very important that the list is ordered
     * by the partition key so that in case of failures the system can recover correctly.
     */
    @SuppressWarnings("squid:S3776")
    @Override
    public List<S> extract(PartitionOrderByContext context) {
        return context.open().apply(Sibyl.class, sibyl -> {
            Map<Comparable, List<S>> results = new HashMap<>((int)(context.getPartitions().size() * 1.4 + 16));
            Class<S> sourceClass = context.getSourceClass();
            Prepared pstmt = context.getPreparedStatements().computeIfAbsent("extract",
                key -> sibyl.getSession().prepare(context.getExtractionQuery()));
            AtomicInteger total = new AtomicInteger(0);
            while (true) {
                AsyncPrepared<?> async = sibyl.createAsync(pstmt);
                for (Comparable partition : context.getPartitions()) {
                    Comparable startId = context.getStartId(partition);
                    Comparable endId = context.getEndId(partition);
                    if (endId.compareTo(startId) > 0) {
                        startId = endId;
                        context.setStartId(partition, startId);
                        async.execute(bound ->
                                bound.set(context.getInspector().getPartitionKeyColumn(0), partition, (Class) partition.getClass())
                                        .set(context.getInspector().getClusteringKeyColumn(0), context.getStartId(partition), (Class) partition.getClass()));
                    }
                }

                int before = total.get();
                async.inExecutionOrder(rs -> {
                    List<S> subList = sibyl.mapAll(sourceClass, rs);
                    if (!subList.isEmpty()) {
                        PartitionOrderBy last = subList.get(subList.size() - 1);
                        Comparable partition = (Comparable) context.getInspector().getPartitionKey(last, 0);
                        context.setEndId(partition, (Comparable) context.getInspector().getClusteringKey(last, 0));
                        List<S> list = results.computeIfAbsent(partition, key -> new ArrayList<>());
                        list.addAll(subList);
                        total.addAndGet(subList.size());
                    }
                });

                if (before == total.get() || total.get() >= context.getBatchSize())
                    break;
            }

            List<S> list = new ArrayList<>(total.get());
            for (Comparable partition : context.getPartitions()) {
                List<S> subList = results.get(partition);
                if (subList != null)
                    list.addAll(subList);
            }
            results.clear();
            return list;
        });
    }

    @Override
    public int run(PartitionOrderByContext context, List<Comparable<?>> partitions) {
        List<S> batchResults = null;
        int processedCount = 0;
        context.setPartitions(partitions);

        batchResults = extract(context);

        while (!batchResults.isEmpty()) {
            processedCount += load(context, batchResults);
            batchResults = extract(context);
        }

        return processedCount;
    }
}
