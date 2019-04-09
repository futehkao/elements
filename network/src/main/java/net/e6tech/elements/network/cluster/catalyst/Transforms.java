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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Transforms<T, R> implements Serializable {
    private static final long serialVersionUID = 5420350641543073437L;

    private Collection<T> data;
    private List<Transform> chain = new ArrayList<>();

    public Collection<R> transform(Operator operator) {
        Stream stream = data.stream();
        for (Transform transform : chain) {
            stream = transform.transform(operator, stream);
        }
        return (Collection) stream.collect(Collectors.toList());
    }

    public <U> Transforms<T, U> add(Transform<R, U> transform) {
        chain.add(transform);
        return (Transforms) this;
    }

    public Transforms<T, R> of(Collection<T> data) {
        Transforms<T, R> transforms = new Transforms<>();
        transforms.data = data;
        transforms.chain = chain;
        return transforms;
    }
}
