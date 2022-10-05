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

package net.e6tech.elements.network.cluster.catalyst.scalar;

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.cluster.catalyst.Mapping;
import net.e6tech.elements.network.cluster.catalyst.Reactor;
import net.e6tech.elements.network.cluster.catalyst.SerializableFunction;
import net.e6tech.elements.network.cluster.catalyst.transform.Series;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;

@SuppressWarnings({"squid:S00119", "squid:S2975"})
public class Scalar<Re extends Reactor, T, R, U> implements Cloneable, SerializableFunction<Re, U>, Serializable {
    private static final long serialVersionUID = 1676649613567136786L;
    private Series<Re, T, R> series;
    private Mapping<Re, Collection<R>, U> mapping;

    public Scalar() {
    }

    public Scalar(Mapping<Re, Collection<R>, U> mapping) {
        setMapping(mapping);
    }

    @SuppressWarnings("unchecked")
    public U apply(Re reactor) {
        Function<Re, Collection<R>> t = series;
        Collection<R> collection = t.apply(reactor);
        Mapping<Reactor, Collection<?>, U>  m = (Mapping) mapping;
        return m.apply(reactor, collection);
    }

    public Scalar<Re, T, R, U> clone() {
        try {
            return (Scalar) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new SystemException(e);
        }
    }

    public Scalar<Re, T, R, U> gatherer() {
        return clone();
    }

    public Series<Re, T, R> getSeries() {
        return series;
    }

    public void setSeries(Series<Re, T, R> series) {
        this.series = series;
    }

    public Mapping<Re, Collection<R>, U> getMapping() {
        return mapping;
    }

    public void setMapping(Mapping<Re, Collection<R>, U> mapping) {
        this.mapping = mapping;
    }
}
