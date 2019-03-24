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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.e6tech.elements.common.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AsyncFutures<T, D> {
    static Logger logger = Logger.getLogger();
    protected Async async;
    protected List<ListenableFuture<T>> futures;
    protected Map<ListenableFuture<T>, D> futuresData;

    AsyncFutures(Async async, List<ListenableFuture<T>> futures, Map<ListenableFuture<T>, D> futuresData) {
        this.async = async;
        this.futures = futures;
        this.futuresData = futuresData;
    }

    public Async inCompletionOrder() {
        return inCompletionOrder(null);
    }

    public Async inCompletionOrder(Consumer<T> consumer) {
        List<ListenableFuture<T>> list = (List) Futures.inCompletionOrder((List) futures);
        futuresAccept(list, consumer);
        return async;
    }

    protected void futuresAccept(List<ListenableFuture<T>> list, Consumer consumer) {
        for (ListenableFuture<T> future : list) {
            try {
                T value = future.get();
                if (consumer != null && value != null)
                    consumer.accept(value);
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    public Async inExecutionOrder() {
        return inCompletionOrder(null);
    }

    public Async inExecutionOrder(Consumer<T> consumer) {
        futuresAccept(futures, consumer);
        return async;
    }

    public Async inExecutionOrder(BiConsumer<D, T> consumer) {
        for (ListenableFuture<T> future : futures) {
            try {
                T value = future.get();
                if (consumer != null && value != null)
                    consumer.accept(futuresData.get(future), value);
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
        return async;
    }

}
