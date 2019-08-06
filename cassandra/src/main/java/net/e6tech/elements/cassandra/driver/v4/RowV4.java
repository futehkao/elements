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

import com.datastax.oss.driver.api.core.cql.Row;
import net.e6tech.elements.cassandra.driver.Wrapper;

public class RowV4 extends Wrapper<Row> implements net.e6tech.elements.cassandra.driver.cql.Row {

    public int columnSize() {
        return unwrap().getColumnDefinitions().size();
    }

    public Object getObject(int i) {
        return unwrap().getObject(i);
    }

    public Object getObject(String name) {
        return unwrap().getObject(name);
    }

    public <T> T get(int i, Class<T> targetClass) {
        return unwrap().get(i, targetClass);
    }

    public <T> T get(String name, Class<T> targetClass) {
        return unwrap().get(name, targetClass);
    }

    public long getLong(int i) {
        return unwrap().getLong(i);
    }

    public long getLong(String name) {
        return unwrap().getLong(name);
    }

    public boolean isNull(int i) {
        return unwrap().isNull(i);
    }

    public boolean isNull(String name) {
        return unwrap().isNull(name);
    }

}
