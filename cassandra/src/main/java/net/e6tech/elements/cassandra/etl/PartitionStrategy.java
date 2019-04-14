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
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.TextBuilder;

import java.util.*;

public class PartitionStrategy<S extends Partition, C extends PartitionContext> implements BatchStrategy<S, C> {

    @Override
    public int load(C context, List<S> source) {
        if (context.getLoadDelegate() != null) {
            return context.getLoadDelegate().apply(source);
        }
        return 0;
    }

    public Map<Comparable, Long> queryPartitions(C context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        Map<Comparable, Long> map = new HashMap<>();
        List<Comparable> partitions = new ArrayList<>();
        context.open().accept(Resources.class, res -> {
            String query = TextBuilder.using(
                    "select ${pk}, count(*) from ${table} " +
                    "where ${pk} > ${start} and ${pk} < ${end} group by ${pk} allow filtering")
                    .build("pk", partitionKey, "table", table,
                            "start", lastUpdate.getLastUpdate(), "end", end);
            ResultSet rs = res.getInstance(Session.class).execute(query);
            for (Row row : rs.all()) {
                Comparable pk = (Comparable) row.get(0, context.getPartitionKeyType());
                map.put(pk, row.getLong(1));
                partitions.add(pk);
            }
        });
        Collections.sort(partitions);
        Map<Comparable, Long> result = new LinkedHashMap<>();
        for (Comparable partition : partitions) {
            result.put(partition, map.get(partition));
        }
        return result;
    }

    @Override
    public List<S> extract(C context) {
        String query = TextBuilder.using("select * from ${table} where ${pk} = :partitionKey")
                .build("table", context.tableName(), "pk", context.getInspector().getPartitionKeyColumn(0));
        PreparedStatement pstmt = context.getPreparedStatements().computeIfAbsent("extract",
                key -> context.getSession().prepare(query));
        Async async = context.createAsync(pstmt);
        for (Comparable hour : context.getPartitions()) {
            async.execute(bound -> bound.set("partitionKey", hour, (Class) hour.getClass()));
        }
        List<S> list = new ArrayList<>();
        async.<ResultSet>inExecutionOrder(rs -> list.addAll(context.getMapper(context.getSourceClass()).map(rs).all()));
        return list;
    }

    @Override
    public int run(C context) {
        int importedCount = 0;
        context.initialize();
        Map<Comparable, Long> partitions = queryPartitions(context);  // partition key vs count

        logger.info("Extracting Class {} to {}", context.getSourceClass(), getClass());
        context.reset();

        List<Comparable> concurrent = new ArrayList<>();
        while (partitions.size() > 0) {
            LastUpdate lastUpdate = context.getLastUpdate();
            concurrent.clear();
            boolean first = true;
            long count = 0;
            for (Map.Entry<Comparable, Long> entry : partitions.entrySet()) {
                if (first) { // always add first regardless of batch size
                    concurrent.add(entry.getKey());
                    first = false;
                    count = entry.getValue();
                } else if (count + entry.getValue() <= context.getBatchSize()) {
                    count += entry.getValue();
                    concurrent.add(entry.getKey());
                } else {
                    break;
                }
            }

            for (Comparable partition : concurrent) {
                partitions.remove(partition);
                lastUpdate.update(partition);
            }
            importedCount += run(concurrent, context);
            context.saveLastUpdate(lastUpdate);
        }

        logger.info("Done loading {} instances of {}", importedCount, context.getSourceClass());
        context.reset();
        return importedCount;
    }

    public int run(List<Comparable> partitions, C context) {
        context.setPartitions(partitions);
        List<S> batchResults = extract(context);
        int processedCount = load(context, batchResults);
        if (logger.isInfoEnabled())
            logger.info("Processed {} instance of {}", processedCount, context.extractor());
        return processedCount;
    }
}
