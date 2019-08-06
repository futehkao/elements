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


import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.mapper.MapperContext;
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.internal.core.util.concurrent.BlockingOperation;
import com.datastax.oss.driver.internal.core.util.concurrent.CompletableFutures;
import com.datastax.oss.driver.internal.mapper.DaoBase;
import net.e6tech.elements.cassandra.ReadOptions;
import net.e6tech.elements.cassandra.WriteOptions;
import net.e6tech.elements.cassandra.driver.v4.sample.Product;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.common.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class MapperImpl<T> extends DaoBase implements Mapper<T> {

    private static final Logger LOG = Logger.getLogger();

    private final Helper<T> helper;
    private final PreparedStatement getStatement;
    private final PreparedStatement saveStatement;
    private final PreparedStatement deleteStatement;
    private Inspector inspector;

    private Map<WriteOptions, PreparedStatement> saveStatements = new ConcurrentHashMap<>();

    private MapperImpl(MapperContext context,
                       Helper helper,
                       Inspector inspector,
                       PreparedStatement getStatement,
                       PreparedStatement saveStatement,
                       PreparedStatement deleteStatement) {
        super(context);
        this.helper = helper;
        this.inspector = inspector;
        this.getStatement = getStatement;
        this.saveStatement = saveStatement;
        this.deleteStatement = deleteStatement;
    }

    @Override
    public T one(ResultSet resultSet) {
        Row row = resultSet.one();
        return (row == null) ? null : helper.get(row);
    }

    @Override
    public T one(AsyncResultSet resultSet) {
        Row row = resultSet.one();
        return (row == null) ? null : helper.get(row);
    }

    @Override
    public PagingIterable<T> all(ResultSet resultSet) {
        return resultSet.map(helper::get);
    }

    @Override
    public MappedAsyncPagingIterable<T> all(AsyncResultSet resultSet) {
        return resultSet.map(helper::get);
    }

    private BoundStatement getBoundStatement(ReadOptions options, Object ... keys) {
        BoundStatementBuilder boundStatementBuilder = getStatement.boundStatementBuilder();

        int i = 0;
        for (Inspector.ColumnAccessor accessor : inspector.getPrimaryKeyColumns()) {
            boundStatementBuilder = boundStatementBuilder.set(accessor.getColumnName(), keys[i], accessor.getType());
            i++;
        }
        BoundStatement boundStatement =  boundStatementBuilder.build();
        if (options != null && options.consistency != null) {
            boundStatement.setConsistencyLevel(DefaultConsistencyLevel.valueOf(options.consistency.name()));
        }
        return boundStatement;
    }

    @Override
    public T get(Object ... keys) {
        return get(null, keys);
    }

    @Override
    public T get(ReadOptions readOptions, Object ... keys) {
        return executeAndMapToSingleEntity(getBoundStatement(readOptions, keys), helper);
    }

    @Override
    public CompletionStage<T> getAsync(Object ... keys) {
        try {
            return executeAsyncAndMapToSingleEntity(getBoundStatement(null, keys), helper);
        } catch (Throwable t) {
            return CompletableFutures.failedFuture(t);
        }
    }

    @Override
    public CompletionStage<T> getAsync(ReadOptions readOptions, Object ... keys) {
        try {
            return executeAsyncAndMapToSingleEntity(getBoundStatement(readOptions, keys), helper);
        } catch (Throwable t) {
            return CompletableFutures.failedFuture(t);
        }
    }

    private BoundStatement saveBoundStatement(WriteOptions options, T entity) {
        PreparedStatement save = (options == null) ? saveStatement
                : saveStatements.computeIfAbsent(options, wo -> {
                    Insert insert = helper.insert();
                    if (options.ifNotExists != null && options.ifNotExists) {
                        insert = insert.ifNotExists();
                    }
                    if (options.ttl != null) {
                        insert = insert.usingTtl(options.ttl);
                    }
            SimpleStatement simple = insert.build();
            return context.getSession().prepare(simple);
        });

        BoundStatementBuilder boundStatementBuilder = save.boundStatementBuilder();
        BoundStatement boundStatement;
        if (options != null) {
            if (options.saveNullFields != null) {
                if (options.saveNullFields)
                    helper.set(entity, boundStatementBuilder, NullSavingStrategy.SET_TO_NULL);
                else
                    helper.set(entity, boundStatementBuilder, NullSavingStrategy.DO_NOT_SET);
            } else {
                helper.set(entity, boundStatementBuilder, NullSavingStrategy.DO_NOT_SET);
            }

            boundStatement = boundStatementBuilder.build();
            if (options.consistency != null) {
                boundStatement.setConsistencyLevel(DefaultConsistencyLevel.valueOf(options.consistency.name()));
            }
        } else {
            helper.set(entity, boundStatementBuilder, NullSavingStrategy.DO_NOT_SET);
            boundStatement = boundStatementBuilder.build();
        }
        return boundStatement;
    }

    @Override
    public void save(T entity) {
        execute(saveBoundStatement(null, entity));
    }

    @Override
    public void save(WriteOptions options, T entity) {
        execute(saveBoundStatement(options, entity));
    }

    @Override
    public CompletionStage<Void> saveAsync(T entity) {
        try {
            return executeAsyncAndMapToVoid(saveBoundStatement(null, entity));
        } catch (Throwable t) {
            return CompletableFutures.failedFuture(t);
        }
    }

    @Override
    public CompletionStage<Void> saveAsync(WriteOptions options, T entity) {
        try {
            return executeAsyncAndMapToVoid(saveBoundStatement(options, entity));
        } catch (Throwable t) {
            return CompletableFutures.failedFuture(t);
        }
    }


    @Override
    public void delete(T entity) {
        BoundStatementBuilder boundStatementBuilder = deleteStatement.boundStatementBuilder();

        int i = 0;
        for (Inspector.ColumnAccessor accessor : inspector.getPrimaryKeyColumns()) {
            Object key = accessor.get(entity);
            boundStatementBuilder = boundStatementBuilder.set(accessor.getColumnName(), key, accessor.getType());
            i++;
        }

        BoundStatement boundStatement = boundStatementBuilder.build();
        execute(boundStatement);
    }

    public static <T> CompletableFuture<MapperImpl<T>> initAsync(MapperContext context, Class<T> cls, Inspector inspector) {
        LOG.debug("[{}] Initializing new instance for keyspace = {} and table = {}",
                context.getSession().getName(),
                context.getKeyspaceId(),
                context.getTableId());
        try {
            // Initialize all entity helpers
            Helper<T> helper = new Helper<>(context, cls, inspector);
            List<CompletionStage<PreparedStatement>> prepareStages = new ArrayList<>();

            // Prepare the statement for `get(java.lang.Long)`:
            SimpleStatement getStatement_simple = helper.selectByPrimaryKey().build();
            LOG.debug("[{}] Preparing query `{}` for method get(java.lang.Long)",
                    context.getSession().getName(),
                    getStatement_simple.getQuery());
            CompletionStage<PreparedStatement> getStatement = prepare(getStatement_simple, context);
            prepareStages.add(getStatement);

            // Prepare the statement for `save(net.e6tech.elements.cassandra.driver.v4.sample.Product)`:
            SimpleStatement saveStatement_simple = helper.insert().build();
            LOG.debug("[{}] Preparing query `{}` for method save(net.e6tech.elements.cassandra.driver.v4.sample.Product)",
                    context.getSession().getName(),
                    saveStatement_simple.getQuery());
            CompletionStage<PreparedStatement> saveStatement = prepare(saveStatement_simple, context);
            prepareStages.add(saveStatement);

            // Prepare the statement for `delete(net.e6tech.elements.cassandra.driver.v4.sample.Product)`:
            SimpleStatement deleteStatement_simple = helper.deleteByPrimaryKey().build();
            LOG.debug("[{}] Preparing query `{}` for method delete(net.e6tech.elements.cassandra.driver.v4.sample.Product)",
                    context.getSession().getName(),
                    deleteStatement_simple.getQuery());
            CompletionStage<PreparedStatement> deleteStatement = prepare(deleteStatement_simple, context);
            prepareStages.add(deleteStatement);

            // Initialize all method invokers
            // Build the DAO when all statements are prepared
            return CompletableFutures.allSuccessful(prepareStages)
                    .thenApply(v -> (MapperImpl<T>) new MapperImpl<>(context,
                            helper,
                            inspector,
                            CompletableFutures.getCompleted(getStatement),
                            CompletableFutures.getCompleted(saveStatement),
                            CompletableFutures.getCompleted(deleteStatement)))
                    .toCompletableFuture();
        } catch (Throwable t) {
            return CompletableFutures.failedFuture(t);
        }
    }

    public static <T> Mapper<T> init(MapperContext context, Class<T> cls, Inspector inspector) {
        BlockingOperation.checkNotDriverThread();
        return CompletableFutures.getUninterruptibly(initAsync(context, cls, inspector));
    }
}
