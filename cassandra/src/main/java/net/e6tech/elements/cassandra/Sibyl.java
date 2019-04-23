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

import com.datastax.driver.core.*;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class Sibyl {

    private static Cache<Class, Inspector> inspectors = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(2000)
            .build();

    private static Cache<String, PreparedStatement> preparedStatementCache = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .initialCapacity(200)
            .maximumSize(500)
            .build();

    private Provision provision;
    private boolean saveNullFields = false;
    private ConsistencyLevel readConsistency = ConsistencyLevel.LOCAL_SERIAL;
    private ConsistencyLevel writeConsistency = ConsistencyLevel.LOCAL_QUORUM;

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public Generator getGenerator() {
        return getProvision().getInstance(Generator.class);
    }

    public Session getSession() {
        return getProvision().open().apply(Resources.class, resources -> resources.getInstance(Session.class));
    }

    public boolean isSaveNullFields() {
        return saveNullFields;
    }

    public void setSaveNullFields(boolean saveNullFields) {
        this.saveNullFields = saveNullFields;
    }

    public ConsistencyLevel getReadConsistency() {
        return readConsistency;
    }

    public void setReadConsistency(ConsistencyLevel readConsistency) {
        this.readConsistency = readConsistency;
    }

    public ConsistencyLevel getWriteConsistency() {
        return writeConsistency;
    }

    public void setWriteConsistency(ConsistencyLevel writeConsistency) {
        this.writeConsistency = writeConsistency;
    }

    public Async createAsync() {
        return getProvision().newInstance(Async.class);
    }

    public Async createAsync(String query) {
        PreparedStatement pstmt = null;
        try {
            pstmt = preparedStatementCache.get(query, () -> getSession().prepare(query));
        } catch (ExecutionException e) {
            pstmt = getSession().prepare(query);
        }
        return getProvision().newInstance(Async.class).prepare(pstmt);
    }

    public Async createAsync(PreparedStatement stmt) {
        return getProvision().newInstance(Async.class).prepare(stmt);
    }

    public Deque<Mapper.Option> mapperOptions (Mapper.Option... options) {
        LinkedList<Mapper.Option> list = new LinkedList<>();
        if (options != null) {
            for (Mapper.Option option : options) {
                list.add(option);
            }
        }
        return list;
    }

    public <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls,  Mapper.Option... options) {
        Async async = createAsync();
        Mapper<X> mapper = getMapper(cls);
        Deque<Mapper.Option> mapperOptions = mapperOptions(options);
        mapperOptions.addFirst(Mapper.Option.consistencyLevel(getReadConsistency()));
        mapper.setDefaultGetOptions(mapperOptions.toArray(new Mapper.Option[0]));
        return async.accept(list, k -> mapper.getAsync(k.getKeys()));
    }

    public <T> Mapper<T> getMapper(Class<T> cls) {
        return provision.getInstance(MappingManager.class).mapper(cls);
    }

    public <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls, Mapper.Option... options) {
        Async async = createAsync();
        Mapper<X> mapper = getMapper(cls);
        Deque<Mapper.Option> mapperOptions = mapperOptions(options);
        mapperOptions.addFirst(Mapper.Option.saveNullFields(isSaveNullFields()));
        mapperOptions.addFirst(Mapper.Option.consistencyLevel(getWriteConsistency()));
        return async.accept(list, item -> mapper.saveAsync(item, mapperOptions.toArray(new Mapper.Option[0])));
    }

    public ResultSet execute(String query, Map<String, Object> map) {
        PreparedStatement pstmt = null;
        try {
            pstmt = preparedStatementCache.get(query, () -> getSession().prepare(query));
        } catch (ExecutionException e) {
            pstmt = getSession().prepare(query);
        }
        return execute(pstmt, map);
    }

    public ResultSet execute(PreparedStatement pstmt, Map<String, Object> map) {
       BoundStatement bound = pstmt.bind();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                bound.setToNull(entry.getKey());
            } else {
                bound.set(entry.getKey(), entry.getValue(), (Class) entry.getValue().getClass());
            }
        }
        return getSession().execute(bound);
    }

    public <X> X one(Class<X> cls, String query, Map<String, Object> map) {
        ResultSet resultSet = execute(query, map);
        Result<X> result = getMapper(cls).map(resultSet);
        return result.one();
    }

    public <X> List<X> all(Class<X> cls, String query, Map<String, Object> map) {
        ResultSet resultSet = execute(query, map);
        Result<X> result = getMapper(cls).map(resultSet);
        return result.all();
    }

    public Inspector getInspector(Class cls) {
        Callable<Inspector> loader = () -> {
            Inspector inspector = new Inspector(cls, getGenerator());
            inspector.initialize();
            return inspector;
        };

        try {
            return inspectors.get(cls, loader);
        } catch (ExecutionException e) {
            try {
                return loader.call();
            } catch (Exception e1) {
                throw new SystemException(e);
            }
        }
    }
}
