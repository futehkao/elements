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
import net.e6tech.elements.cassandra.driver.cql.Bound;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.common.reflection.ObjectConverter;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.TextBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class PartitionStrategy<S extends Partition, C extends PartitionContext> implements BatchStrategy<S, C> {

    @Override
    public int load(C context, List<S> source) {
        if (context.getLoadDelegate() != null) {
            return context.getLoadDelegate().applyAsInt(source);
        }
        return 0;
    }

    /**
     * Return a map of partitions and count.  The partitions are sorted from small to large.
     * @param context PartitionContext
     * @return map of partition anc count
     */
    /**
     * Return a map of partitions and count.  The partitions are sorted from small to large.
     * @param context PartitionContext
     * @return map of partition anc count
     */
    public Map<Comparable, Long> queryPartitions(C context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        Map<Comparable, Long> map = Collections.synchronizedMap(new TreeMap<>());
        List<Comparable> partitions = Collections.synchronizedList(new LinkedList<>());
        String query = TextBuilder.using(
                        "select ${pk}, count(*) from ${table} " +
                                "where ${pk} >= ${start} and ${pk} < ${end} group by ${pk} allow filtering")
                .build("pk", partitionKey, "table", table,
                        "start", lastUpdate.getLastUpdate(), "end", end);
        context.open().accept(Resources.class, res -> {
            try {
                ResultSet rs = res.getInstance(Session.class).execute(query);
                List<Row> rows = rs.all();
                for (Row row : rows) {
                    Comparable pk = (Comparable) row.get(0, context.getPartitionKeyType());
                    map.put(pk, row.get(1, Long.class));
                    partitions.add(pk);
                }
            } catch (Exception ex) {
                logger.warn("queryPartitions failed: " + query, ex);
                throw ex;

            }
        });

        Collections.sort(partitions);
        Map<Comparable, Long> result = new LinkedHashMap<>(partitions.size() + 1, 1.0f);
        for (Comparable partition : partitions) {
            result.put(partition, map.get(partition));
        }
        return result;
    }

    public Map<Comparable, Long> queryPartitions2(C context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        Map<Comparable, Long> map = Collections.synchronizedMap(new TreeMap<>());
        List<Comparable> partitions = Collections.synchronizedList(new LinkedList<>());
        String query2 = TextBuilder.using(
                        "select ${pk}, count(*) from ${table} " +
                                "where ${pk} >= :start and ${pk} < :end group by ${pk} allow filtering")
                .build("pk", partitionKey, "table", table);
        BigDecimal asyncStep = context.getAsyncTimeUnitStepSize() == null ? null : new BigDecimal(context.getAsyncTimeUnitStepSize());
        int retries = context.getRetries();
        if (retries < 0)
            retries = 0;
        ObjectConverter converter = new ObjectConverter();
        while (retries >= 0) {
            try {
                if (context.getTimeUnit() != null && asyncStep != null) {
                    BigDecimal from = new BigDecimal(lastUpdate.getLastUpdate());
                    BigDecimal to = new BigDecimal(context.getCutoff().toString());
                    if (from.compareTo(BigDecimal.ZERO) <= 0) {
                        BigDecimal min = context.open().apply(Resources.class, res -> {
                            String query = TextBuilder.using("select min(${pk}) from ${table}")
                                    .build("pk", partitionKey, "table", table);
                            try {
                                ResultSet rs = res.getInstance(Session.class).execute(query);
                                Comparable comparable = (Comparable) rs.one().get(0, context.getPartitionKeyType());
                                return new BigDecimal(comparable.toString());
                            } catch (Exception ex) {
                                return null;
                            }
                        });
                        if (min != null)
                            from = min;
                        else
                            from = new BigDecimal(context.getCutoff(System.currentTimeMillis(), context.getMaxPast()).toString());
                    }
                    List<Range> ranges = getAsyncSteps(from, to, asyncStep);
                    context.open().accept(Sibyl.class, sibyl -> {
                        sibyl.<Range>createAsync(query2)
                                .execute(ranges, (range, bound) -> {
                                    try {
                                    bound.set("start", converter.convert(range.start, context.getPartitionKeyType(), null), context.getPartitionKeyType());
                                    bound.set("end", converter.convert(range.end, context.getPartitionKeyType(), null), context.getPartitionKeyType());
                                    } catch (IOException ex) {
                                        throw new SystemException(ex);
                                    }
                                })
                                .inExecutionOrderRows(r -> {
                                    Row row = (Row) r;
                                    Comparable pk = (Comparable) row.get(0, context.getPartitionKeyType());
                                    map.put( pk, row.get(1, Long.class));
                                    partitions.add(pk);
                                });
                    });
                } else {
                    context.open().accept(Resources.class, res -> {
                        Prepared pstmt = res.getInstance(Session.class).prepare(query2);
                        try {
                            Bound bound = pstmt.bind();
                            bound.set("start", converter.convert(lastUpdate.getLastUpdate(), context.getPartitionKeyType(), null), context.getPartitionKeyType());
                            bound.set("end", converter.convert(end.toString(), context.getPartitionKeyType(), null), context.getPartitionKeyType());

                            ResultSet rs = res.getInstance(Session.class).execute(bound);
                            for (Row row : rs.all()) {
                                Comparable pk = (Comparable) row.get(0, context.getPartitionKeyType());
                                map.put(pk, row.get(1, Long.class));
                                partitions.add(pk);
                            }
                        } catch (Exception ex) {
                            logger.warn("queryPartitions failed: " + query2, ex);
                            throw ex;

                        }
                    });
                }
            }  catch (Exception ex) {
                String info = "extractor=" + context.extractor() +
                        " sourceClass=" + context.getSourceClass() +
                        " tableName=" + context.tableName();
                if (retries <= 0) {
                    logger.warn("Cannot transmutate " + query2, ex);
                    throw ex;
                } else {
                    logger.warn("Cannot transmutate " + query2 + ", " + retries + " retry attempts left, " + info, ex);
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

        Collections.sort(partitions);
        Map<Comparable, Long> result = new LinkedHashMap<>(partitions.size() + 1, 1.0f);
        for (Comparable partition : partitions) {
            result.put(partition, map.get(partition));
        }
        return result;
    }

    public Map<Comparable, Long> queryPartitions3(C context) {
        String table = context.tableName();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        Map<Comparable, Long> map = Collections.synchronizedMap(new TreeMap<>());
        List<Comparable> partitions = Collections.synchronizedList(new LinkedList<>());
        String query = TextBuilder.using(
                        "select ${pk}, count(*) from ${table} " +
                                "where ${pk} > :start and ${pk} < :end group by ${pk} allow filtering")
                .build("pk", partitionKey, "table", table);

        ObjectConverter converter = new ObjectConverter();
        incrementalQuery(context, query,
                (range, bound) -> {
                    try {
                        bound.set("start", converter.convert(range.start, context.getPartitionKeyType(), null), context.getPartitionKeyType());
                        bound.set("end", converter.convert(range.end, context.getPartitionKeyType(), null), context.getPartitionKeyType());
                    } catch (IOException ex) {
                        throw new SystemException(ex);
                    }
                }, row -> {
                    Comparable pk = (Comparable) row.get(0, context.getPartitionKeyType());
                    map.put(pk, row.get(1, Long.class));
                    partitions.add(pk);
                });

        Collections.sort(partitions);
        Map<Comparable, Long> result = new LinkedHashMap<>(partitions.size() + 1, 1.0f);
        for (Comparable partition : partitions) {
            result.put(partition, map.get(partition));
        }
        return result;
    }

    protected void incrementalQuery(C context, String query,
                                                 BiConsumer<Range, Bound> paramSetter,
                                                 Consumer<Row> rows) {
        LastUpdate lastUpdate = context.getLastUpdate();
        String start = lastUpdate.getLastUpdate();
        String end = context.getCutoff().toString();

        BigDecimal steps = context.getMaxTimeUnitStepSize() == null ? null : new BigDecimal(context.getMaxTimeUnitStepSize());
        if (steps.compareTo(BigDecimal.ZERO) <= 0)
            steps = null;
        boolean incremental = context.getTimeUnit() != null && steps != null;
        BigDecimal from = null;
        BigDecimal to = null;
        final BigDecimal stop = new BigDecimal(context.getCutoff().toString());
        if (incremental) {
            from = new BigDecimal(lastUpdate.getLastUpdate());
            if (from.compareTo(BigDecimal.ZERO) <= 0) {
                from = new BigDecimal(context.getCutoff(System.currentTimeMillis(), context.getMaxPast()).toString());
            }
            to = from.add(steps);

            if (to.compareTo(stop) > 0) {
                to = stop;
            }
            start = from.toPlainString();
            end = to.toPlainString();
        }

        while (true) {
            int retries = context.getRetries();
            while (retries >= 0) {
                try {
                    BigDecimal asyncStep = context.getAsyncTimeUnitStepSize() == null ? null : new BigDecimal(context.getAsyncTimeUnitStepSize());
                    if (asyncStep.compareTo(BigDecimal.ZERO) <= 0)
                        asyncStep = null;

                    if (from != null && to != null && asyncStep != null && to.subtract(from).longValue() > asyncStep.longValue()) {
                        List<Range> ranges = getAsyncSteps(from, to, asyncStep);
                        context.open().accept(Sibyl.class, sibyl -> {
                            sibyl.<Range>createAsync(query)
                                    .execute(ranges, paramSetter::accept)
                                    .inExecutionOrderRows(rows::accept);
                        });
                    } else {
                        String s = start;
                        String e = end;

                            context.open().accept(Resources.class, res -> {
                                Prepared pstmt = res.getInstance(Session.class).prepare(query);
                                try {
                                    Bound bound = pstmt.bind();
                                    paramSetter.accept(new Range(s, e), bound);
                                    ResultSet rs = res.getInstance(Session.class).execute(bound);
                                    for (Row row : rs.all()) {
                                        rows.accept(row);
                                    }
                                } catch (Exception ex) {
                                    logger.warn("queryPartitions failed: " + query, ex);
                                    throw ex;

                                }
                            });
                    }
                } catch (Exception ex) {
                    String info = "extractor=" + context.extractor() +
                            " sourceClass=" + context.getSourceClass() +
                            " tableName=" + context.tableName();
                    if (retries <= 0) {
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

            if (incremental) {
                if (to.compareTo(stop) >= 0)
                    break;

                from = to; // because ${pk} >= ${start} and ${pk} < ${end}, > and <
                to = from.add(steps);

                if (to.compareTo(stop) > 0) {
                    to = stop;
                }
                start = from.toPlainString();
                end = to.toPlainString();
            } else {
                break;
            }
        }
    }

    private List<Range> getAsyncSteps(BigDecimal from, BigDecimal to, BigDecimal asyncStepSize) {
        BigDecimal start = from;
        BigDecimal end = from.add(asyncStepSize);
        if (end.compareTo(to) > 0)
            end = to;
        List<Range> ranges = new LinkedList<>();
        while (true) {
            ranges.add(new Range(start.toPlainString(), end.toPlainString()));
            if (end.compareTo(to) >= 0)
                break;

            start = end; // because ${pk} > ${start} and ${pk} < ${end}, > and <
            end = start.add(asyncStepSize);

            if (end.compareTo(to) > 0)
                end = to;
        }
        return ranges;
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
        Map<Comparable, Long> partitions = queryPartitions(context);  // partition key vs count

        logger.info("Extracting Class {} to {}", context.getSourceClass(), getClass());
        context.reset();

        List<Comparable> concurrent = new LinkedList<>();
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

    public List<Comparable> queryRange(C context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        List<Comparable> list = Collections.synchronizedList(new LinkedList<>());
        // select distinct only works when selecting partition keys
        String query = TextBuilder.using(
                        "select distinct ${pk} from ${table} " +
                                "where ${pk} >= ${start} and ${pk} < ${end} allow filtering")
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

    public List<Comparable> queryRange2(C context) {
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        List<Comparable> list = Collections.synchronizedList(new LinkedList<>());

        String query = TextBuilder.using(
                        "select distinct ${pk} from ${table} " +
                                "where ${pk} >= :start and ${pk} <= :end allow filtering")
                .build("pk", partitionKey, "table", table);

        ObjectConverter converter = new ObjectConverter();
        incrementalQuery(context, query,
                (range, bound) -> {
                    try {
                        bound.set("start", converter.convert(range.start, context.getPartitionKeyType(), null), context.getPartitionKeyType());
                        bound.set("end", converter.convert(range.end, context.getPartitionKeyType(), null), context.getPartitionKeyType());
                    } catch (IOException ex) {
                        throw new SystemException(ex);
                    }
                }, row -> {
                    list.add((Comparable) row.get(0, context.getPartitionKeyType()));
                });

        list.sort(null);
        return list;
    }

    public int runPartitions(C context) {
        context.initialize();
        List<Comparable> list = queryRange(context);

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
        public String start;
        public String end;

        public Range(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }
}
