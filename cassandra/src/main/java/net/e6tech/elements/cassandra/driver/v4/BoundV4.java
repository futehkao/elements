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

package net.e6tech.elements.cassandra.driver.v4;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.cql.Bound;

import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("squid:S135")
public class BoundV4 extends Wrapper<BoundStatement> implements Bound {

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
        Class type = null;
        for (T t : v) {
            if (t == null)
                continue;
            type = t.getClass();
            break;
        }
        if (type != null)
            wrap(unwrap().setList(name, v, type));
        return this;
    }

    @Override
    public <T> Bound setSet(String name, Set<T> v) {
        Class type = null;
        for (T t : v) {
            if (t == null)
                continue;
            type = t.getClass();
            break;
        }
        if (type != null)
            wrap(unwrap().setSet(name, v, type));

        return this;
    }

    @Override
    public <K, V> Bound setMap(String name, Map<K, V> v) {
        Class keyType = null;
        Class valueType = null;
        for (Map.Entry<K, V> entry : v.entrySet()) {
            if (entry.getKey() == null && entry.getValue() == null)
                continue;
            keyType = entry.getKey().getClass();
            valueType = entry.getValue().getClass();
            break;
        }

        if (keyType != null && valueType != null)
            wrap(unwrap().setMap(name, v, keyType, valueType));

        return this;
    }
}
