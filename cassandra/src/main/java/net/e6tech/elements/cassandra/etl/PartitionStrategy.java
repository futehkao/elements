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
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings({"unchecked", "java:S1192"})
/**
 * The default behavior is to use select ${pk} ... where ... allow filtering in one call.
 * However, if asyncTimeUnitStepSize is set it will break the query into separate queries.  In addition,
 * if asyncUseFutures is set to ture, each query is broken down into smaller queries.
 * See detailed explanation in execAsyncQuery2.
 *
 * ETLContext
 * context.setAsyncTimeUnitStepSize(100);
 * context.setAsyncUseFutures(true);
 */
public class PartitionStrategy<S extends Partition, C extends PartitionContext> implements BatchStrategy<S, C> {

    public static final String QUERY_PARTITION = "select ${pk}, count(*) from ${table} " +
            "where ${pk} > ${start} and ${pk} < ${end} group by ${pk} allow filtering";
    public static final String ASYNC_QUERY_PARTITION = "select ${pk}, count(*) from ${table} where ${pk} = :pk";
    public static final String QUERY_RANGE = "select distinct ${pk} from ${table} " +
            "where ${pk} > ${start} and ${pk} < ${end} allow filtering";
    public static final String ASYNC_QUERY_RANGE = "select ${pk} from ${table} where ${pk} = :pk";

    private ObjectConverter converter = new ObjectConverter();
    private String partitionTiming;

    @Override
    public int load(C context, List<S> source) {
        if (context.getLoadDelegate() != null) {
            return context.getLoadDelegate().applyAsInt(source);
        }
        return 0;
    }

    /**
     * Return a map of partitions and count.  The partitions are sorted from small to large.
     *
     * @param p PartitionQuery
     * @return map of partition anc count
     */
    public Map<Comparable<Object>, Long> queryPartitions(PartitionQuery<C> p) {
        long start = System.currentTimeMillis();
        String query = TextBuilder.using(QUERY_PARTITION)
                .build("pk", p.partitionKey, "table", p.table, "start", p.lastUpdate.getLastUpdate(), "end", p.end);
        Map<Comparable<Object>, Long> map = Collections.synchronizedMap(new TreeMap<>());
        List<Comparable<Object>> partitions = Collections.synchronizedList(new LinkedList<>());
        p.context.open().accept(Resources.class, res -> {
            try {
                ResultSet rs = res.getInstance(Session.class).execute(query);
                List<Row> rows = rs.all();
                for (Row row : rows) {
                    Comparable<Object> pk = (Comparable<Object>) row.get(0, p.context.getPartitionKeyType());
                    map.put(pk, row.get(1, Long.class));
                    partitions.add(pk);
                }
            } catch (Exception ex) {
                logger.warn("queryPartitions failed: " + query, ex);
                throw ex;
            }
        });
        Map<Comparable<Object>, Long> ret = sortPartitions(map, partitions);
        partitionTiming = "queryPartition for " + p.table + " took " + (System.currentTimeMillis() - start) + "ms";
        return ret;
    }

    public Map<Comparable<Object>, Long> queryPartitions2(PartitionQuery<C> p) {
        try {
            new BigDecimal(p.lastUpdate.getLastUpdate());
        } catch (Exception ex) {
            logger.warn("Cannot parse latUpdate " + p.lastUpdate.getLastUpdate() + " for " + p.lastUpdate.getExtractor());
            return queryPartitions(p);
        }

        if (p.context.getTimeUnit() != null && p.asyncStep != null && p.asyncStep > 0) {
            long start = System.currentTimeMillis();
            String query = buildQuery(p, QUERY_PARTITION, ASYNC_QUERY_PARTITION);
            Map<Comparable<Object>, Long> map = Collections.synchronizedMap(new TreeMap<>());
            List<Comparable<Object>> partitions = Collections.synchronizedList(new LinkedList<>());
            asyncQuery(p, query, row -> {
                Comparable<Object> pk = (Comparable<Object>) row.get(0, p.context.getPartitionKeyType());
                map.put(pk, row.get(1, Long.class));
                partitions.add(pk);
            });
            Map<Comparable<Object>, Long> ret = sortPartitions(map, partitions);
            partitionTiming = "queryPartition2 for " + p.table + " took " + (System.currentTimeMillis() - start) + "ms";
            return ret;
        } else {
            return queryPartitions(p);
        }
    }

    private String buildQuery(PartitionQuery<C> p, String query, String asyncQuery) {
        if (p.context.isAsyncUseFutures())
            return TextBuilder.using(asyncQuery).build("pk", p.partitionKey, "table", p.table);
        else
            return TextBuilder.using(query).build("pk", p.partitionKey, "table", p.table, "start", ":start", "end", ":end");

    }

    private Map<Comparable<Object>, Long> sortPartitions(Map<Comparable<Object>, Long> map, List<Comparable<Object>> partitions) {
        partitions.sort(null);
        Map<Comparable<Object>, Long> result = new LinkedHashMap<>(partitions.size() + 1, 1.0f);
        for (Comparable<Object> partition : partitions) {
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
        List<Range> ranges = getAsyncRanges(query, start, to, p.asyncStep, p.asyncMaxChunkSize);
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
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    retries--;
                }
            }
            Range lastRange = ranges.get(ranges.size() - 1);
            start = lastRange.end.subtract(BigDecimal.ONE);
            ranges = getAsyncRanges(query, start, to, p.asyncStep, p.asyncMaxChunkSize);
        }
    }

    // using select ${pk}, count(*) from ${table} where ${pk} > ${start} and ${pk} < ${end} group by ${pk} allow filtering
    protected void execAsyncQuery(C context, String query, List<Range> working, Consumer<Row> rowConsumer) {
        if (context.isAsyncUseFutures()) {
            execAsyncQuery2(context, query, working, rowConsumer);
            return;
        }
        context.open().accept(Sibyl.class, sibyl ->
                sibyl.<Range>createAsync(query)
                        .execute(working, (range, bound) -> {
                            try {
                                bound.set("start", converter.convert(range.start.toString(), context.getPartitionKeyType(), null), context.getPartitionKeyType());
                                bound.set("end", converter.convert(range.end.toPlainString(), context.getPartitionKeyType(), null), context.getPartitionKeyType());
                            } catch (IOException ex) {
                                throw new SystemException(ex);
                            }
                        })
                        .inExecutionOrderRows(rowConsumer)
        );
    }

    // instead of using select ${pk}, count(*) from ${table} where ${pk} > ${start} and ${pk} < ${end} group by ${pk} allow filtering
    // this method break the query into a number of queries of the form
    // select ${pk}, count(*) from ${table} where ${pk} = :pk, where pk is start, start + 1, start +2 ... end.
    // asyncStepSize controls the chunkSize and should be around 100.
    protected void execAsyncQuery2(C context, String query, List<Range> working, Consumer<Row> rowConsumer) {
        for (Range r : working) {
            List<BigDecimal> list = new LinkedList<>();
            for (BigDecimal i = r.start.add(BigDecimal.ONE); i.compareTo(r.end) < 0; i = i.add(BigDecimal.ONE))
                list.add(i);

            context.open().accept(Sibyl.class, sibyl ->
                    sibyl.<BigDecimal>createAsync(query)
                            .execute(list, (pk, bound) -> {
                                try {
                                    bound.set("pk", converter.convert(pk.toPlainString(), context.getPartitionKeyType(), null), context.getPartitionKeyType());
                                } catch (IOException ex) {
                                    throw new SystemException(ex);
                                }
                            })
                            .inExecutionOrderRows(row -> {
                                if (!row.isNull(0))
                                    rowConsumer.accept(row);
                            })
            );
        }
    }

    // Breaks from and to into a list ranges.  Each range has a start and its end is start + asyncStepSize (or smaller).
    // The asyncMaxChunkSize is used to limit the size of the list.  Caller will call this method over and over
    // again until the list is empty.
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
            logger.warn("async range error for {}, first {} ", query, first);

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
            for (Comparable<?> hour : context.getPartitions()) {
                async.execute(bound -> bound.set("partitionKey", hour, (Class) hour.getClass()));
            }
            List<S> list = new ArrayList<>();
            async.inExecutionOrder(rs -> list.addAll(sibyl.mapAll(context.getSourceClass(), rs)));
            return list;
        });
    }

    @Override
    public int run(C context) {
        long start = System.currentTimeMillis();
        int importedCount = 0;
        context.initialize();
        Map<Comparable<Object>, Long> partitions;  // partition key vs count
        PartitionQuery<C> p = new PartitionQuery<>(context);
        partitions = queryPartitions2(p);

        context.reset();

        List<Comparable<?>> concurrent = new LinkedList<>();
        boolean empty = partitions.isEmpty();
        while (partitions.size() > 0) {
            LastUpdate lastUpdate = context.getLastUpdate();
            concurrent.clear();
            boolean first = true;
            long count = 0;
            for (Map.Entry<Comparable<Object>, Long> entry : partitions.entrySet()) {
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

            int processedCount = run(context, concurrent);
            importedCount += processedCount;
            if (logger.isInfoEnabled())
                logger.info("Batch loaded {} instances of {}", processedCount, context.extractor());
            for (Comparable<?> partition : concurrent) {
                partitions.remove(partition);
                lastUpdate.update(partition);
            }
            context.saveLastUpdate(lastUpdate);
        }

        if (context.getTimeUnit() != null && empty && context.isAsyncUseFutures()) {
            LastUpdate lastUpdate = context.getLastUpdate();
            BigDecimal from = new BigDecimal(lastUpdate.getLastUpdate());
            BigDecimal to = new BigDecimal(p.context.getCutoff().toString());
            if (to.compareTo(from) > 0) {
                BigDecimal currentTimeInTimeUnit = timeExpressedInCorrectTimeUnit(context.getTimeUnit(), System.currentTimeMillis());
                if (to.compareTo(currentTimeInTimeUnit) > 0)
                    lastUpdate.update(currentTimeInTimeUnit.subtract(BigDecimal.ONE));
                else
                    lastUpdate.update(to.subtract(BigDecimal.ONE));
                context.saveLastUpdate(lastUpdate);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("{}.run for {} loaded {} instances of {} took {}ms, {}",
                    getClass().getSimpleName(), context.extractor(),
                    importedCount, context.getSourceClass().getSimpleName(),
                    System.currentTimeMillis() - start, partitionTiming
            );
        }
        context.reset();
        return importedCount;
    }

    public BigDecimal timeExpressedInCorrectTimeUnit(TimeUnit timeUnit, long time) {
        BigDecimal timeToConvert = new BigDecimal(time);
        if (timeUnit == TimeUnit.DAYS)
            return timeToConvert.divide(new BigDecimal(ETLContext.DAY), 0, RoundingMode.DOWN);
        else if (timeUnit == TimeUnit.HOURS)
            return timeToConvert.divide(new BigDecimal(ETLContext.HOUR), 0, RoundingMode.DOWN);
        else if (timeUnit == TimeUnit.MINUTES)
            return timeToConvert.divide(new BigDecimal(ETLContext.MINUTE), 0, RoundingMode.DOWN);
        else if (timeUnit == TimeUnit.SECONDS)
            return timeToConvert.divide(new BigDecimal(ETLContext.SECOND), 0, RoundingMode.DOWN);
        return timeToConvert;
    }

    public int run(C context, List<Comparable<?>> partitions) {
        context.setPartitions(partitions);
        List<S> batchResults = extract(context);
        return load(context, batchResults);
    }

    public List<Comparable<Object>> queryRange(PartitionQuery<C> p) {
        long start = System.currentTimeMillis();
        C context = p.context;
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable<?> end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        List<Comparable<Object>> list = Collections.synchronizedList(new LinkedList<>());
        // select distinct only works when selecting partition keys
        String query = TextBuilder.using(QUERY_RANGE)
                .build("pk", partitionKey, "table", table,
                        "start", lastUpdate.getLastUpdate(), "end", end);
        context.open().accept(Resources.class, res -> {
            try {
                ResultSet rs = res.getInstance(Session.class).execute(query);
                for (Row row : rs.all()) {
                    list.add((Comparable<Object>) row.get(0, context.getPartitionKeyType()));
                }
            } catch (Exception ex) {
                logger.warn("PartitionStrategy.queryRange failed: " + query, ex);
                throw ex;
            }
        });

        list.sort(null);
        partitionTiming = "queryRange for " + p.table + " took " + (System.currentTimeMillis() - start) + "ms";
        return list;
    }

    public List<Comparable<Object>> queryRange2(PartitionQuery<C> p) {
        try {
            new BigDecimal(p.lastUpdate.getLastUpdate());
        } catch (Exception ex) {
            logger.warn("Cannot parse latUpdate " + p.lastUpdate.getLastUpdate() + " for " + p.lastUpdate.getExtractor());
            return queryRange(p);
        }

        if (p.context.getTimeUnit() != null && p.asyncStep != null && p.asyncStep > 0) {
            long start = System.currentTimeMillis();
            List<Comparable<Object>> list = Collections.synchronizedList(new LinkedList<>());
            String query = buildQuery(p, QUERY_RANGE, ASYNC_QUERY_RANGE);
            asyncQuery(p, query, row -> list.add((Comparable<Object>) row.get(0, p.context.getPartitionKeyType())));
            list.sort(null);
            partitionTiming = "queryRange2 for " + p.table + " took " + (System.currentTimeMillis() - start) + "ms";
            return list;
        } else {
            return queryRange(p);
        }
    }

    public int runPartitions(C context) {
        long start = System.currentTimeMillis();
        context.initialize();
        PartitionQuery<C> p = new PartitionQuery<>(context);
        List<Comparable<Object>> list = queryRange2(p);

        List<Comparable<?>> batch = new ArrayList<>(context.getBatchSize());
        int processCount = 0;
        for (Comparable<?> c : list) {
            batch.add(c);
            if (batch.size() == context.getBatchSize()) {
                LastUpdate lastUpdate = context.getLastUpdate();
                processCount += runPartitions(batch, context);
                lastUpdate.update(batch.get(batch.size() - 1));
                context.saveLastUpdate(lastUpdate);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            LastUpdate lastUpdate = context.getLastUpdate();
            processCount += runPartitions(batch, context);
            lastUpdate.update(batch.get(batch.size() - 1));
            context.saveLastUpdate(lastUpdate);
            batch.clear();
        }

        if (logger.isInfoEnabled()) {
            logger.info("{}.runPartitions for {} loaded {} instances of {} took {}ms, {}",
                    getClass().getSimpleName(), context.extractor(),
                    processCount, context.getSourceClass().getSimpleName(),
                    System.currentTimeMillis() - start, partitionTiming
            );
        }
        return list.size();
    }

    public int runPartitions(List<Comparable<?>> list, C context) {
        context.setPartitions(list);
        return context.getLoadDelegate().applyAsInt(list);
    }

    public static class Range {
        BigDecimal start;
        BigDecimal end;

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
        C context;
        LastUpdate lastUpdate;
        Comparable<?> end;
        String partitionKey;
        String table;
        int retries;
        Integer asyncStep;
        Integer asyncMaxChunkSize;

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
