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

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.common.util.SystemException;

import java.util.Iterator;

public class AsyncResultSetV4 extends Wrapper<AsyncResultSet> implements net.e6tech.elements.cassandra.driver.cql.AsyncResultSet {

    @Override
    public Iterator<Row> iterator() {
        return new Iterator<Row>() {
            AsyncResultSet rs = unwrap();

            private void prepareNextPage() {
                while (!rs.currentPage().iterator().hasNext()) {
                    if (rs.hasMorePages()) {
                        try {
                            rs = rs.fetchNextPage().toCompletableFuture().get();
                        } catch (Exception e) {
                            throw new SystemException(e);
                        }
                    }
                    else break;
                }
            }

            @Override
            public boolean hasNext() {
                prepareNextPage();
                return rs.currentPage().iterator().hasNext();
            }

            @Override
            public Row next() {
                com.datastax.oss.driver.api.core.cql.Row row = rs.currentPage().iterator().next();
                RowV4 current = new RowV4();
                current.wrap(row);
                return current;
            }

            @Override
            public void remove() {
                rs.currentPage().iterator().remove();
            }
        };
    }
}
