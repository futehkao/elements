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
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.e6tech.elements.common.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AsyncResultSet<D> extends AsyncFutures<ResultSet, D> {
    static Logger logger = Logger.getLogger();

    AsyncResultSet(Async async, List<ResultSetFuture> futures, Map<ResultSetFuture, Object> futuresData) {
        super(async, (List) futures, (Map) futuresData);
    }

    public Async inCompletionOrderRows(Consumer<Row> consumer) {
        List<ListenableFuture<ResultSet>> list = (List) Futures.inCompletionOrder((List) futures);
        futuresGet(list, consumer);
        return async;
    }

    public Async inExecutionOrderRows(Consumer<Row> consumer) {
        futuresGet(futures, consumer);
        return async;
    }

    private void futuresGet(List<ListenableFuture<ResultSet>> list, Consumer<Row> consumer) {
        for (ListenableFuture<ResultSet> future : list) {
            try {
                for (Row row : future.get()) {
                    consumer.accept(row);
                }
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    public Async inExecutionRows(BiConsumer<Row, D> consumer) {
        for (ListenableFuture<ResultSet> future : futures) {
            try {
                for (Row row : future.get()) {
                    consumer.accept(row, futuresData.get(future));
                }
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
        return async;
    }

    public Async andThen(Async async, BiConsumer<Row, BoundStatement> biConsumer) {
        async.reset();
        inCompletionOrderRows(row -> {
            async.execute(bound -> biConsumer.accept(row, bound));
        });
        return async;
    }
}
