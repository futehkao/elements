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

import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;

public class Scalar<T, R> implements Function<Reactor, R>, Serializable {
    private static final long serialVersionUID = 1676649613567136786L;
    private Series<T, R> series;
    private Mapping<? extends Reactor, Collection<R>, R> mapping;

    public Scalar(Series<T, R> series, Mapping< ? extends Reactor, Collection<R>, R> mapping) {
        this.series = series;
        this.mapping = mapping;
    }

    public R apply(Reactor reactor) {
        Function<Reactor, Collection<R>> t = series;
        Collection<R> collection = t.apply(reactor);
        Mapping<Reactor, Collection<?>, R>  m = (Mapping) mapping;
        return m.apply(reactor, collection);
    }

    public <S extends Reactor> R scalar(Catalyst<S> catalyst, DataSet<T> dataSet) {
        Mapping<S, Collection<R>, R> m = (Mapping) mapping;
        return catalyst.scalar(dataSet, series, m);
    }
}
