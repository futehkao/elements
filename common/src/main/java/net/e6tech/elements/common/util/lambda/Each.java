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

package net.e6tech.elements.common.util.lambda;

import java.util.Optional;

/**
 * This is for iterating through object but want to keep state during iterating
 * Created by futeh.
 */
public class Each<T, U> {
    private T value;
    private U state;


    protected Each(T value) {
        this.value = value;
    }

    public static <T, U> Mutator<T, U> create() {
        Mutator mutator = new Mutator<>();
        mutator.each = new Each<>(null);
        return mutator;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public Optional<U> state() {
        return Optional.ofNullable(state);
    }

    public void state(U state) {
        this.state = state;
    }

    public static class Mutator<T, U> {
        Each<T, U> each;

        public Each<T, U> each() {
            return each;
        }

        public void setValue(T value) {
            each.value = value;
        }
    }
}
