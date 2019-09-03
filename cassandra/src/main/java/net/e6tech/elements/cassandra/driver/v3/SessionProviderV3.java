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

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.mapping.*;
import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.metadata.TableMetadata;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.TextBuilder;

import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public class SessionProviderV3 extends SessionProvider {

    private Generator generator = new GeneratorV3();
    private NamingStrategy namingStrategy = new DefaultNamingStrategy(NamingConventions.LOWER_CAMEL_CASE, NamingConventions.LOWER_SNAKE_CASE);

    private Cluster cluster;
    private MappingManager sharedMappingManager;
    private Deque<MappingManager> cachedManagers = new ConcurrentLinkedDeque<>();
    private Consumer<Cluster.Builder> builderOptions;
    private volatile boolean sharedInitialized = false;

    @Override
    public Generator getGenerator() {
        return generator;
    }

    protected net.e6tech.elements.cassandra.Session createSession(String keyspaceIn) {
        Session session =  cluster.connect(keyspaceIn);
        return Wrapper.wrap(new net.e6tech.elements.cassandra.driver.v3.SessionV3(), session);
    }

    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public Consumer<Cluster.Builder> getBuilderOptions() {
        return builderOptions;
    }

    public void setBuilderOptions(Consumer<Cluster.Builder> builderOptions) {
        this.builderOptions = builderOptions;
    }

    public TableMetadata getTableMetadata(String keyspaceIn, String tableName) {
        com.datastax.driver.core.TableMetadata metadata = cluster.getMetadata().getKeyspace(getKeyspace(keyspaceIn)).getTable(tableName);
        if (metadata == null)
            return null;
        return new TableMetadataV3(generator, metadata);
    }

    protected MappingManager createMappingManager(Session session) {
        DefaultPropertyMapper propertyMapper = new DefaultPropertyMapper();
        propertyMapper.setNamingStrategy(namingStrategy);
        MappingConfiguration conf = MappingConfiguration.builder().withPropertyMapper(propertyMapper).build();
        return new MappingManager(session, conf);
    }

    protected synchronized void buildCluster() {
        if (cluster != null)
            return;

        PoolingOptions poolingOptions = new PoolingOptions();
        poolingOptions
                .setHeartbeatIntervalSeconds(getHeartbeatIntervalSeconds())
                .setPoolTimeoutMillis(getPoolTimeout())
                .setCoreConnectionsPerHost(HostDistance.LOCAL, getCoreConnections())
                .setMaxConnectionsPerHost( HostDistance.LOCAL, getMaxConnections())
                .setMaxRequestsPerConnection(HostDistance.LOCAL, getMaxRequests());

        SocketOptions socketOptions = new SocketOptions()
                .setReadTimeoutMillis(getReadTimeout());
        if (getKeepAlive() != null)
            socketOptions.setKeepAlive(getKeepAlive());

        Cluster.Builder builder = Cluster.builder()
                .addContactPoint(getHost())
                .withPort(getPort())
                .withPoolingOptions(poolingOptions)
                .withSocketOptions(socketOptions)
                .withoutJMXReporting();

        if (getBuilderOptions() != null)
            getBuilderOptions().accept(builder);
        cluster = builder.build();
    }

    @Override
    protected void initGenerator() {
        GeneratorV3 gen = new GeneratorV3();
        gen.setNamingStrategy(namingStrategy);
        generator = gen;
    }

    @Override
    protected void initDriver() {
        buildCluster();
    }

    @Override
    protected void initKeyspace() {
        Session session;
        try {
            session = cluster.connect(getKeyspace());
        } catch (InvalidQueryException ex) {
            // connect to default keyspace and create a named keyspace
            session = cluster.connect();
            createKeyspaceArguments.put("keyspace", getKeyspace());
            session.execute(TextBuilder.using(createKeyspace).build(createKeyspaceArguments));
        }
        session.close();
    }

    @Override
    protected void postInit() {
        getProvision().getResourceManager().rebind(Cluster.class, cluster);
    }

    @Override
    public void onOpen(Resources resources) {
        MappingManager mappingManager;
        Session session;

        if (isSharedSession()) {
            if (!sharedInitialized) {
                synchronized (this) {
                    if (!sharedInitialized) {
                        cluster.connect(getKeyspace());
                        session = cluster.connect(getKeyspace());
                        sharedMappingManager = createMappingManager(session);
                    }
                }
            }
            mappingManager = sharedMappingManager;
            session = mappingManager.getSession();
        } else {
            try {
                mappingManager = cachedManagers.pop();
                session = mappingManager.getSession();
            } catch (NoSuchElementException ex) {
                cluster.connect(getKeyspace());
                session = cluster.connect(getKeyspace());
                mappingManager = createMappingManager(session);
            }
        }

        resources.rebind(Session.class, session);
        resources.rebind(net.e6tech.elements.cassandra.Session.class, Wrapper.wrap(new net.e6tech.elements.cassandra.driver.v3.SessionV3(), session));
        resources.rebind(MappingManager.class, mappingManager);
        resources.rebind(Cluster.class, cluster);
        resources.rebind(SessionProvider.class, this);
        Sibyl s = resources.newInstance(SibylV3.class);
        s.setReadOptions(getDefaultReadOptions());
        s.setWriteOptions(getDefaultWriteOptions());
        resources.rebind(Sibyl.class, s);
    }

    @Override
    public void onClosed(Resources resources) {
        if (isSharedSession()) {
            return;
        }

        MappingManager mappingManager = resources.getInstance(MappingManager.class);
        if (mappingManager != null) {
            if (cachedManagers.size() < getMaxSessions()) {
                cachedManagers.push(mappingManager);
            } else {
                mappingManager.getSession().closeAsync();
            }
        }
    }

    @Override
    public void onShutdown() {
        if (isSharedSession()) {
            if (sharedMappingManager != null) {
                sharedMappingManager.getSession().close();
            }
        } else {
            for (MappingManager mappingManager : cachedManagers) {
                mappingManager.getSession().close();
            }
        }
    }
}
