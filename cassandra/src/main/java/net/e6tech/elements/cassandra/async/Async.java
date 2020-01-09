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

import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.common.inject.Inject;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Async<T, D> {

    protected List<Future<T>> futures = new LinkedList<>();
    Map<Future<T>, D> futuresData = new IdentityHashMap<>(512);
    protected Session session;
    private AsyncFutures<T, D> result;

    public static void resetAll(Async... asyncs) {
        if (asyncs != null)
            for (Async async : asyncs)
                async.reset();
    }

    public Async() {
        result = createResult();
    }

    @SuppressWarnings("unchecked")
    protected AsyncFutures<T, D> createResult() {
        return new AsyncResultSetFutures(this, futures);
    }

    public Session getSession() {
        return session;
    }

    @Inject
    public void setSession(Session session) {
        this.session = session;
    }

    public Async<T, D> reset() {
        futures.clear();
        futuresData.clear();
        return this;
    }

    public AsyncFutures<T, D> getResult() {
        return result;
    }

    public AsyncFutures<T, D> acceptAll(AsyncFutures<T, D> result) {
        for (Future<T> future : result.futures) {
            futures.add(future);
            if (result.async.futuresData.containsKey(future))
                futuresData.put(future, result.async.futuresData.get(future));
        }
        return this.result;
    }

    public AsyncFutures<T, D> accept(D data, Future<T> future) {
        futures.add(future);
        if (data != null)
            futuresData.put(future, data);
        return result;
    }

    public AsyncFutures<T, D> accept(Collection<D> collection, Function<D, Future<T>> function) {
        resizeFuturesData(collection.size());
        for (D t : collection) {
            accept(t, function.apply(t));
        }
        return result;
    }

    protected void resizeFuturesData(int size) {
        int currentSize = futuresData.size();
        int totalSize = size + currentSize;
        Map<Future<T>, D> tmp = new IdentityHashMap<>(Math.max((int) (totalSize/.75f) + 1, 16));
        tmp.putAll(futuresData);
        futuresData = tmp;
    }

    @SuppressWarnings("unchecked")
    public Async<T, D> inExecutionOrder() {
        return this.inExecutionOrder((Consumer) null);
    }

    public Async<T, D> inExecutionOrder(Consumer<T> consumer) {
        return result.inExecutionOrder(consumer);
    }

    public  Async<T, D> inExecutionOrder(BiConsumer<D, T> consumer) {
        return result.inExecutionOrder(consumer);
    }

}
