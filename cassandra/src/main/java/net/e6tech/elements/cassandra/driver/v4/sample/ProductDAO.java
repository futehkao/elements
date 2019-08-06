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

package net.e6tech.elements.cassandra.driver.v4.sample;

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.mapper.annotations.*;

import java.util.concurrent.CompletionStage;

import static com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy.SET_TO_NULL;

@DefaultNullSavingStrategy(SET_TO_NULL)
@Dao
public interface ProductDAO {
    @Select
    Product get(Long productId);

    @Select
    CompletionStage<Product> getAsync(Long productId);

    @GetEntity
    Product one(ResultSet resultSet);

    @GetEntity
    Product one(AsyncResultSet resultSet);

    @GetEntity
    PagingIterable<Product> all(ResultSet resultSet);

    @GetEntity
    MappedAsyncPagingIterable<Product> all(AsyncResultSet resultSet);

    @Insert
    void save(Product product);

    @Insert(ttl = "2000", ifNotExists = true)
    void saveTtl(Product product);

    @Insert
    CompletionStage<Void> saveAsync(Product product);

    @Delete
    void delete(Product product);
}

