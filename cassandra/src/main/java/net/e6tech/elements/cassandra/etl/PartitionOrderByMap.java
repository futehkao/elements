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
import net.e6tech.elements.common.resources.Provision;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This is a map that support storing entities with a SINGLE partition key and ONE or more clustering key.  However, only
 * first clustering key is used to sort the list for each partition key.
 */
public class PartitionOrderByMap<T extends PartitionOrderBy> {

    private Map<Comparable, List<T>> partitionMap = new LinkedHashMap<>();
    private Map<Comparable, List<PrimaryKey>> primaryKeys = new LinkedHashMap<>();
    private Provision provision;
    private Class sourceClass;

    public PartitionOrderByMap(Provision provision, Class sourceClass) {
        this.provision = provision;
        this.sourceClass = sourceClass;
    }

    public Inspector getInspector(Class cls) {
        return provision.getInstance(Sibyl.class).getInspector(cls);
    }

    public PartitionOrderByMap<T> addAll(Collection<T> objects) {
        for (T object : objects) {
            List<T> list = partitionMap.computeIfAbsent((Comparable) getInspector(sourceClass).getPartitionKey(object, 0), key -> new ArrayList<>());
            list.add(object);
        }

        // sort each list using its clustering key
        for (List<T> list : partitionMap.values()) {
            Collections.sort(list, Comparator.comparing(t -> (Comparable) getInspector(sourceClass).getClusteringKey(t, 0)));
        }

        // primary keys
        for (Map.Entry<Comparable, List<T>> entry : partitionMap.entrySet()) {
            List<PrimaryKey> list = primaryKeys.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
            for (T t : entry.getValue()) {
                list.add(getInspector(t.getClass()).getPrimaryKey(t));
            }
        }
        return this;
    }

    public PartitionOrderByMap<T> getValueList(Comparable partitionKey, Consumer<List<T>> consumer) {
        List<T> list = partitionMap.get(partitionKey);
        if (list != null)
            consumer.accept(list);
        return this;
    }

    public PartitionOrderByMap<T> getPrimaryKeyList(Comparable partitionKey, Consumer<List<PrimaryKey>> consumer) {
        List<PrimaryKey> list = primaryKeys.get(partitionKey);
        if (list != null)
            consumer.accept(list);
        return this;
    }

    public PartitionOrderByMap<T> forEachValueList(BiConsumer<Comparable, List<T>> consumer) {
        for (Map.Entry<Comparable, List<T>> entry : partitionMap.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public PartitionOrderByMap<T> forEachPrimaryKeyList(BiConsumer<Comparable, List<PrimaryKey>> consumer) {
        for (Map.Entry<Comparable, List<PrimaryKey>> entry : primaryKeys.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public PartitionOrderByMap<T> forEachValue(Consumer<T> consumer) {
        for (Map.Entry<Comparable, List<T>> entry : partitionMap.entrySet()) {
            for (T t : entry.getValue())
             consumer.accept(t);
        }
        return this;
    }

    public PartitionOrderByMap<T> forEachPrimaryKey(Consumer<PrimaryKey> consumer) {
        for (Map.Entry<Comparable, List<PrimaryKey>> entry : primaryKeys.entrySet()) {
            for (PrimaryKey t : entry.getValue())
                consumer.accept(t);
        }
        return this;
    }
}
