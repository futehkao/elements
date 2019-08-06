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

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import net.e6tech.elements.cassandra.ReadOptions;
import net.e6tech.elements.cassandra.WriteOptions;
import net.e6tech.elements.cassandra.driver.v4.sample.Product;

import java.util.concurrent.CompletionStage;

public interface Mapper<T> {

    T one(ResultSet resultSet);

    T one(AsyncResultSet resultSet);

    PagingIterable<T> all(ResultSet resultSet);

    MappedAsyncPagingIterable<T> all(AsyncResultSet resultSet);

    T get(Object ... keyColumns);

    T get(ReadOptions options, Object ... keyColumns);

    CompletionStage<T> getAsync(Object ... keyColumns);

    CompletionStage<T> getAsync(ReadOptions readOptions, Object ... keyColumnss);

    void save(T entity);

    void save(WriteOptions options, T entity);

    CompletionStage<Void> saveAsync(T entity);

    CompletionStage<Void> saveAsync(WriteOptions options, T entity);

    void delete(T entity);
}
