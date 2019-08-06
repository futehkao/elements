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

package net.e6tech.elements.cassandra.driver.v3;

import com.datastax.driver.core.BoundStatement;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.cql.Bound;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BoundV3 extends Wrapper<BoundStatement> implements Bound {

    @Override
    public <V> Bound set(int i, V v, Class<V> targetClass) {
        wrap(unwrap().set(i, v, targetClass));
        return this;
    }

    @Override
    public <V> Bound set(String name, V v, Class<V> targetClass) {
        wrap(unwrap().set(name, v, targetClass));
        return this;
    }

    @Override
    public Bound setToNull(String name) {
        wrap(unwrap().setToNull(name));
        return this;
    }

    @Override
    public <T> Bound setList(String name, List<T> v) {
        wrap(unwrap().setList(name, v));
        return this;
    }

    @Override
    public <T> Bound setSet(String name, Set<T> v) {
        wrap(unwrap().setSet(name, v));
        return this;
    }

    @Override
    public <K, V> Bound setMap(String name, Map<K, V> v) {
        wrap(unwrap().setMap(name, v));
        return this;
    }
}
