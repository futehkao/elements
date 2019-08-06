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
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.driver.Wrapper;
import net.e6tech.elements.cassandra.driver.metadata.TableMetadata;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.TextBuilder;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

public class SessionProviderV4 extends SessionProvider {

    private Generator generator = new GeneratorV4();
    private CqlSession session;
    private Map<String, String> driverOptions = new LinkedHashMap<>();
    private MappingManager mappingManager;

    public Map<String, String> getDriverOptions() {
        return driverOptions;
    }

    public void setDriverOptions(Map<String, String> driverOptions) {
        this.driverOptions = driverOptions;
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
    protected void initDriver() {
        if (session != null)
            return;

        ProgrammaticDriverConfigLoaderBuilder configBuilder = DriverConfigLoader.programmaticBuilder();
        for (Map.Entry<String, String> entry : driverOptions.entrySet()) {
            configBuilder.withString(DefaultDriverOption.valueOf(entry.getKey()), entry.getValue().toString());
        }

        DriverConfigLoader loader = configBuilder.build();
        CqlSessionBuilder builder = CqlSession.builder();
        session = builder
                .withConfigLoader(loader)
                .addContactPoint(new InetSocketAddress(getHost(), getPort()))
                .build();

        mappingManager = new MappingManager(this, session, getKeyspace());
    }

    @Override
    protected void initKeyspace() {
        try {
            session.execute("use " + getKeyspace());
        } catch (InvalidQueryException ex) {
            // connect to default keyspace and create a named keyspace
            createKeyspaceArguments.put("keyspace", getKeyspace());
            session.execute(TextBuilder.using(createKeyspace).build(createKeyspaceArguments));
            session.execute("use " + getKeyspace());
        }
    }

    @Override
    protected void postInit() {

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

    }

    @Override
    public void onShutdown() {

    }
}
