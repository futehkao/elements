/*
 * Copyright 2015 Futeh Kao
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

/**
 * Created by futeh.
 */
public interface ResourceManagerListener {

    default void provisionLoaded(Provision provision) {

    }

    default void beanAdded(String name, Object instance) {

    }

    default void beanRemoved(String name, Object instance) {

    }

    default void resourceProviderAdded(ResourceProvider provider) {

    }

    default <T> void bound(Class<T> cls, T instance) {

    }

    default <T> void unbound(Class<T> cls, T instance) {

    }

    default void classBound(Class cls, Class service) {

    }

    default <T> void namedInstanceBound(String name, Class<T> a, T b) {

    }

    default void injected(Object object) {

    }
}
