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

package net.e6tech.elements.network.cluster.catalyst.transform;

import net.e6tech.elements.network.cluster.catalyst.Reactor;
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segment;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segments;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Union<Re extends Reactor, T> implements Transform<Re, T, T> {

    private transient DataSet<T> dataSet;
    private Collection<Segment<T>> segments = new ArrayList<>();

    public Union(DataSet<T> dataSet) {
        this.dataSet = dataSet;
    }

    protected Union(Collection<Segment<T>> segments) {
        this.segments = segments;
    }

    @Override
    public Stream<T> transform(Re reactor, Stream<T> stream) {
        Set<T> set = new HashSet<>();
        for (Segment<T> segment : segments) {
            segment.stream(reactor).collect(Collectors.toCollection(() -> set));
        }
        List<T> union = new ArrayList<>(set);
        Iterator<T> iterator = stream.iterator();
        while (iterator.hasNext()) {
            T t = iterator.next();
            if (!set.contains(t)) {
                union.add(t);
            }
        }
        return union.stream();
    }

    @Override
    public Union<Re, T> allocate(Segments<?> root) {
        return new Union<>(root.segment(dataSet));
    }
}
