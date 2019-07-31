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

package net.e6tech.elements.cassandra;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.mapping.*;
import com.datastax.driver.mapping.annotations.UDT;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.cassandra.etl.LastUpdate;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceProvider;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.MapBuilder;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.TextBuilder;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class SessionProvider implements ResourceProvider, Initializable {
    private static final String CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS ${keyspace}  WITH replication = {'class':'SimpleStrategy', 'replication_factor' : ${replication}};";
    private static final Map<String, Object> CREATE_KEYSPACE_ARGUMENTS = Collections.unmodifiableMap(MapBuilder.of("replication", 3));

    private Cache<Class, Inspector> inspectors = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(2000)
            .build();
    private Deque<MappingManager> cachedManagers = new ConcurrentLinkedDeque<>();
    private Cluster cluster;
    private String createKeyspace = CREATE_KEYSPACE;
    private Map<String, Object> createKeyspaceArguments = new HashMap<>(CREATE_KEYSPACE_ARGUMENTS);
    private String host;
    private int port = 9042;
    private String keyspace;
    private Map<String, Session> sessions = new HashMap<>();
    private Provision provision;
    private int maxSessions = 20;
    private Generator generator = new Generator();
    private int coreConnections = 20;
    private int maxConnections = 200;
    private int maxRequests = 32768;
    private int poolTimeout = 5000;
    private int readTimeout = 20000;
    private Boolean keepAlive;
    private HostDistance distance = HostDistance.LOCAL;
    private NamingStrategy namingStrategy = new DefaultNamingStrategy(NamingConventions.LOWER_CAMEL_CASE, NamingConventions.LOWER_SNAKE_CASE);
    private Class<? extends LastUpdate> lastUpdateClass = LastUpdate.class;
    private Sibyl sibylDefault = new Sibyl();
    private Consumer<Cluster.Builder> builderOptions;
    private boolean sharedSession = false;
    private MappingManager sharedMappingManager;

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

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
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

    public HostDistance getDistance() {
        return distance;
    }

    public void setDistance(HostDistance distance) {
        this.distance = distance;
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

    public Consumer<Cluster.Builder> getBuilderOptions() {
        return builderOptions;
    }

    public void setBuilderOptions(Consumer<Cluster.Builder> builderOptions) {
        this.builderOptions = builderOptions;
    }

    public Session getSession() {
        return getSession(keyspace);
    }

    public Session getSession(String keyspaceIn) {
        return buildSession(getKeyspace(keyspaceIn));
    }

    public KeyspaceMetadata getKeyspaceMetadata(String keyspaceIn) {
        return cluster.getMetadata().getKeyspace(getKeyspace(keyspaceIn));
    }

    public TableMetadata getTableMetadata(String keyspaceIn, String tableName) {
        return cluster.getMetadata().getKeyspace(getKeyspace(keyspaceIn)).getTable(tableName);
    }

    protected String getKeyspace(String keyspaceIn) {
        String ks = keyspaceIn;
        if (ks == null)
            ks = keyspace;
        return ks;
    }

    public Sibyl getSibylDefault() {
        return sibylDefault;
    }

    public void setSibylDefault(Sibyl sibylDefault) {
        this.sibylDefault = sibylDefault;
    }

    public boolean isSharedSession() {
        return sharedSession;
    }

    public void setSharedSession(boolean sharedSession) {
        this.sharedSession = sharedSession;
    }

    protected MappingManager createMappingManager(Session session) {
        DefaultPropertyMapper propertyMapper = new DefaultPropertyMapper();
        propertyMapper.setNamingStrategy(namingStrategy);
        MappingConfiguration conf = MappingConfiguration.builder().withPropertyMapper(propertyMapper).build();
        return new MappingManager(session, conf);
    }

    public Class<? extends LastUpdate> getLastUpdateClass() {
        return lastUpdateClass;
    }

    public void setLastUpdateClass(Class<? extends LastUpdate> lastUpdateClass) {
        this.lastUpdateClass = lastUpdateClass;
    }

    public Generator getGenerator() {
        return generator;
    }

    protected synchronized Session buildSession(String keyspaceIn) {
        return sessions.computeIfAbsent(keyspaceIn, key -> getCluster().connect(keyspaceIn));
    }

    protected synchronized void buildCluster() {
        if (cluster != null)
            return;

        PoolingOptions poolingOptions = new PoolingOptions();
        poolingOptions
                .setPoolTimeoutMillis(getPoolTimeout())
                .setCoreConnectionsPerHost(HostDistance.LOCAL, coreConnections)
                .setMaxConnectionsPerHost( HostDistance.LOCAL, maxConnections)
                .setMaxRequestsPerConnection(HostDistance.LOCAL, maxRequests);

        SocketOptions socketOptions = new SocketOptions()
                .setReadTimeoutMillis(getReadTimeout());
        if (getKeepAlive() != null)
            socketOptions.setKeepAlive(getKeepAlive());

        Cluster.Builder builder = Cluster.builder()
                .addContactPoint(host)
                .withPort(port)
                .withPoolingOptions(poolingOptions)
                .withSocketOptions(socketOptions);

        if (builderOptions != null)
            builderOptions.accept(builder);
        cluster = builder.build();
    }

    @Override
    public void initialize(Resources resources) {
        generator.setNamingStrategy(namingStrategy);
        provision.getResourceManager().bind(Generator.class, generator);
        buildCluster();
        try {
            getSession();
        } catch (InvalidQueryException ex) {
            // connect to default keyspace and create a named keyspace
            Session session = getCluster().connect();
            createKeyspaceArguments.put("keyspace", keyspace);
            session.execute(TextBuilder.using(createKeyspace).build(createKeyspaceArguments));
            session.close();
            getSession();
        }

        provision.getResourceManager().rebind(Cluster.class, getCluster());
        provision.getResourceManager().rebind(SessionProvider.class, this);
    }

    public void registerCodec(Class<? extends TypeCodec> codecClass) {
        UDT udt = codecClass.getAnnotation(UDT.class);
        if (udt == null)
            throw new IllegalArgumentException("Codec class does not have @UDT annotation");
        registerCodec(udt.keyspace(), udt.name(), codecClass);
    }

    public void registerCodec(String keyspaceIn, String userType, Class<? extends TypeCodec> codecClass) {
        try {
            if (keyspaceIn == null || keyspaceIn.isEmpty())
                keyspaceIn = this.keyspace;

            CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();
            UserType type = cluster.getMetadata().getKeyspace(keyspaceIn).getUserType(userType);
            if (type == null)
                throw new SystemException("Invalid user type " + userType + " in keyspace " + keyspace);
            TypeCodec<UDTValue> userTypeCodec = codecRegistry.codecFor(type);
            TypeCodec typeCodec = codecClass.getConstructor(TypeCodec.class, Class.class).newInstance(userTypeCodec, Reflection.getParametrizedType(codecClass, 0));
            codecRegistry.register(typeCodec);
            generator.setDataType(Reflection.getParametrizedType(codecClass, 0), userType);
        } catch (Exception e) {
            throw new SystemException(e);
        }
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
    public void onOpen(Resources resources) {
        MappingManager mappingManager;
        Session session;

        if (isSharedSession()) {
            synchronized (this) {
                getCluster().connect(getKeyspace());
                session = getCluster().connect(getKeyspace());
                sharedMappingManager = createMappingManager(session);
            }
            mappingManager = sharedMappingManager;
        } else {
            try {
                mappingManager = cachedManagers.pop();
                session = mappingManager.getSession();
            } catch (NoSuchElementException ex) {
                getCluster().connect(getKeyspace());
                session = getCluster().connect(getKeyspace());
                mappingManager = createMappingManager(session);
            }
        }


        resources.rebind(Session.class, session);
        resources.rebind(MappingManager.class, mappingManager);
        resources.rebind(Cluster.class, getCluster());
        resources.rebind(SessionProvider.class, this);
        Sibyl s = resources.newInstance(Sibyl.class);
        s.setReadConsistency(sibylDefault.getReadConsistency());
        s.setWriteConsistency(sibylDefault.getWriteConsistency());
        s.setSaveNullFields(sibylDefault.isSaveNullFields());
        resources.rebind(Sibyl.class, s);
    }

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
