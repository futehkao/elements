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

package net.e6tech.elements.network.cluster.catalyst;

import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


public class Distinct<T, R> extends Series<T, R> {

    public Distinct() {
    }

    public Distinct(Series<T, R> other) {
        super(other);
    }

    public DataSet<R> distinct(Catalyst<? extends Reactor> catalyst, DataSet<T> dataSet) {
        // add a Transform that collects a stream into a set and then output the set's stream.
        add(((reactor, stream) -> stream.collect(Collectors.toSet()).stream()));
        DataSet<R> result = transform(catalyst, dataSet);

        Set<R> set = new HashSet<>();
        set.addAll(result.asCollection());
        return new CollectionDataSet<>(set);
    }
}
