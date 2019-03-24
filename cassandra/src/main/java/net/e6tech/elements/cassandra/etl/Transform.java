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
import net.e6tech.elements.common.util.datastructure.Pair;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This is a convenient class for helping to transform an array of extracted entities of type E to another set of entities of
 * type T.
 *
 * @param <T> Transformed type
 * @param <E> Extracted type
 */
public class Transform<T, E> {
    private Map<PrimaryKey, T> map = new HashMap<>();
    private Class<T> tableClass;
    private ETLContext context;
    private Set<PrimaryKey> primaryKeys = new HashSet<>();
    private List<Pair<PrimaryKey, E>> entries = new ArrayList<>();

    public Transform(ETLContext context, Class<T> cls) {
        this.context = context;
        tableClass = cls;
    }

    public Transform<T, E> addPrimaryKey(PrimaryKey key, E e) {
        if (key == null)
            return this;
        primaryKeys.add(key);
        entries.add(new Pair<>(key, e));
        return this;
    }

    public Transform<T, E> load() {
        context.get(keys(), tableClass).inExecutionOrder(map::put);
        return this;
    }

    public Set<PrimaryKey> keys() {
        return primaryKeys;
    }

    public Collection<Pair<PrimaryKey, E>> entries() {
        return entries;
    }

    public Transform<T,E> put(PrimaryKey key, T t) {
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
                context.setPrimaryKey(key, t);
                if (consumer != null) {
                    consumer.accept(t);
                    context.setPrimaryKey(key, t);
                }
                return t;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        });
    }

    public Transform<T, E> forEachCreateIfNotExist(BiConsumer<E, T> consumer) {
        for (Pair<PrimaryKey, E> e : entries()) {
            T t = computeIfAbsent(e.key());
            consumer.accept(e.value(), t);
            checkpoint(e.value(), t);
        }
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
        Object extractionKey = context.getInspector(extraction.getClass()).getPartitionKey(extraction, 0);
        if (extractionKey == null)
            return;
        context.getInspector(tableClass).setCheckpoint(t, 0, extractionKey);
    }
}
