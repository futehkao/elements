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

import net.e6tech.elements.network.cluster.catalyst.dataset.Segment;
import net.e6tech.elements.network.cluster.catalyst.transform.Transform;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class generates a collection of R from a data set by applying successive transforms.
 *
 * @param <T> Input type
 * @param <R> Output type
 */
public class Tuple<T, R> implements Serializable, Function<Reactor, Collection<R>> {
    private static final long serialVersionUID = 5420350641543073437L;

    private Segment<T> segment;
    private List<Transform> transforms = new ArrayList<>();

    @Override
    public Collection<R> apply(Reactor reactor) {
        Stream stream = segment.stream(reactor);
        segment = null; // so that it can be gc'd.
        for (Transform transform : transforms) {
            Stream tmp = transform.transform(reactor, stream);
            stream = tmp;
        }
        return (Collection) stream.collect(Collectors.toList());
    }

    public <U> Tuple<T, U> add(Transform<R, U> transform) {
        transforms.add(transform);
        return (Tuple) this;
    }

    public Tuple<T, R> of(Segment<T> segment) {
        Tuple<T, R> generator = new Tuple<>();
        generator.segment = segment;
        generator.transforms = transforms;
        return generator;
    }
}
