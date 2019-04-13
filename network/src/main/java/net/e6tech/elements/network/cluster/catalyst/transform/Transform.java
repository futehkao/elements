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
import net.e6tech.elements.network.cluster.catalyst.dataset.Segments;

import java.io.Serializable;
import java.util.stream.Stream;

/**
 * This class is a base class for transforming a stream into another stream.
 * Some typical types of transformations are converting from one type of element
 * to another, or filtering.
 * @param <T> input stream type
 * @param <R> output stream type
 */
public interface Transform<Re extends Reactor, T, R> extends Serializable {

    Stream<R> transform(Re reactor, Stream<T> stream);

    default Transform<Re, T, R> allocate(Segments<?> root) {
        return this;  // default is not to segment.
    }
}
