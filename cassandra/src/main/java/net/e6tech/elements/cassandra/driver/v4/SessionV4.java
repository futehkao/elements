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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.cql.AsyncResultSet;
import net.e6tech.elements.cassandra.driver.cql.Bound;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.common.util.StringUtil;

import java.util.concurrent.*;

public class SessionV4 extends Wrapper<CqlSession> implements net.e6tech.elements.cassandra.Session {

    @Override
    public ResultSet execute(String query) {
        return Wrapper.wrap(new ResultSetV4(), unwrap().execute(query));
    }

    @Override
    public ResultSet execute(String keyspace, String query) {
        if (StringUtil.isNullOrEmpty(keyspace))
            return execute(query);
        SimpleStatement stmt = SimpleStatement.newInstance(query);
        stmt.setKeyspace(keyspace);
        return Wrapper.wrap(new ResultSetV4(), unwrap().execute(stmt));
    }

    @Override
    public ResultSet execute(Bound bound) {
        return Wrapper.wrap(new ResultSetV4(), unwrap().execute(((BoundV4) bound).unwrap()));
    }

    @Override
    public Future<AsyncResultSet> executeAsync(String keyspace, String query) {
        if (StringUtil.isNullOrEmpty(keyspace))
            return executeAsync(query);
        SimpleStatement stmt = SimpleStatement.newInstance(query);
        stmt.setKeyspace(keyspace);
        return new FutureAsyncResultSet(unwrap().executeAsync(stmt).toCompletableFuture());
    }

    @Override
    public Future<AsyncResultSet> executeAsync(String query) {
        return new FutureAsyncResultSet(unwrap().executeAsync(query).toCompletableFuture());
    }

    public Future<AsyncResultSet> executeAsync(Bound bound) {
        CompletionStage<com.datastax.oss.driver.api.core.cql.AsyncResultSet> completionStage = unwrap().executeAsync(((BoundV4) bound).unwrap());
        CompletableFuture<com.datastax.oss.driver.api.core.cql.AsyncResultSet> future = completionStage.toCompletableFuture();
        return new FutureAsyncResultSet(future);
    }

    @Override
    public Prepared prepare(String query) {
        return Wrapper.wrap(new PreparedV4(), unwrap().prepare(query));
    }

    private static class FutureAsyncResultSet implements Future<AsyncResultSet> {

        CompletableFuture<com.datastax.oss.driver.api.core.cql.AsyncResultSet> future;

        FutureAsyncResultSet(CompletableFuture<com.datastax.oss.driver.api.core.cql.AsyncResultSet> future) {
            this.future = future;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public net.e6tech.elements.cassandra.driver.cql.AsyncResultSet get() throws InterruptedException, ExecutionException {
            com.datastax.oss.driver.api.core.cql.AsyncResultSet rs = future.get();
            return Wrapper.wrap(new AsyncResultSetV4(), rs);
        }

        @Override
        public AsyncResultSet get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            com.datastax.oss.driver.api.core.cql.AsyncResultSet rs = future.get(timeout, unit);
            return Wrapper.wrap(new AsyncResultSetV4(), rs);
        }
    }
}
