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

import net.e6tech.elements.common.util.SystemException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// T is the type of value from future.get()
// D is the associated type of data
public class AsyncFutures<T, D> {
    protected Async<T, D> async;
    protected List<Future<T>> futures;
    private long timeout = 0;

    AsyncFutures(Async async, List<Future<T>> futures) {
        this.async = async;
        this.futures = futures;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public AsyncFutures<T, D> timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    protected void futuresAccept(List<Future<T>> list, Consumer consumer) {
        for (Future<T> future : list) {
            try {
                T value = (timeout > 0) ? future.get(timeout, TimeUnit.MILLISECONDS) : future.get();
                if (consumer != null && value != null)
                    consumer.accept(value);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
    }

    public Async<T, D> inExecutionOrder() {
        futuresAccept(futures, null);
        return async;
    }

    public Async<T, D> inExecutionOrder(Consumer<T> consumer) {
        futuresAccept(futures, consumer);
        return async;
    }

    public Async<T, D> inExecutionOrder(BiConsumer<D, T> consumer) {
        Map<Future<T>, D> futuresData = async.futuresData;
        for (Future<T> future : futures) {
            try {
                T value = (timeout > 0) ? future.get(timeout, TimeUnit.MILLISECONDS) : future.get();
                if (consumer != null && value != null)
                    consumer.accept(futuresData.get(future), value);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return async;
    }
}
