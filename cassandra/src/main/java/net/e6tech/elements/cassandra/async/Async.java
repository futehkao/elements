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
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Async {
    private PreparedStatement preparedStatement;
    private List<ListenableFuture> futures = Lists.newArrayList();
    private Map<ListenableFuture, Object> futuresData = new IdentityHashMap<>();
    private Session session;
    private AsyncResultSet result;
    private Provision provision;

    public static void resetAll(Async... asyncs) {
        if (asyncs != null)
            for (Async async : asyncs)
                async.reset();
    }

    public Async() {
        result = new AsyncResultSet(this, futures, futuresData);
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
        return prepare(session.prepare(stmt));
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
            if (result.futuresData.containsKey(future))
                futuresData.put(future, result.futuresData.get(future));
        }
        return result;
    }

    public <T, D> AsyncFutures<T, D> accept(D data, ListenableFuture<T> future) {
        futures.add(future);
        if (data != null)
            futuresData.put(future, data);
        return result;
    }

    public <T, D> AsyncFutures<T, D> accept(Iterable<D> iterable, Function<D, ListenableFuture<T>> function) {
        for (D t : iterable) {
            accept(t, function.apply(t));
        }
        return result;
    }

    public AsyncResultSet<?> execute(Consumer<BoundStatement> consumer) {
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

    public <D> AsyncResultSet<D> execute(Iterable<D> iterable, BiConsumer<D, BoundStatement> biConsumer) {
        for (D t : iterable) {
            execute(t, boundStatement -> biConsumer.accept(t, boundStatement));
        }
        return result;
    }

    public <D> AsyncResultSet<D> execute(D[] array, BiConsumer<D, BoundStatement> biConsumer) {
        for (D t : array) {
            execute(t, boundStatement -> biConsumer.accept(t, boundStatement));
        }
        return new AsyncResultSet(this, futures, futuresData);
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
