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
import com.datastax.driver.mapping.Mapper;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.datastructure.Pair;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * This is a convenient class for helping to transform an array of extracted entities of type E to another set of entities of
 * type T.
 *
 * @param <T> Transformed type
 * @param <E> Extracted type
 */
public class Transformer<T, E> {
    private Map<PrimaryKey, T> map = new HashMap<>();
    private Class<T> tableClass;
    private Provision provision;
    private Set<PrimaryKey> primaryKeys = new HashSet<>();
    private List<Pair<PrimaryKey, E>> entries = new ArrayList<>();

    public Transformer(Provision provision, Class<T> cls) {
        this.provision = provision;
        tableClass = cls;
    }

    public Transformer<T,E> transform(Stream<E> stream, BiConsumer<Transformer<T, E>, E> consumer) {
        stream.forEach(e -> consumer.accept(this, e));
        return this;
    }

    public Transformer<T,E> transform(E[] array, BiConsumer<Transformer<T, E>, E> consumer) {
        for (E e : array) {
            consumer.accept(this, e);
        }
        return this;
    }

    public Transformer<T,E> transform(Collection<E> collection, BiConsumer<Transformer<T, E>, E> consumer) {
        for (E e : collection) {
            consumer.accept(this, e);
        }
        return this;
    }
    public Async createAsync() {
        return provision.newInstance(Async.class);
    }

    public Async createAsync(String query) {
        return provision.getInstance(Sibyl.class).createAsync(query);
    }

    public Async createAsync(PreparedStatement stmt) {
        return provision.getInstance(Sibyl.class).createAsync(stmt);
    }

    public Transformer<T, E> addPrimaryKey(PrimaryKey key, E e) {
        if (key == null)
            return this;
        primaryKeys.add(key);
        entries.add(new Pair<>(key, e));
        return this;
    }

    public Transformer<T, E> load() {
        Sibyl s = provision.getInstance(Sibyl.class);
        s.get(keys(), tableClass).inExecutionOrder(map::put);
        return this;
    }

    public Set<PrimaryKey> keys() {
        return primaryKeys;
    }

    public Collection<Pair<PrimaryKey, E>> entries() {
        return entries;
    }

    public Transformer<T,E> put(PrimaryKey key, T t) {
        map.put(key, t);
        return this;
    }

    public T computeIfAbsent(PrimaryKey key) {
        return computeIfAbsent(key, null);
    }

    public T computeIfAbsent(PrimaryKey key, Consumer<T> consumer) {
        return map.computeIfAbsent(key, k -> {
            try {
                T t = tableClass.getDeclaredConstructor().newInstance();
                setPrimaryKey(key, t);
                if (consumer != null) {
                    consumer.accept(t);
                    setPrimaryKey(key, t);
                }
                return t;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        });
    }

    private Inspector getInspector(Class cls) {
        return provision.getInstance(Sibyl.class).getInspector(cls);
    }

    private void setPrimaryKey(PrimaryKey primaryKey, T t) {
        getInspector(t.getClass()).setPrimaryKey(primaryKey, t);
    }

    public Transformer<T, E> forEachCreateIfNotExist(BiConsumer<E, T> consumer) {
        for (Pair<PrimaryKey, E> e : entries()) {
            T t = computeIfAbsent(e.key());
            consumer.accept(e.value(), t);
            checkpoint(e.value(), t);
        }
        return this;
    }

    public Transformer<T, E> save(Mapper.Option... options) {
        Sibyl s = provision.getInstance(Sibyl.class);
        s.save(values(), tableClass, options).inCompletionOrder();
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

    public void checkpoint(E extraction, T t) {
        Object extractionKey = getInspector(extraction.getClass()).getPartitionKey(extraction, 0);
        if (extractionKey == null)
            return;
        getInspector(tableClass).setCheckpoint(t, 0, extractionKey);
    }
}
