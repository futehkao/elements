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

import com.datastax.driver.core.ResultSet;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.cql.Row;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class ResultSetV3 extends Wrapper<ResultSet> implements net.e6tech.elements.cassandra.driver.cql.ResultSet {

    @Override
    public Row one() {
        return Wrapper.wrap(new RowV3(), unwrap().one());
    }

    @Override
    public List<Row> all() {
        List<com.datastax.driver.core.Row> rows = unwrap().all();
        return rows.stream().map(r -> Wrapper.wrap(new RowV3(), r)).collect(Collectors.toList());
    }

    @Override
    public Iterator<Row> iterator() {
        return new Iterator<Row>() {
            Iterator<com.datastax.driver.core.Row> inner = unwrap().iterator();

            @Override
            public boolean hasNext() {
                return inner.hasNext();
            }

            @Override
            public Row next() {
                RowV3 current = new RowV3();
                com.datastax.driver.core.Row row = inner.next();
                current.wrap(row);
                return current;
            }

            @Override
            public void remove() {
                inner.remove();
            }
        };
    }
}
