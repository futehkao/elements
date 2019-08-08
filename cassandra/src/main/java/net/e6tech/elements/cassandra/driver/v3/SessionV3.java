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

package net.e6tech.elements.cassandra.driver.v3;

import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.SimpleStatement;
import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.cql.AsyncResultSet;
import net.e6tech.elements.cassandra.driver.cql.Bound;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.common.util.StringUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SessionV3 extends Wrapper<com.datastax.driver.core.Session> implements Session {

    @Override
    public ResultSet execute(String query) {
        return Wrapper.wrap(new ResultSetV3(), unwrap().execute(query));
    }

    @Override
    public ResultSet execute(String keyspace, String query) {
        if (StringUtil.isNullOrEmpty(keyspace))
            return execute(query);
        SimpleStatement stmt = new SimpleStatement(query);
        stmt.setKeyspace(keyspace);
        return Wrapper.wrap(new ResultSetV3(), unwrap().execute(stmt));
    }

    @Override
    public ResultSet execute(Bound bound) {
        return Wrapper.wrap(new ResultSetV3(), unwrap().execute(((BoundV3) bound).unwrap()));
    }

    @Override
    public Future<AsyncResultSet> executeAsync(String keyspace, String query) {
        if (StringUtil.isNullOrEmpty(keyspace))
            return executeAsync(query);
        SimpleStatement stmt = new SimpleStatement(query);
        stmt.setKeyspace(keyspace);
        return new FutureAsyncResultSet(unwrap().executeAsync(stmt));
    }

    @Override
    public Future<AsyncResultSet> executeAsync(String query) {
        ResultSetFuture future = unwrap().executeAsync(query);
        return new FutureAsyncResultSet(future);
    }

    public Future<AsyncResultSet> executeAsync(Bound bound) {
        ResultSetFuture future = unwrap().executeAsync(((BoundV3) bound).unwrap());
        return new FutureAsyncResultSet(future);
    }

    @Override
    public Prepared prepare(String query) {
        return Wrapper.wrap(new PreparedV3(), unwrap().prepare(query));
    }

    private static class FutureAsyncResultSet implements Future<AsyncResultSet> {
        private ResultSetFuture future;

        FutureAsyncResultSet(ResultSetFuture future) {
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
        public AsyncResultSet get() throws InterruptedException, ExecutionException {
            com.datastax.driver.core.ResultSet rs = future.get();
            return Wrapper.wrap(new AsyncResultSetV3(), rs);
        }

        @Override
        public AsyncResultSet get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            com.datastax.driver.core.ResultSet rs = future.get(timeout, unit);
            return Wrapper.wrap(new AsyncResultSetV3(), rs);
        }
    }
}
