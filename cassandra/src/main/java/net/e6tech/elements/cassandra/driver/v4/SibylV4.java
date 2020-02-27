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

import net.e6tech.elements.cassandra.ReadOptions;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.WriteOptions;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.driver.cql.BaseResultSet;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.common.inject.Inject;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SibylV4 extends Sibyl {

    private MappingManager mappingManager;

    public MappingManager getMappingManager() {
        return mappingManager;
    }

    @Inject
    public void setMappingManager(MappingManager mappingManager) {
        this.mappingManager = mappingManager;
    }

    private WriteOptions writeOptions(WriteOptions userOptions) {
        WriteOptions options = WriteOptions.from(userOptions);
        return options.merge(getWriteOptions());
    }

    private ReadOptions readOptions(ReadOptions userOptions) {
        ReadOptions options = ReadOptions.from(userOptions);
        return options.merge(getReadOptions());
    }

    @Override
    public String getKeyspace() {
        return mappingManager.getKeyspace();
    }

    @Override
    public <T> T get(Class<T> cls, PrimaryKey primaryKey) {
        return get(cls, primaryKey, null);
    }

    @Override
    public <T> T get(Class<T> cls, PrimaryKey primaryKey, ReadOptions readOptions) {
        return mappingManager.getMapper(cls).get(readOptions(readOptions), primaryKey.getKeys());
    }

    @Override
    public <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls, ReadOptions userOptions) {
        Async<X, PrimaryKey> async = createAsync();
        Mapper<X> mapper = mappingManager.getMapper(cls);
        return async.accept(list, item ->
            mapper.getAsync(readOptions(userOptions), item.getKeys()).toCompletableFuture()
        );
    }

    @Override
    public <T> void save(Class<T> cls, T entity) {
        save(cls, entity, null);
    }

    @Override
    public <T> void save(Class<T> cls, T entity, WriteOptions options) {
        mappingManager.getMapper(cls).save(writeOptions(options), entity);
    }

    @Override
    public <T> void delete(Class<T> cls, T entity) {
        mappingManager.getMapper(cls).delete(entity);
    }

    @Override
    public <X> void save(Collection<X> list, Class<X> cls) {
        save(list, cls, null);
    }

    @Override
    public <X> void save(Collection<X> list, Class<X> cls, WriteOptions userOptions) {
        Async<Void, X> async = createAsync();
        Mapper<X> mapper = mappingManager.getMapper(cls);
        AsyncFutures futures = async.accept(list, item -> mapper.saveAsync(writeOptions(userOptions), item).toCompletableFuture());
            if (userOptions != null && userOptions.timeout != null && userOptions.timeout > 0)
                futures.timeout(userOptions.timeout);
        futures.inExecutionOrder();
    }

    @Override
    public <X> X one(Class<X> cls, String query, Map<String, Object> map) {
        ResultSet resultSet = execute(query, map);
        com.datastax.oss.driver.api.core.cql.ResultSet rs = ((ResultSetV4) resultSet).unwrap();
        return mappingManager.getMapper(cls).one(rs);
    }

    @Override
    public <X> List<X> all(Class<X> cls, String query, Map<String, Object> map) {
        return mapAll(cls, execute(query, map));
    }

    @Override
    public <X> List<X> mapAll(Class<X> cls, BaseResultSet resultSet) {
        Mapper<X> mapper = mappingManager.getMapper(cls);
        List<net.e6tech.elements.cassandra.driver.cql.Row> all = resultSet.all();
        List<X> list = new LinkedList<>();
        for (net.e6tech.elements.cassandra.driver.cql.Row row : all) {
            list.add(mapper.map(row));
        }
        return list;
    }
}
