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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.util.function.ConsumerWithException;
import net.e6tech.elements.common.util.function.FunctionWithException;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("squid:S1700")
public class Bindings {

    private Resources resources;
    private Map<Class, Binding> bindings = new HashMap<>();

    public Bindings(Resources resources) {
        this.resources = resources;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> cls) {
        Binding<T> binding = bindings.computeIfAbsent(cls, key -> resources.getBinding(cls));
        return binding.get();
    }

    @SuppressWarnings("unchecked")
    public <T> Bindings rebind(Class<T> cls, T newValue) {
        Binding<T> binding = bindings.computeIfAbsent(cls, key -> resources.getBinding(cls));
        binding.rebind(newValue);
        return this;
    }

    public void restore() {
        bindings.values().forEach(Binding::restore);
    }

    public <E extends Exception> void rebind(ConsumerWithException<Bindings, E> consumer) throws E {
        try {
            consumer.accept(this);
        } finally {
            restore();
        }
    }

    public <T, E extends Exception> T rebind(FunctionWithException<Bindings, T, E> function) throws E {
        try {
            return function.apply(this);
        } finally {
            restore();
        }
    }
}
