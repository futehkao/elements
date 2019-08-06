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

import net.e6tech.elements.cassandra.*;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.datastructure.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * This is a convenient class for helping to transform an array of extracted entities of type E to another set of entities of
 * type T.
 *
 * NOTE, the entities to be transformed need to be in ascending order.
 *
 * @param <T> Transformed type
 * @param <E> Extracted type
 */
public class Transformer<T, E> {
    private Resources resources;
    private Map<PrimaryKey, T> map = new HashMap<>();
    private Class<T> tableClass;
    private List<Pair<PrimaryKey, E>> entries = Collections.synchronizedList(new LinkedList<>());
    private boolean hasCheckpoint;
    private Inspector tableInspector;
    private Consistency readConsistency = null;
    private Consistency writeConsistency = null;
    private long timeout = 0;  // ie disable

    public Transformer(Resources resources, Class<T> cls) {
        this.resources = resources;
        tableClass = cls;
        tableInspector = getInspector(tableClass);
        hasCheckpoint = tableInspector.getCheckpointColumn(0) != null;
    }

    public Transformer<T,E> transform(Stream<E> stream, BiConsumer<Transformer<T, E>, E> consumer) {
        stream.forEach(e -> consumer.accept(this, e));
        load();
        return this;
    }

    public Transformer<T,E> transform(E[] array, BiConsumer<Transformer<T, E>, E> consumer) {
        List<CompletableFuture> asyncList = new LinkedList<>();
        for (E e : array) {
            asyncList.add(CompletableFuture.runAsync(() ->  consumer.accept(this, e)));
        }
        for (CompletableFuture future : asyncList)
            future.join();
        asyncList.clear();
        load();
        return this;
    }

    public Transformer<T,E> transform(Collection<E> collection, BiConsumer<Transformer<T, E>, E> consumer) {
        for (E e : collection) {
            consumer.accept(this, e);
        }
        load();
        return this;
    }

    public Consistency getReadConsistency() {
        return readConsistency;
    }

    public void setReadConsistency(Consistency readConsistency) {
        this.readConsistency = readConsistency;
    }

    public Consistency getWriteConsistency() {
        return writeConsistency;
    }

    public void setWriteConsistency(Consistency writeConsistency) {
        this.writeConsistency = writeConsistency;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Transformer<T, E> timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    public Transformer<T, E> addPrimaryKey(PrimaryKey key, E e) {
        if (key == null)
            return this;

        int keyColumns = tableInspector.getPartitionKeySize() + tableInspector.getClusteringKeySize();
        if (keyColumns != key.getKeys().length) {
            throw new IllegalArgumentException("Mismatch key columns.  The primary key for " + tableClass + " consists of " +  keyColumns + " column(s): " +
                    "the provided primary key has " + key.getKeys().length + " componenets.");
        }

        entries.add(new Pair<>(key, e));
        return this;
    }

    private Transformer<T, E> load() {
        Sibyl s = resources.getInstance(Sibyl.class);
        map = new HashMap<>(Math.max((int) (entries.size()/.75f) + 1, 16));
        Set<PrimaryKey> keys = new HashSet<>(Math.max((int) (entries.size()/.75f) + 1, 16));
        for (Pair<PrimaryKey, E> e : entries()) {
            keys.add(e.key());
        }

        ReadOptions readOptions = new ReadOptions();
        readOptions.consistency = readConsistency;
        s.get(keys, tableClass, readOptions)
                    .timeout(timeout)
                    .inExecutionOrder(map::put);
        return this;
    }

    private Collection<Pair<PrimaryKey, E>> entries() {
        return entries;
    }

    private Inspector getInspector(Class cls) {
        return resources.getInstance(SessionProvider.class).getInspector(cls);
    }

    private T computeIfAbsent(PrimaryKey key) {
        return map.computeIfAbsent(key, k -> {
            try {
                T t = tableClass.getDeclaredConstructor().newInstance();
                tableInspector.setPrimaryKey(key, t);
                return t;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        });
    }

    @SuppressWarnings("squid:S3776")
    public Transformer<T, E> forEachCreateIfNotExist(BiConsumer<E, T> consumer) {
        Inspector extractedInspector = null;
        List<CompletableFuture> asyncList = new LinkedList<>();
        for (Pair<PrimaryKey, E> e : entries()) {
            T t = computeIfAbsent(e.key());
            E extracted = e.value();
            if (extractedInspector == null)
                extractedInspector = getInspector(extracted.getClass());

            boolean duplicate = false;
            if (hasCheckpoint) {
                Comparable extractedPartitionKey = (Comparable) extractedInspector.getPartitionKey(extracted, 0);
                if (extractedPartitionKey != null) {
                    Comparable checkPoint = tableInspector.getCheckpoint(t, 0);
                    // extracted partition key need to be larger that checkpoint.  Otherwise, it means
                    // it is a duplicate because of failure conditions.
                    duplicate = checkPoint != null && extractedPartitionKey.compareTo(checkPoint) <= 0;
                }
            }
            if (!duplicate) {
                asyncList.add(CompletableFuture.runAsync(() -> consumer.accept(extracted, t)));
            }
        }

        for (CompletableFuture future : asyncList)
            future.join();
        asyncList.clear();

        // update checkpoints.
        for (Pair<PrimaryKey, E> e : entries()) {
            E extracted = e.value();
            if (hasCheckpoint) {
                T t = computeIfAbsent(e.key());
                Comparable extractedPartitionKey = (Comparable) extractedInspector.getPartitionKey(extracted, 0);
                if (extractedPartitionKey != null)
                    tableInspector.setCheckpoint(t, 0, extractedPartitionKey);
            }
        }
        return this;
    }

    public Transformer<T, E> save() {
        return save(null);
    }

    public Transformer<T, E> save(WriteOptions userOptions) {
        WriteOptions options = WriteOptions.from(userOptions);

        if (options.consistency == null && writeConsistency != null) {
            options.consistency = writeConsistency;
        }

        Sibyl s = resources.getInstance(Sibyl.class);

        s.save(values(), tableClass, options)
                .timeout(timeout)
                    .inExecutionOrder();

        return this;
    }

    public Collection<T> values() {
        return map.values();
    }

    public Set<PrimaryKey> keySet() {
        return map.keySet();
    }

    public int size() {
        return map.size();
    }
}
