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

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.InvalidKeyspaceException;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention;
import com.datastax.oss.driver.internal.core.loadbalancing.DefaultLoadBalancingPolicy;
import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.metadata.TableMetadata;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.TextBuilder;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class SessionProviderV4 extends SessionProvider {

    private static Logger logger = Logger.getLogger();

    private Generator generator = new GeneratorV4();
    private CqlSession session;
    private Map<String, Object> driverOptions = new LinkedHashMap<>();
    private MappingManager mappingManager;
    private String namingConvention;
    private Function<CqlSessionBuilder, CqlSessionBuilder> sessionCustomizer;

    public String getNamingConvention() {
        return namingConvention;
    }

    public void setNamingConvention(String namingConvention) {
        this.namingConvention = namingConvention;
    }

    public Map<String, Object> getDriverOptions() {
        return driverOptions;
    }

    public void setDriverOptions(Map<String, Object> driverOptions) {
        this.driverOptions = driverOptions;
    }

    public Function<CqlSessionBuilder, CqlSessionBuilder> getSessionCustomizer() {
        return sessionCustomizer;
    }

    public void setSessionCustomizer(Function<CqlSessionBuilder, CqlSessionBuilder> customizer) {
        this.sessionCustomizer = customizer;
    }

    @Override
    public TableMetadata getTableMetadata(String keyspaceIn, String tableName) {
        KeyspaceMetadata keyspaceMetadata = session.getMetadata().getKeyspace(getKeyspace(keyspaceIn)).orElse(null);
        if (keyspaceMetadata == null)
            return null;
        com.datastax.oss.driver.api.core.metadata.schema.TableMetadata tableMetadata = keyspaceMetadata.getTable(tableName).orElse(null);
        return (tableMetadata == null) ? null : new TableMetadataV4(generator, tableMetadata);
    }

    @Override
    public Generator getGenerator() {
        return generator;
    }

    protected net.e6tech.elements.cassandra.Session createSession(String keyspaceIn) {
        return Wrapper.wrap(new net.e6tech.elements.cassandra.driver.v4.SessionV4(), session);
    }

    @Override
    protected void initGenerator() {
        GeneratorV4 gen = new GeneratorV4();
        if (getNamingConvention() != null) {
            gen.setNamingConvention(NamingConvention.valueOf(getNamingConvention()));
        }
        generator = gen;
    }

    @Override
    protected void initDriver() {
        if (session != null)
            return;

        ProgrammaticDriverConfigLoaderBuilder configBuilder = DriverConfigLoader.programmaticBuilder();
        for (Map.Entry<String, ?> entry : driverOptions.entrySet()) {
            configBuilder.withString(DefaultDriverOption.valueOf(entry.getKey()), "" + entry.getValue());
        }

        if (driverOptions.get("LOAD_BALANCING_POLICY_CLASS") == null) {
            configBuilder.withString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS, DefaultLoadBalancingPolicy.class.getName());
        }

        DriverConfigLoader loader = configBuilder.build();
        CqlSessionBuilder builder = CqlSession.builder()
                .withConfigLoader(loader)
                .addContactPoint(new InetSocketAddress(getHost(), getPort()));
        try {
            session = getSession(builder, getKeyspace());
        } catch (InvalidKeyspaceException ex) {
            session = getSession(builder, null);

            // create keyspace
            createKeyspaceArguments.put("keyspace", getKeyspace());
            session.execute(TextBuilder.using(createKeyspace).build(createKeyspaceArguments));
            session.close();

            session = getSession(builder, getKeyspace());
        } catch (AllNodesFailedException ex) {
            logger.error("No Cassandra server found at address {}:{}", getHost(), getPort());
            throw ex;
        }
        mappingManager = new MappingManager(this, session, getKeyspace());
    }

    private CqlSession getSession(CqlSessionBuilder builder, String keyspace) {
        CqlSessionBuilder b = builder
                .withKeyspace(keyspace);

        if (sessionCustomizer != null)
            b = sessionCustomizer.apply(b);
        return b.build();
    }

    @Override
    protected void initKeyspace() {
        // initKeyspace is done in initDriver
    }

    @Override
    protected void postInit() {
        // nothing to do here.
    }

    @Override
    public void onOpen(Resources resources) {
        resources.rebind(net.e6tech.elements.cassandra.Session.class, Wrapper.wrap(new net.e6tech.elements.cassandra.driver.v4.SessionV4(), session));
        resources.rebind(MappingManager.class, mappingManager);
        resources.rebind(SessionProvider.class, this);
        Sibyl s = resources.newInstance(SibylV4.class);
        s.setReadOptions(getDefaultReadOptions());
        s.setWriteOptions(getDefaultWriteOptions());
        resources.rebind(Sibyl.class, s);
    }

    @Override
    public void onClosed(Resources resources) {
        // no need to close using V4 driver.  In fact, there is only one session.
    }

    @Override
    public void onShutdown() {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
