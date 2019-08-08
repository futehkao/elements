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

package net.e6tech.elements.cassandra;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.driver.metadata.TableMetadata;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.cassandra.etl.LastUpdate;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.MapBuilder;
import net.e6tech.elements.common.util.SystemException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@BindClass(SessionProvider.class)
public abstract class SessionProvider implements ResourceProvider, Initializable {
    private static final String CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS ${keyspace}  WITH replication = {'class':'SimpleStrategy', 'replication_factor' : ${replication}};";
    private static final Map<String, Object> CREATE_KEYSPACE_ARGUMENTS = Collections.unmodifiableMap(MapBuilder.of("replication", 3));

    private Cache<Class, Inspector> inspectors = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(2000)
            .build();

    protected String createKeyspace = CREATE_KEYSPACE;
    protected Map<String, Object> createKeyspaceArguments = new HashMap<>(CREATE_KEYSPACE_ARGUMENTS);
    private String host;
    private int port = 9042;
    private String keyspace;
    private Provision provision;
    private int maxSessions = 20;
    private int coreConnections = 20;
    private int maxConnections = 200;
    private int heartbeatIntervalSeconds = 10000;
    private int maxRequests = 32768;
    private int poolTimeout = 5000;
    private int readTimeout = 20000;
    private Boolean keepAlive;
    private Class<? extends LastUpdate> lastUpdateClass = LastUpdate.class;
    private Map<String, Session> sessions = new HashMap<>();
    private WriteOptions defaultWriteOptions = new WriteOptions().consistency(Consistency.LOCAL_QUORUM).saveNullFields(false);
    private ReadOptions defaultReadOptions = new ReadOptions().consistency(Consistency.LOCAL_SERIAL);

    private boolean sharedSession = false;

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public String getCreateKeyspace() {
        return createKeyspace;
    }

    public void setCreateKeyspace(String createKeyspace) {
        this.createKeyspace = createKeyspace;
    }

    public Map<String, Object> getCreateKeyspaceArguments() {
        return createKeyspaceArguments;
    }

    public void setCreateKeyspaceArguments(Map<String, Object> createKeyspaceArguments) {
        this.createKeyspaceArguments = createKeyspaceArguments;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public int getCoreConnections() {
        return coreConnections;
    }

    public void setCoreConnections(int coreConnections) {
        this.coreConnections = coreConnections;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int getPoolTimeout() {
        return poolTimeout;
    }

    public void setPoolTimeout(int poolTimeout) {
        this.poolTimeout = poolTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public abstract TableMetadata getTableMetadata(String keyspaceIn, String tableName);

    protected String getKeyspace(String keyspaceIn) {
        String ks = keyspaceIn;
        if (ks == null)
            ks = keyspace;
        return ks;
    }

    public Cache<Class, Inspector> getInspectors() {
        return inspectors;
    }

    public void setInspectors(Cache<Class, Inspector> inspectors) {
        this.inspectors = inspectors;
    }

    public Map<String, Session> getSessions() {
        return sessions;
    }

    public void setSessions(Map<String, Session> sessions) {
        this.sessions = sessions;
    }

    public WriteOptions getDefaultWriteOptions() {
        return defaultWriteOptions;
    }

    public void setDefaultWriteOptions(WriteOptions defaultWriteOptions) {
        this.defaultWriteOptions = defaultWriteOptions;
    }

    public ReadOptions getDefaultReadOptions() {
        return defaultReadOptions;
    }

    public void setDefaultReadOptions(ReadOptions defaultReadOptions) {
        this.defaultReadOptions = defaultReadOptions;
    }

    public boolean isSharedSession() {
        return sharedSession;
    }

    public void setSharedSession(boolean sharedSession) {
        this.sharedSession = sharedSession;
    }


    public Class<? extends LastUpdate> getLastUpdateClass() {
        return lastUpdateClass;
    }

    public void setLastUpdateClass(Class<? extends LastUpdate> lastUpdateClass) {
        this.lastUpdateClass = lastUpdateClass;
    }

    public abstract Generator getGenerator();

    public synchronized Session buildSession(String keyspaceIn) {
        return sessions.computeIfAbsent(keyspaceIn, k -> createSession(getKeyspace(keyspaceIn)));
    }

    protected abstract Session createSession(String keyspaceIn);

    protected abstract void initGenerator();

    protected abstract void initDriver();

    protected abstract void initKeyspace();

    protected abstract void postInit();

    public void initialize(Resources resources) {
        initGenerator();
        getProvision().getResourceManager().bind(Generator.class, getGenerator());
        initDriver();
        initKeyspace();
        getProvision().getResourceManager().rebind(SessionProvider.class, this);
        postInit();
    }

    public Inspector getInspector(Class cls) {
        Callable<Inspector> loader = () -> {
            Inspector inspector = new Inspector(cls, getGenerator());
            inspector.initialize();
            return inspector;
        };

        try {
            return inspectors.get(cls, loader);
        } catch (ExecutionException e) {
            try {
                return loader.call();
            } catch (Exception e1) {
                throw new SystemException(e);
            }
        }
    }

    @Override
    public abstract void onOpen(Resources resources);

    @Override
    public void afterOpen(Resources resources) {
        //
    }

    @Override
    public void onCommit(Resources resources) {
        //
    }

    @Override
    public void afterCommit(Resources resources) {
        //
    }

    @Override
    public void afterAbort(Resources resources) {
        //
    }

    @Override
    public void onAbort(Resources resources) {
        //
    }

    @Override
    public abstract void onClosed(Resources resources);

    @Override
    public abstract void onShutdown();
}
