/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.network.cluster.catalyst.dataset;

import net.e6tech.elements.network.cluster.catalyst.Catalyst;

import java.util.*;
import java.util.function.Function;

public class SplitDataSet <E> implements DataSet<E> {
    private List<Segment<E>> segments = new ArrayList<>();

    public SplitDataSet() {
    }

    public SplitDataSet(Collection<Collection<E>> segments) {
        addAll(segments);
    }

    public SplitDataSet(Collection<E> entries, Function<E, Object> keyFunc) {
        Map<Object, Collection<E>> map = new HashMap<>(entries.size());
        for (E e : entries) {
            Collection<E> list = map.computeIfAbsent(keyFunc.apply(e), key -> new ArrayList<>());
            list.add(e);
        }
        addAll(map.values());
    }

    @SuppressWarnings("all")
    @SafeVarargs
    public SplitDataSet(Function<E, Object> keyFunc, E ... entries) {
        if (entries != null) {
            Map<Object, Collection<E>> map = new HashMap<>(entries.length);
            for (E e : entries) {
                Collection<E> list = map.computeIfAbsent(keyFunc.apply(e), key -> new ArrayList<>());
                list.add(e);
            }
            addAll(map.values());
        }
    }

    public SplitDataSet<E> add(Collection<E> collection) {
        segments.add(reactor -> collection.stream());
        return this;
    }

    public SplitDataSet<E> addAll(Collection<Collection<E>> segments) {
        if (this.segments.isEmpty())
            this.segments = new ArrayList<>(segments.size());
        for (Collection<E> c : segments) {
            add(c);
        }
        return this;
    }

    @Override
    public Segments<E> segment(Catalyst catalyst) {
        return new Segments<>(catalyst, segments);
    }

    @Override
    public Collection<E> asCollection() {
        return Collections.emptyList();
    }
}
