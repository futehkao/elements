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

import net.e6tech.elements.network.cluster.catalyst.Mapping;
import net.e6tech.elements.network.cluster.catalyst.Reactor;

import java.util.stream.Stream;

/**
 * Transform a Stream R directly to Stream T
 */
public class FlatMapTransform<T, R> extends Transform<T, R> {

    private Mapping<Reactor, Stream<T>, Stream<R>> mapping;

    public FlatMapTransform(Mapping<Reactor, Stream<T>, Stream<R>> mapping) {
        this.mapping = mapping;
    }

    @Override
    public Stream<R> transform(Reactor operator, Stream<T> stream) {
        return mapping.apply(operator, stream);
    }
}
