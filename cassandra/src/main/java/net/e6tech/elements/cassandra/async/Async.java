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

package net.e6tech.elements.cassandra.async;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Async {
    private static Cache<String, PreparedStatement> preparedStatementCache = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .initialCapacity(200)
            .maximumSize(500)
            .build();

    private PreparedStatement preparedStatement;
    private List<ListenableFuture> futures = new LinkedList<>();
    Map<ListenableFuture, Object> futuresData = new IdentityHashMap<>(512);
    private Session session;
    private AsyncResultSet result;
    private Provision provision;

    public static void resetAll(Async... asyncs) {
        if (asyncs != null)
            for (Async async : asyncs)
                async.reset();
    }

    public Async() {
        result = new AsyncResultSet(this, futures);
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
        this.session = provision.getInstance(Session.class);
    }

    public Async prepare(String stmt) {
        try {
            return prepare(preparedStatementCache.get(stmt, () -> session.prepare(stmt)));
        } catch (ExecutionException e) {
            return prepare(session.prepare(stmt));
        }
    }

    public Async prepare(PreparedStatement stmt) {
        preparedStatement = stmt;
        return this;
    }

    public Async reset() {
        futures.clear();
        futuresData.clear();
        return this;
    }

    public <T, D> AsyncFutures<T, D> getResult() {
        return result;
    }

    public <T, D> AsyncFutures<T, D> acceptAll(AsyncFutures<T, D> result) {
        for (ListenableFuture<T> future : result.futures) {
            futures.add(future);
            if (result.async.futuresData.containsKey(future))
                futuresData.put(future, result.async.futuresData.get(future));
        }
        return result;
    }

    public <T, D> AsyncFutures<T, D> accept(D data, ListenableFuture<T> future) {
        futures.add(future);
        if (data != null)
            futuresData.put(future, data);
        return result;
    }

    public <T, D> AsyncFutures<T, D> accept(Collection<D> collection, Function<D, ListenableFuture<T>> function) {
        resizeFuturesData(collection.size());
        for (D t : collection) {
            accept(t, function.apply(t));
        }
        return result;
    }

    private void resizeFuturesData(int size) {
        int currentSize = futuresData.size();
        int totalSize = size + currentSize;
        Map<ListenableFuture, Object> tmp = new IdentityHashMap<>(Math.max((int) (totalSize/.75f) + 1, 16));
        tmp.putAll(futuresData);
        futuresData = tmp;
    }

    public AsyncResultSet execute(Consumer<BoundStatement> consumer) {
        return execute(null, consumer);
    }

    public <D> AsyncResultSet<D> execute(D data, Consumer<BoundStatement> consumer) {
        BoundStatement bound = preparedStatement.bind();
        consumer.accept(bound);
        ResultSetFuture future = session.executeAsync(bound);
        futures.add(future);
        if (data != null)
            futuresData.put(future, data);
        return result;
    }

    public <D> AsyncResultSet<D> execute(Collection<D> collection, BiConsumer<D, BoundStatement> biConsumer) {
        resizeFuturesData(collection.size());
        for (D t : collection) {
            execute(t, boundStatement -> biConsumer.accept(t, boundStatement));
        }
        return result;
    }

    public <D> AsyncResultSet<D> execute(D[] array, BiConsumer<D, BoundStatement> biConsumer) {
        resizeFuturesData(array.length);
        for (D t : array) {
            execute(t, boundStatement -> biConsumer.accept(t, boundStatement));
        }
        return result;
    }

    public Async inCompletionOrder() {
        return inCompletionOrder(null);
    }

    public <T> Async inCompletionOrder(Consumer<T> consumer) {
        result.inCompletionOrder(consumer);
        return this;
    }

    public Async inExecutionOrder() {
        return this.inExecutionOrder((Consumer) null);
    }

    public <T> Async inExecutionOrder(Consumer<T> consumer) {
        return result.inExecutionOrder(consumer);
    }

    public <D, T> Async inExecutionOrder(BiConsumer<D, T> consumer) {
        return result.inExecutionOrder(consumer);
    }

}
