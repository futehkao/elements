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

package net.e6tech.elements.common.inject.spi;

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.util.SystemException;

/**
 * Created by futeh.
 */
class Binding {
    private Class implementation;
    private Object value;

    public Binding() {
    }

    Binding(Object value) {
        this.value = value;
    }

    Binding(Class implementation) {
        this.implementation = implementation;
    }

    @SuppressWarnings("unchecked")
    public Binding getInstance(Injector injector) {
        if (implementation != null) {
            try {
                Object instance = implementation.getDeclaredConstructor().newInstance();
                injector.inject(instance);
                return new Binding(instance);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return this;
    }

    public Class getImplementation() {
        return implementation;
    }

    public Object getValue() {
        return value;
    }
}
