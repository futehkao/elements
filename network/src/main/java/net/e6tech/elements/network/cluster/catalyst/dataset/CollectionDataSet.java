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
import net.e6tech.elements.network.cluster.catalyst.Reactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CollectionDataSet<E> implements DataSet<E> {

    private List<Segment<E>> segments = new ArrayList<>();
    private Collection<E> dataSet;
    private int splitFactor = 1;

    public CollectionDataSet(Collection<E> dataSet) {
        this.dataSet = dataSet;
    }

    /**
     *
     * @param dataSet data
     * @param splitFactor determine addition rounds of splits.  For example, if it were 3, a segment for a node would
     *                    be split into 8 segments because 2^3 is 8.  Default is 1 so that each node ideally handles two
     *                    segments.
     */
    public CollectionDataSet(Collection<E> dataSet, int splitFactor) {
        this.dataSet = dataSet;
        this.splitFactor = splitFactor;
    }

    @Override
    public Segments<E> segment(Catalyst catalyst) {
        int routes = catalyst.getRegistry().routes(catalyst.getQualifier(), Reactor.class).size();
        int split = 0;
        if (routes > 1) {
            double ln = Math.log(routes) / Math.log(2);
            int whole = (int) ln;
            if (Math.pow(2, ln) - Math.pow(2, whole) > whole * .2) {
                whole++;
            }
            split = whole + splitFactor;
        }

        List<Spliterator<E>> tmp = new ArrayList<>();
        Spliterator<E> spliterator = dataSet.spliterator();
        List<Spliterator<E>> spliterators = new ArrayList<>();
        spliterators.add(spliterator);
        for (int i = 0; i < split; i++) {
            tmp.clear();
            for (Spliterator<E> segment : spliterators) {
                Spliterator<E> second = segment.trySplit();
                if (second != null) {
                    tmp.add(second);
                }
                tmp.add(segment);
            }
            spliterators.clear();
            spliterators.addAll(tmp);
        }
        segments.clear();
        for (Spliterator<E> s : spliterators) {
            List<E> list = StreamSupport.stream(s, false).collect(Collectors.toList());
            segments.add(reactor -> list.stream());
        }
        return new Segments<>(catalyst, segments);
    }

    @Override
    public Collection<E> asCollection() {
        return dataSet;
    }
}
