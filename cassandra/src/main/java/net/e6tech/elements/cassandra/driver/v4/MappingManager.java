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

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.internal.core.util.concurrent.LazyReference;
import com.datastax.oss.driver.internal.mapper.DefaultMapperContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.etl.Inspector;
import org.ehcache.impl.internal.concurrent.ConcurrentHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class MappingManager {
    protected final SessionProvider sessionProvider;
    protected final CqlSession session;
    private String keyspace;
    private ConcurrentMap<Class, MapperBuilder> builders = new ConcurrentHashMap<>();

    public MappingManager(SessionProvider sessionProvider, CqlSession session, String keyspace) {
        this.sessionProvider = sessionProvider;
        this.session = session;
        this.keyspace = keyspace;
        if (keyspace == null)
            this.keyspace = session.getKeyspace().map(CqlIdentifier::asInternal).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public <T> Mapper<T> getMapper(Class<T> cls) {
        MapperBuilder builder = builders.computeIfAbsent(cls, key -> {
            MapperBuilder b = new MapperBuilder();
            return b.build(cls, keyspace);
        });
        return builder.getMapper();
    }

    public String getKeyspace() {
        return keyspace;
    }

    @SuppressWarnings("unchecked")
    private class MapperBuilder<T> {
        protected Map<Object, Object> customState;
        private LazyReference<Mapper<T>> cache;

        protected MapperBuilder() {
            this.customState = new HashMap<>();
        }

        @NonNull
        public MapperBuilder withCustomState(@Nullable Object key, @Nullable Object value) {
            customState.put(key, value);
            return this;
        }

        public MapperBuilder build(Class cls, String keyspace) {
            Inspector inspector = sessionProvider.getInspector(cls);
            CqlIdentifier tableId = CqlIdentifier.fromInternal(inspector.tableName());
            CqlIdentifier keyspaceId = CqlIdentifier.fromInternal(keyspace);
            DefaultMapperContext context = new DefaultMapperContext(session, customState)
                    .withKeyspaceAndTable(keyspaceId, tableId);
            this.cache = new LazyReference<>(() -> MapperImpl.init(context, cls, inspector));
            return this;
        }

        public Mapper<T> getMapper() {
            return cache.get();
        }
    }
}
