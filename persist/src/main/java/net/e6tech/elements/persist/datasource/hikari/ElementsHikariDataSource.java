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

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.e6tech.elements.common.logging.Logger;

/**
 * Subclassed from HikariDataSource to support connectionInitStatments.
 */
@SuppressWarnings("squid:S3077")
public class ElementsHikariDataSource extends HikariDataSource {

    private static Logger logger = Logger.getLogger();

    private static Timer timer = new Timer(true);

    private List<String> connectionInitStatements = new ArrayList<>();

    private volatile DataSource dataSource;
    private volatile boolean externalPool = false;

    private boolean resetPoolOnTimeout = true;

    private long resetPoolShutdownDelay = 3000L;

    private long resetPoolMaxFrequency = TimeUnit.MINUTES.toMillis(5); // default to no more than one pool reset every 5 minutes

    private int resetPoolMaxAttempts = Integer.MAX_VALUE;  // limits how many resets

    private long resetPoolTolerance = TimeUnit.SECONDS.toMillis(30); // Don't bother to reset pool if some other thread has already reset it within this window.

    private volatile long lastReset = 0L;

    private int resetCount = 0;

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

    public boolean isResetPoolOnTimeout() {
        return resetPoolOnTimeout;
    }

    public void setResetPoolOnTimeout(boolean resetPoolOnTimeout) {
        this.resetPoolOnTimeout = resetPoolOnTimeout;
    }

    public long getResetPoolShutdownDelay() {
        return resetPoolShutdownDelay;
    }

    public void setResetPoolShutdownDelay(long resetPoolShutdownDelay) {
        this.resetPoolShutdownDelay = resetPoolShutdownDelay;
    }

    public long getResetPoolMaxFrequency() {
        return resetPoolMaxFrequency;
    }

    public void setResetPoolMaxFrequency(long resetPoolMaxFrequency) {
        this.resetPoolMaxFrequency = resetPoolMaxFrequency;
    }

    public int getResetPoolMaxAttempts() {
        return resetPoolMaxAttempts;
    }

    public void setResetPoolMaxAttempts(int resetPoolMaxAttempts) {
        this.resetPoolMaxAttempts = resetPoolMaxAttempts;
    }

    public long getResetPoolTolerance() {
        return resetPoolTolerance;
    }

    public void setResetPoolTolerance(long resetPoolTolerance) {
        this.resetPoolTolerance = resetPoolTolerance;
    }

    public long getLastReset() {
        return lastReset;
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource ds = dataSource;
        if (ds == null && !externalPool) {
            synchronized (this) {
                ds = dataSource;
                if (ds == null) {
                    try {
                        if (resetPoolOnTimeout && !isAllowPoolSuspension())
                            setAllowPoolSuspension(true);
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

        try {
            return super.getConnection();
        } catch (Exception e) {
            try {
                if (e instanceof SQLTransientConnectionException && resetPool())
                   return super.getConnection();
                else
                    throw e;
            } catch (Exception ex) {
                throw e;
            }
        }
    }

    synchronized protected boolean resetPool() throws Exception {
        if (!resetPoolOnTimeout)
            return false;

        // some other thread has already reset it.  Also prevents multiple threads from trying to reset simultaneously
        if (System.currentTimeMillis() - lastReset <= resetPoolTolerance)
            return true;

        // prevents runaway resetting due to DB connection error.
        if (System.currentTimeMillis() - lastReset < resetPoolMaxFrequency) {
            logger.warn("Reset HikariPool bypassed: last reset happened " + (System.currentTimeMillis() - lastReset) + " milli-seconds ago.");
            return false;
        }

        // put an upper bound on how many resets.
        if (resetCount >= resetPoolMaxAttempts) {
            logger.warn("Reset HikariPool bypassed: resetPoolMaxAttempts=" + resetPoolMaxAttempts);
            return false;
        }

        Field fastField = HikariDataSource.class.getDeclaredField("fastPathPool");
        Field poolField = HikariDataSource.class.getDeclaredField("pool");
        fastField.setAccessible(true);
        poolField.setAccessible(true);
        HikariPool pool = (HikariPool) fastField.get(this);
        if (pool != null) {
            fastField.set(this, null);
        } else {
            pool = (HikariPool) poolField.get(this);
        }

        if (pool != null) {
            lastReset = System.currentTimeMillis();
            resetCount++;
            logger.warn("Reset HikariPool due to getConnection timeout.");
            poolField.set(this, null);
            if (isAllowPoolSuspension())
                pool.suspendPool();
            unregisterPool(pool);
            final HikariPool p = pool;
            TimerTask task = new TimerTask() {
                public void run() {
                    try {
                        logger.warn("Shutting down unresponsive HikariPool: active="
                                + p.getActiveConnections()
                                + ", idle=" + p.getIdleConnections()
                                + ", awaiting=" + p.getThreadsAwaitingConnection()
                                + ", total=" + p.getTotalConnections());
                        p.shutdown();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            timer.schedule(task, resetPoolShutdownDelay);
        }
        return true;
    }

    void unregisterPool(final HikariPool pool) {
        if (!isRegisterMbeans()) {
            return;
        }

        try {
            final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            final ObjectName beanConfigName = new ObjectName("com.zaxxer.hikari:type=PoolConfig (" + getPoolName() + ")");
            final ObjectName beanPoolName = new ObjectName("com.zaxxer.hikari:type=Pool (" + getPoolName() + ")");
            if (mBeanServer.isRegistered(beanConfigName)) {
                mBeanServer.unregisterMBean(beanConfigName);
                mBeanServer.unregisterMBean(beanPoolName);
            }
        } catch (Exception e) {
            logger.warn("{} - Failed to unregister beans.", getPoolName(), e);
        }
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

    private class WrappedDataSource implements DataSource {

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
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
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
