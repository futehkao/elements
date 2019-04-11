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

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;

public class Scalar<T, R> implements Function<Reactor, R>, Serializable {
    private static final long serialVersionUID = 1676649613567136786L;
    private Function<Reactor, Collection<T>>  transform;
    private Mapping<Reactor, Collection<T>, R> mapping;

    public Scalar(Function<Reactor, Collection<T>> transform, Mapping<Reactor, Collection<T>, R> mapping) {
        this.transform = transform;
        this.mapping = mapping;
    }

    public R apply(Reactor reactor) {
        Collection<T> collection = transform.apply(reactor);
        return mapping.apply(reactor, collection);
    }
}
