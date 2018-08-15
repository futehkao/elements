/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.util.function;

import java.util.Objects;

@SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
@FunctionalInterface
public interface FunctionWithException<T, R, E extends Exception> {

    R apply(T t) throws E;

    default <V> FunctionWithException<V, R, E> compose(FunctionWithException<? super V, ? extends T, E> before) throws E{
        Objects.requireNonNull(before);
        return (V v) -> apply(before.apply(v));
    }

    default <V> FunctionWithException<T, V, E> andThen(FunctionWithException<? super R, ? extends V, E> after) throws E {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }
}
