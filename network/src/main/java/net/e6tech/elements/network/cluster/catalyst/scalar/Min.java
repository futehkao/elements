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

import net.e6tech.elements.network.cluster.catalyst.Reactor;

import java.util.Comparator;

public class Min<Re extends Reactor, T, R extends Comparable> extends Scalar<Re, T, R,R> {

    public Min() {
        this(Comparator.naturalOrder());
    }

    public Min(Comparator<R> comparator) {
        setMapping((reactor, collection) -> (R) collection.stream().min(comparator).orElse(null));
    }
}
