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

import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.AsyncPrepared;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.common.reflection.ObjectConverter;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.TextBuilder;
import net.e6tech.elements.common.util.datastructure.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class PartitionStrategy<S extends Partition, C extends PartitionContext> implements BatchStrategy<S, C> {

    private ObjectConverter converter = new ObjectConverter();

    @Override
    public int load(C context, List<S> source) {
        if (context.getLoadDelegate() != null) {
            return context.getLoadDelegate().applyAsInt(source);
        }
        return 0;
    }

    /**
     * Return a map of partitions and count.  The partitions are sorted from small to large.
     * @param p PartitionQuery
     * @return map of partition anc count
     */
    public Map<Comparable, Long> queryPartitions(PartitionQuery<C> p) {

        String query = TextBuilder.using(
                        "select ${pk}, count(*) from ${table} " +
                                "where ${pk} > ${start} and ${pk} < ${end} group by ${pk} allow filtering")
                .build("pk", p.partitionKey, "table", p.table,
                        "start", p.lastUpdate.getLastUpdate(), "end", p.end);
        Map<Comparable, Long> map = Collections.synchronizedMap(new TreeMap<>());
        List<Comparable> partitions = Collections.synchronizedList(new LinkedList<>());
        p.context.open().accept(Resources.class, res -> {
            try {
                ResultSet rs = res.getInstance(Session.class).execute(query);
                List<Row> rows = rs.all();
                for (Row row : rows) {
                    Comparable pk = (Comparable) row.get(0, p.context.getPartitionKeyType());
                    map.put(pk, row.get(1, Long.class));
                    partitions.add(pk);
                }
            } catch (Exception ex) {
                logger.warn("queryPartitions failed: " + query, ex);
                throw ex;

            }
        });
        return sortPartitions(map, partitions);
    }

    public Map<Comparable, Long> queryPartitions2(PartitionQuery<C> p) {
        try {
            new BigDecimal(p.lastUpdate.getLastUpdate());
        } catch (Exception ex) {
            logger.warn("Cannot parse latUpdate " + p.lastUpdate.getLastUpdate() + " for " + p.lastUpdate.getExtractor());
            return queryPartitions(p);
        }

        if (p.context.getTimeUnit() != null && p.asyncStep != null && p.asyncStep > 0) {
            String query2 = TextBuilder.using(
                            "select ${pk}, count(*) from ${table} " +
                                    "where ${pk} > :start and ${pk} < :end group by ${pk} allow filtering")
                    .build("pk", p.partitionKey, "table", p.table);

            Map<Comparable, Long> map = Collections.synchronizedMap(new TreeMap<>());
            List<Comparable> partitions = Collections.synchronizedList(new LinkedList<>());
            asyncQuery(p, query2, row -> {
                Comparable pk = (Comparable) row.get(0, p.context.getPartitionKeyType());
                map.put(pk, row.get(1, Long.class));
                partitions.add(pk);
            });
            return sortPartitions(map, partitions);
        } else {
            return queryPartitions(p);
        }
    }

    private Map<Comparable, Long> sortPartitions( Map<Comparable, Long> map, List<Comparable> partitions) {
        Collections.sort(partitions);
        Map<Comparable, Long> result = new LinkedHashMap<>(partitions.size() + 1, 1.0f);
        for (Comparable partition : partitions) {
            result.put(partition, map.get(partition));
        }
        return result;
    }

    private Pair<BigDecimal, BigDecimal> fromAndTo(PartitionQuery<C> p) {
        BigDecimal from = new BigDecimal(p.lastUpdate.getLastUpdate());
        BigDecimal to = new BigDecimal(p.context.getCutoff().toString());
        if (from.compareTo(BigDecimal.ZERO) <= 0) {
            from = new BigDecimal(p.context.getCutoff(System.currentTimeMillis(), p.context.getMaxPast()).toString());
        }

        return new Pair<>(from, to);
    }

    protected void asyncQuery(PartitionQuery<C> p, String query, Consumer<Row> rowConsumer) {
        C context = p.context;
        Pair<BigDecimal, BigDecimal> fromTo = fromAndTo(p);
        BigDecimal from = fromTo.key();
        BigDecimal to = fromTo.value();
        BigDecimal start = from;
        List<Range> ranges = getAsyncRanges(query, start, to, p.asyncStep,  p.asyncMaxChunkSize);
        while (!ranges.isEmpty()) {
            List<Range> working = ranges;
            int retries = p.retries;
            while (retries >= 0) {
                try {
                    execAsyncQuery(p.context, query, working, rowConsumer);
                    break;
                } catch (Exception ex) {
                    String info = "extractor=" + context.extractor() +
                            " sourceClass=" + context.getSourceClass() +
                            " tableName=" + context.tableName();
                    if (retries == 0) {
                        logger.warn("Cannot transmutate " + query, ex);
                        throw ex;
                    } else {
                        logger.warn("Cannot transmutate " + query + ", " + retries + " retry attempts left, " + info, ex);
                    }
                    try {
                        Thread.sleep(context.getRetrySleep());
                    } catch (InterruptedException e) {
                        // ignore
                    }
                } finally {
                    retries --;
                }
            }
            Range lastRange = ranges.get(ranges.size() - 1);
            start = lastRange.end.subtract(BigDecimal.ONE);
            ranges = getAsyncRanges(query, start, to, p.asyncStep,  p.asyncMaxChunkSize);
        }
    }

    protected void execAsyncQuery(C context, String query, List<Range> working, Consumer<Row> rowConsumer) {
        context.open().accept(Sibyl.class, sibyl -> {
            sibyl.<Range>createAsync(query)
                    .execute(working, (range, bound) -> {
                        try {
                            bound.set("start", converter.convert(range.start.toString(), context.getPartitionKeyType(), null), context.getPartitionKeyType());
                            bound.set("end", converter.convert(range.end.toPlainString(), context.getPartitionKeyType(), null), context.getPartitionKeyType());
                        } catch (IOException ex) {
                            throw new SystemException(ex);
                        }
                    })
                    .inExecutionOrderRows(rowConsumer);
        });
    }

    protected List<Range> getAsyncRanges(String query, BigDecimal from, BigDecimal to, int asyncStepSize, int asyncMaxChunkSize) {
        BigDecimal start = from;
        BigDecimal end = from.add(new BigDecimal(asyncStepSize));
        if (end.compareTo(to) > 0)
            end = to;
        if (end.subtract(start).compareTo(BigDecimal.ONE) <= 0)
            return new ArrayList<>();

        List<Range> ranges = new LinkedList<>();
        boolean loopEntered = false;
        while (true) {
            if (!loopEntered) {
                loopEntered = true;
            } else if (end.compareTo(to) >= 0 || ranges.size() >= asyncMaxChunkSize)
                break;
            ranges.add(new Range(start, end));

            start = end.subtract(BigDecimal.ONE); // because ${pk} > ${start} and ${pk} < ${end}, > and <
            end = start.add(new BigDecimal(asyncStepSize));

            if (end.compareTo(to) > 0)
                end = to;
        }

        Range first = ranges.get(0);
        if (from.compareTo(first.start) != 0)
            logger.warn("async range error for " + query + ", first " + first);

        return new ArrayList<>(ranges);
    }

    @Override
    public List<S> extract(C context) {
        return context.open().apply(Sibyl.class, sibyl -> {
            String query = TextBuilder.using("select * from ${tbl} where ${pk} = :partitionKey")
                    .build("tbl", context.tableName(), "pk", context.getInspector().getPartitionKeyColumn(0));
            Prepared pstmt = context.getPreparedStatements().computeIfAbsent("extract",
                    key -> sibyl.getSession().prepare(query));
            AsyncPrepared<?> async = sibyl.createAsync(pstmt);
            for (Comparable hour : context.getPartitions()) {
                async.execute(bound -> bound.set("partitionKey", hour, (Class) hour.getClass()));
            }
            List<S> list = new ArrayList<>();
            async.inExecutionOrder(rs -> list.addAll(sibyl.mapAll(context.getSourceClass(), rs)));
            return list;
        });
    }

    @Override
    public int run(C context) {
        int importedCount = 0;
        context.initialize();
        Map<Comparable, Long> partitions;  // partition key vs count
        PartitionQuery<C> p = new PartitionQuery<>(context);
        partitions = queryPartitions2(p);

        logger.info("Extracting Class {} to {}", context.getSourceClass(), getClass());
        context.reset();

        List<Comparable> concurrent = new LinkedList<>();
        boolean empty = partitions.isEmpty();
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

            importedCount += run(context, concurrent);
            for (Comparable partition : concurrent) {
                partitions.remove(partition);
                lastUpdate.update(partition);
            }
            context.saveLastUpdate(lastUpdate);
        }

        if (context.getTimeUnit() != null && empty) {
            LastUpdate lastUpdate = context.getLastUpdate();
            BigDecimal from = new BigDecimal(lastUpdate.getLastUpdate());
            BigDecimal to = new BigDecimal(p.context.getCutoff().toString());
            if (to.compareTo(from) > 0) {
                lastUpdate.update(to.subtract(BigDecimal.ONE));
                context.saveLastUpdate(lastUpdate);
            }
        }

        logger.info("Done loading {} instances of {}", importedCount, context.getSourceClass());
        context.reset();
        return importedCount;
    }

    public int run(C context, List<Comparable> partitions) {
        context.setPartitions(partitions);
        List<S> batchResults = extract(context);
        int processedCount = load(context, batchResults);
        if (logger.isInfoEnabled())
            logger.info("Processed {} instance of {}", processedCount, context.extractor());
        return processedCount;
    }

    public List<Comparable> queryRange(PartitionQuery<C> p) {
        C context = p.context;
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        List<Comparable> list = Collections.synchronizedList(new LinkedList<>());
        // select distinct only works when selecting partition keys
        String query = TextBuilder.using(
                        "select distinct ${pk} from ${table} " +
                                "where ${pk} > ${start} and ${pk} < ${end} allow filtering")
                .build("pk", partitionKey, "table", table,
                        "start", lastUpdate.getLastUpdate(), "end", end);
        context.open().accept(Resources.class, res -> {
            try {
                ResultSet rs = res.getInstance(Session.class).execute(query);
                for (Row row : rs.all()) {
                    list.add((Comparable) row.get(0, context.getPartitionKeyType()));
                }
            } catch (Exception ex) {
                logger.warn("queryRange failed: " + query, ex);
                throw ex;
            }
        });

        list.sort(null);
        return list;
    }

    public List<Comparable> queryRange2(PartitionQuery<C> p) {
        try {
            new BigDecimal(p.lastUpdate.getLastUpdate());
        } catch (Exception ex) {
            logger.warn("Cannot parse latUpdate " + p.lastUpdate.getLastUpdate() + " for " + p.lastUpdate.getExtractor());
            return queryRange(p);
        }

        if (p.context.getTimeUnit() != null && p.asyncStep != null && p.asyncStep > 0) {
            List<Comparable> list = Collections.synchronizedList(new LinkedList<>());
            String query2 = TextBuilder.using(
                            "select distinct ${pk} from ${table} " +
                                    "where ${pk} > :start and ${pk} < :end allow filtering")
                    .build("pk", p.partitionKey, "table", p.table);
            asyncQuery(p, query2, row -> list.add((Comparable) row.get(0, p.context.getPartitionKeyType())));
            list.sort(null);
            return list;
        } else {
            return queryRange(p);
        }
    }

    public int runPartitions(C context) {
        context.initialize();
        PartitionQuery<C> p = new PartitionQuery<>(context);
        List<Comparable> list = queryRange2(p);

        List<Comparable> batch = new ArrayList<>(context.getBatchSize());
        for (Comparable c : list) {
            batch.add(c);
            if (batch.size() == context.getBatchSize()) {
                LastUpdate lastUpdate = context.getLastUpdate();
                runPartitions(batch, context);
                lastUpdate.update(batch.get(batch.size() - 1));
                context.saveLastUpdate(lastUpdate);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            LastUpdate lastUpdate = context.getLastUpdate();
            runPartitions(batch, context);
            lastUpdate.update(batch.get(batch.size() - 1));
            context.saveLastUpdate(lastUpdate);
            batch.clear();
        }
        return list.size();
    }

    public void runPartitions(List<Comparable> list, C context) {
        context.setPartitions(list);
        int processCount = context.getLoadDelegate().applyAsInt(list);
        if (logger.isInfoEnabled())
            logger.info("Processed {} partitions of {}", processCount, context.extractor());
    }

    public static class Range {
        public BigDecimal start;
        public BigDecimal end;

        public Range(BigDecimal start, BigDecimal end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "range start= " + start + " end=" + end;
        }
    }

    public static class PartitionQuery<C extends PartitionContext> {
        public C context;
        public LastUpdate lastUpdate;
        public Comparable end;
        public String partitionKey;
        public String table;
        public int retries;
        public Integer asyncStep;
        public Integer asyncMaxChunkSize;

        public PartitionQuery(C context) {
            this.context = context;
            lastUpdate = context.getLastUpdate();
            end = context.getCutoff();
            partitionKey = context.getInspector().getPartitionKeyColumn(0);
            table = context.tableName();
            asyncStep = context.getAsyncTimeUnitStepSize();

            if (context.getAsyncMaxNumOfChunks() == null)
                asyncMaxChunkSize = ETLContext.ASYNC_MAX_NUM_OF_CHUNKS;
            else
                asyncMaxChunkSize = context.getAsyncMaxNumOfChunks();
            retries = context.getRetries();
            if (retries < 0)
                retries = 0;
        }
    }
}
