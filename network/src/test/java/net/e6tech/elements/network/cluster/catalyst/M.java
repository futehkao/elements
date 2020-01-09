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
import java.util.Comparator;
import java.util.function.BiFunction;

public class M<T, R extends Comparable> implements Serializable {

    private Func<T, R> function;

    @SuppressWarnings("unchecked")
    public M() {
        this(Comparator.naturalOrder());
    }

    public M(Comparator<R> comparator) {
        function = ((reactor, collection) -> (R) collection.stream().max(comparator).orElse(null));
    }

    public interface Func<T, R> extends  BiFunction<T, Collection<R>, R>, Serializable {

    }
}
