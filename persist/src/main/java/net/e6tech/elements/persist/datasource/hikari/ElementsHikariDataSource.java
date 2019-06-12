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

package net.e6tech.elements.persist.datasource.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Subclassed from HikariDataSource to support connectionInitStatments.
 */
@SuppressWarnings("squid:S3077")
public class ElementsHikariDataSource extends HikariDataSource  {

    private List<String> connectionInitStatements = new ArrayList<>();

    private volatile DataSource dataSource;
    private volatile boolean externalPool = false;

    public ElementsHikariDataSource() {
    }

    public ElementsHikariDataSource(HikariConfig configuration) {
        super(configuration);
        externalPool = true;
    }

    public List<String> getConnectionInitStatements() {
        return connectionInitStatements;
    }

    public void setConnectionInitStatements(List<String> connectionInitStatements) {
        this.connectionInitStatements = connectionInitStatements;
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource ds = dataSource;
        if (ds == null && !externalPool) {
            synchronized (this) {
                ds = dataSource;
                if (ds == null) {
                    try {
                        boolean registerMBean = isRegisterMbeans();
                        setRegisterMbeans(false);
                        HikariPool pool = new HikariPool(this);
                        setRegisterMbeans(registerMBean);
                        ds = dataSource = pool.getUnwrappedDataSource();
                        setDataSource(wrapDataSource(ds));
                        pool.shutdown();
                    } catch (HikariPool.PoolInitializationException pie) {
                        if (pie.getCause() instanceof SQLException) {
                            throw (SQLException) pie.getCause();
                        } else {
                            throw pie;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return super.getConnection();
    }

    protected void initConnection(final Connection connection) throws SQLException {
        if (connectionInitStatements.isEmpty())
            return;

        try (Statement statement = connection.createStatement()) {
            for (String sql : connectionInitStatements) {
                statement.addBatch(sql);
            }
            statement.executeBatch();
        }
    }

    protected DataSource wrapDataSource(DataSource ds) {
        return new WrappedDataSource(ds);
    }

    private class WrappedDataSource implements  DataSource {

        private DataSource dataSource;

        WrappedDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = dataSource.getConnection();
            initConnection(connection);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = dataSource.getConnection(username, password);
            initConnection(connection);
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return dataSource.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            dataSource.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            dataSource.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return dataSource.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return dataSource.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return dataSource.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return dataSource.isWrapperFor(iface);
        }
    }
}
