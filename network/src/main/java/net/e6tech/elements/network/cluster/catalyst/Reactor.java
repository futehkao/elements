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

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.network.cluster.invocation.Local;

import java.util.function.Function;

public interface Reactor {

    default Provision getProvision() {
        return null;
    }

    @Local
    default <T> T get(Class<T> cls) {
        return null;
    }

    default <R> R apply(Function<? extends Reactor, R> function) {
        Function<Reactor, R> capture = (Function) function;
        return capture.apply(this);
    }
}
