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

import net.e6tech.elements.common.util.SystemException;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;

public class Scalar<Re extends Reactor, T, R> implements Cloneable, Function<Re, R>, Serializable {
    private static final long serialVersionUID = 1676649613567136786L;
    private Series<Re, T, R> series;
    private Mapping<Re, Collection<R>, R> mapping;

    public Scalar() {
    }

    public Scalar(Mapping<Re, Collection<R>, R> mapping) {
        setMapping(mapping);
    }

    public R apply(Re reactor) {
        Function<Re, Collection<R>> t = series;
        Collection<R> collection = t.apply(reactor);
        Mapping<Reactor, Collection<?>, R>  m = (Mapping) mapping;
        return m.apply(reactor, collection);
    }

    public Scalar<Re, T, R> clone() {
        try {
            return (Scalar) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new SystemException(e);
        }
    }

    public Series<Re, T, R> getSeries() {
        return series;
    }

    public void setSeries(Series<Re, T, R> series) {
        this.series = series;
    }

    public Mapping<Re, Collection<R>, R> getMapping() {
        return mapping;
    }

    public void setMapping(Mapping<Re, Collection<R>, R> mapping) {
        this.mapping = mapping;
    }
}
