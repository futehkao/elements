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
import java.util.stream.Stream;

public abstract class Transform<T, R> implements Serializable {
    private static final long serialVersionUID = -6216804622554747554L;
    private Mapping<Operator, T, R> mapping;

    public Transform(Mapping<Operator, T, R> mapping) {
        this.mapping = mapping;
    }

    public abstract Stream<R> transform(Operator operator, Stream<T> stream);

    protected R mapping(Operator operator, T t) {
        return mapping.apply(operator, t);
    }
}
