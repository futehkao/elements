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
import com.datastax.driver.mapping.*;
import com.datastax.driver.mapping.annotations.UDT;
import net.e6tech.elements.cassandra.etl.LastUpdate;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceProvider;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;

import java.util.HashMap;
import java.util.Map;

public class SessionProvider implements ResourceProvider, Initializable {
    private Cluster cluster;

    private String host;
    private int port = 9042;
    private String keyspace;
    private Map<String, Session> sessions = new HashMap<>();
    private Provision provision;
    private Map<String, MappingManager> mappingManager = new HashMap<>();
    private Generator generator = new Generator();
    private int coreConnections = 20;
    private int maxConnections = 200;
    private int maxRequests = 32768;
    private HostDistance distance = HostDistance.LOCAL;
    private NamingStrategy namingStrategy = new DefaultNamingStrategy(NamingConventions.LOWER_CAMEL_CASE, NamingConventions.LOWER_SNAKE_CASE);
    private Class<? extends LastUpdate> lastUpdateClass = LastUpdate.class;
    private boolean saveNullFields = false;

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
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

    public Session getSession() {
        return getSession(keyspace);
    }

    public Session getSession(String keyspaceIn) {
        String ks = keyspaceIn;
        if (ks == null)
            ks = keyspace;
        return sessions.computeIfAbsent(ks, this::buildSession);
    }

    public MappingManager getMappingManager() {
        return getMappingManager(keyspace);
    }

    public MappingManager getMappingManager(String keyspaceIn) {
        return mappingManager.computeIfAbsent(keyspaceIn, key -> {
            DefaultPropertyMapper propertyMapper = new DefaultPropertyMapper();
            propertyMapper.setNamingStrategy(namingStrategy);
            MappingConfiguration conf = MappingConfiguration.builder().withPropertyMapper(propertyMapper).build();
            return new MappingManager(getSession(keyspaceIn), conf);
        });
    }

    public boolean isSaveNullFields() {
        return saveNullFields;
    }

    public void setSaveNullFields(boolean saveNullFields) {
        this.saveNullFields = saveNullFields;
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
                .setCoreConnectionsPerHost(HostDistance.LOCAL, coreConnections)
                .setMaxConnectionsPerHost( HostDistance.LOCAL, maxConnections)
                .setMaxRequestsPerConnection(HostDistance.LOCAL, maxRequests);

        cluster = Cluster.builder().addContactPoint(host).withPort(port)
                .withPoolingOptions(poolingOptions)
                .build();
    }

    @Override
    public void initialize(Resources resources) {
        generator.setNamingStrategy(namingStrategy);
        provision.getResourceManager().bind(Generator.class, generator);
        if (cluster != null)
            return;
        buildCluster();
        try {
            getSession();
        } catch (InvalidQueryException ex) {
            // connect to default keyspace and create a named keyspace
            Session session = getCluster().connect();
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE KEYSPACE IF NOT EXISTS ").append(keyspace);
            sb.append(" WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};");
            session.execute(sb.toString());
            session.close();
            getSession();
        }
        getMappingManager();
        provision.getResourceManager().rebind(Session.class, getSession());
        provision.getResourceManager().rebind(MappingManager.class, getMappingManager());
        provision.getResourceManager().rebind(Cluster.class, getCluster());
        provision.getResourceManager().rebind(SessionProvider.class, this);
        Sibyl sibyl = provision.newInstance(Sibyl.class);
        sibyl.setSaveNullFields(isSaveNullFields());
        provision.getResourceManager().rebind(Sibyl.class, sibyl);
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

    @Override
    public void onOpen(Resources resources) {
    }

    @Override
    public void afterOpen(Resources resources) {
    }

    @Override
    public void onCommit(Resources resources) {
    }

    @Override
    public void afterCommit(Resources resources) {
    }

    @Override
    public void afterAbort(Resources resources) {

    }

    @Override
    public void onAbort(Resources resources) {
    }

    @Override
    public void onClosed(Resources resources) {
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public String getDescription() {
        return null;
    }
}
