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

package net.e6tech.elements.cassandra.async;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.driver.cql.AsyncResultSet;
import net.e6tech.elements.cassandra.driver.cql.Bound;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.common.resources.Provision;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AsyncPrepared<D> extends Async<AsyncResultSet, D> {

    private static Cache<String, Prepared> preparedStatementCache = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .initialCapacity(200)
            .maximumSize(500)
            .build();

    private Prepared prepared;

    @Override
    protected AsyncFutures<AsyncResultSet, D> createResult() {
        return new AsyncResultSetFutures<>(this, futures);
    }

    @Override
    public AsyncResultSetFutures<D> getResult() {
        return (AsyncResultSetFutures) super.getResult();
    }

    public AsyncPrepared<D> prepare(String stmt) {
        try {
            return prepare(preparedStatementCache.get(stmt, () -> session.prepare(stmt)));
        } catch (ExecutionException e) {
            return prepare(session.prepare(stmt));
        }
    }

    public AsyncPrepared<D> prepare(Prepared stmt) {
        prepared = stmt;
        return this;
    }

    public AsyncResultSetFutures<D> execute(Consumer<Bound> consumer) {
        return execute(null, consumer);
    }

    public AsyncResultSetFutures<D> execute(D data, Consumer<Bound> consumer) {
        Bound bound = prepared.bind();
        consumer.accept(bound);
        Future<AsyncResultSet> future = session.executeAsync(bound);
        futures.add(future);
        if (data != null)
            futuresData.put(future, data);
        return getResult();
    }

    public AsyncResultSetFutures<D> execute(Collection<D> collection, BiConsumer<D, Bound> biConsumer) {
        resizeFuturesData(collection.size());
        for (D t : collection) {
            execute(t, boundStatement -> biConsumer.accept(t, boundStatement));
        }
        return getResult();
    }

    public AsyncResultSetFutures<D> execute(D[] array, BiConsumer<D, Bound> biConsumer) {
        resizeFuturesData(array.length);
        for (D t : array) {
            execute(t, boundStatement -> biConsumer.accept(t, boundStatement));
        }
        return getResult();
    }

}
