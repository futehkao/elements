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

@SuppressWarnings("squid:S1700")
public class Segments<E> {

    private Map<DataSet, Segments> dependents = new HashMap<>();
    private LinkedList<Segment<E>> segments = new LinkedList<>();
    private Catalyst catalyst;

    public Segments(Catalyst catalyst, Collection<Segment<E>> segments) {
        this.segments.addAll(segments);
        this.catalyst = catalyst;
    }

    public int size() {
        return segments.size();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public Segment<E> remove() {
        return segments.removeFirst();
    }

    public Collection<Segment<E>> remaining() {
        Collection<Segment<E>> remaining = segments;
        segments = new LinkedList<>();
        return remaining;
    }

    @SuppressWarnings("unchecked")
    public <T> Segments<T> from(DataSet<T> dataSet) {
        return dependents.computeIfAbsent(dataSet, d -> d.segment(catalyst));
    }

    public <T> Collection<Segment<T>> segment(DataSet<T> dataSet) {
        Segments<T> mySegments = from(dataSet);

        Collection<Segment<T>> s = new ArrayList<>();
        if (isEmpty()) {
            s.addAll(mySegments.remaining());
        } else {
            if (!mySegments.isEmpty()) {
                s.add(mySegments.remove());
            }
        }
        return s;
    }
}
