/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.cassandra;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.async.AsyncPrepared;
import net.e6tech.elements.cassandra.driver.cql.BaseResultSet;
import net.e6tech.elements.cassandra.driver.cql.Bound;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public abstract class Sibyl {

    private static Cache<String, Prepared> preparedStatementCache = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .initialCapacity(200)
            .maximumSize(500)
            .build();

    private Resources resources;
    private ReadOptions readOptions = new ReadOptions().consistency(Consistency.LOCAL_SERIAL);
    private WriteOptions writeOptions = new WriteOptions().consistency(Consistency.LOCAL_QUORUM).saveNullFields(false);

    public <T> T computeIfAbsent(String key, Function<String, T> mappingFunction) {
        return (T) resources.configurator().computeIfAbsent(key, mappingFunction);
    }

    public ReadOptions getReadOptions() {
        return readOptions;
    }

    public void setReadOptions(ReadOptions readOptions) {
        this.readOptions = readOptions;
    }

    public WriteOptions getWriteOptions() {
        return writeOptions;
    }

    public void setWriteOptions(WriteOptions writeOptions) {
        this.writeOptions = writeOptions;
    }

    public Resources getResources() {
        return resources;
    }

    @Inject
    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public Generator getGenerator() {
        return getResources().getInstance(Generator.class);
    }

    public Session getSession() {
        return resources.getInstance(Session.class);
    }

    public <T, D> Async<T, D> createAsync() {
        return getResources().newInstance(Async.class);
    }

    public <D> AsyncPrepared<D> createAsync(String query) {
        Prepared pstmt;
        try {
            pstmt = preparedStatementCache.get(query, () -> getSession().prepare(query));
        } catch (ExecutionException e) {
            pstmt = getSession().prepare(query);
        }
        return getResources().newInstance(AsyncPrepared.class).prepare(pstmt);
    }

    public <D> AsyncPrepared<D> createAsync(Prepared stmt) {
        return getResources().newInstance(AsyncPrepared.class).prepare(stmt);
    }

    public ResultSet execute(String query, Map<String, Object> map) {
        Prepared pstmt ;
        try {
            pstmt = preparedStatementCache.get(query, () -> getSession().prepare(query));
        } catch (ExecutionException e) {
            pstmt = getSession().prepare(query);
        }
        return execute(pstmt, map);
    }

    protected ResultSet execute(Prepared pstmt, Map<String, Object> map) {
        Bound bound = pstmt.bind();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                bound.setToNull(entry.getKey());
            } else {
                if (entry.getValue() instanceof List) {
                    bound.setList(entry.getKey(), (List) entry.getValue());
                } else if (entry.getValue() instanceof Set) {
                    bound.setSet(entry.getKey(), (Set) entry.getValue());
                } else if (entry.getValue() instanceof Map) {
                    bound.setMap(entry.getKey(), (Map) entry.getValue());
                } else {
                    bound.set(entry.getKey(), entry.getValue(), (Class) entry.getValue().getClass());
                }
            }
        }
        return getSession().execute(bound);
    }

    public abstract <T> T get(Class<T> cls, PrimaryKey primaryKey);

    public abstract <T> T get(Class<T> cls, PrimaryKey primaryKey, ReadOptions readOptions);

    public abstract <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls, ReadOptions userOptions);

    public abstract <T> void save(Class<T> cls, T entity);

    public abstract <T> void save(Class<T> cls, T entity, WriteOptions options);

    public abstract <T> void delete(Class<T> cls, T entity);

    public abstract  <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls);

    public abstract  <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls, WriteOptions userOptions);

    public abstract <X> X one(Class<X> cls, String query, Map<String, Object> map);

    public abstract <X> List<X> all(Class<X> cls, String query, Map<String, Object> map);

    public abstract <X> List<X> mapAll(Class<X> cls, BaseResultSet rs);

    public Inspector getInspector(Class cls) {
        return getResources().getInstance(SessionProvider.class).getInspector(cls);
    }
}
