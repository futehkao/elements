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

import net.e6tech.elements.network.cluster.catalyst.Reactor;

import java.util.Collection;
import java.util.stream.Stream;

public class CollectionSegment<E> implements Segment<E> {

    private Collection<E> segment;

    public CollectionSegment(Collection<E> segment) {
        this.segment = segment;
    }

    @Override
    public Stream<E> stream(Reactor reactor) {
        return segment.stream();
    }
}
