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

package net.e6tech.elements.cassandra.etl;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.UnitOfWork;
import net.e6tech.elements.common.util.SystemException;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public abstract class ETLContext {
    private static Cache<Class, Inspector> inspectors = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(2000)
            .build();

    public static final long DAY = 24 * 60 * 60 * 1000L;
    public static final long HOUR = 60 * 60 * 1000L;
    public static final long MINUTE = 60 * 1000L;
    public static final long SECOND = 60 * 1000L;
    public static final long MONTH = DAY * 30;

    private Provision provision;
    private int batchSize = 1000;
    private long timeLag = 5 * 60 * 1000L;
    private int importedCount;
    private Map<Class, Mapper> mappers = new HashMap<>();
    private String extractorName;
    private boolean extractAll = true;
    private Class sourceClass;
    private TimeUnit timeUnit;
    private boolean initialized = false;
    private long startTime = System.currentTimeMillis();

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public Generator getGenerator() {
        return getProvision().getInstance(Generator.class);
    }

    public UnitOfWork open() {
        return getProvision().open();
    }

    public Session getSession() {
        return getProvision().open().apply(Resources.class, resources -> resources.getInstance(Session.class));
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getTimeLag() {
        return timeLag;
    }

    public void setTimeLag(long timeLag) {
        this.timeLag = timeLag;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public Class getSourceClass() {
        return sourceClass;
    }

    public void setSourceClass(Class sourceClass) {
        this.sourceClass = sourceClass;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public <T> Mapper<T> getMapper(Class<T> cls) {
        return mappers.computeIfAbsent(cls, key -> open().apply(Resources.class, res -> res.getInstance(MappingManager.class).mapper(cls)));
    }

    public String getExtractorName() {
        return extractorName;
    }

    public void setExtractorName(String extractorName) {
        this.extractorName = extractorName;
    }

    public String extractor() {
        return getExtractorName() != null ? getExtractorName() : getSourceClass().getName();
    }

    public Class getPartitionKeyType() {
        initialize();
        return getInspector(getSourceClass()).getPartitionKeyClass(0);
    }

    public void reset() {
        mappers.clear();
    }

    public TimeUnit getTimeUnit() {
        initialize();
        if (timeUnit == null) {
            String partitionKeyColumn = getInspector(getSourceClass()).getPartitionKeyColumn(0);
            if (partitionKeyColumn != null) {
                if (partitionKeyColumn.endsWith("_day")) {
                    timeUnit = TimeUnit.DAYS;
                } else if (partitionKeyColumn.endsWith("_hour")) {
                    timeUnit = TimeUnit.HOURS;
                } else if (partitionKeyColumn.endsWith("_minute")) {
                    timeUnit = TimeUnit.MINUTES;
                } else if (partitionKeyColumn.endsWith("_second")) {
                    timeUnit = TimeUnit.SECONDS;
                } else if (partitionKeyColumn.endsWith("_milli")) {
                    timeUnit = TimeUnit.MILLISECONDS;
                } else if (partitionKeyColumn.endsWith("_time")) {
                    timeUnit = TimeUnit.MILLISECONDS;
                } else {
                    timeUnit = null;
                }
            }
        }
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Async createAsync() {
        return getProvision().open().apply(Resources.class, Async::new);
    }

    public Async createAsync(String query) {
        return getProvision().open().apply(Resources.class, resources -> new Async(resources, query));
    }

    public Async createAsync(PreparedStatement query) {
        return getProvision().open().apply(Resources.class, resources -> new Async(resources, query));
    }

    public <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls, Mapper.Option... options) {
        Async async = getProvision().open().apply(Resources.class, Async::new);
        Mapper<X> mapper = getMapper(cls);
        try {
            if (options != null && options.length > 0) {
                List<Mapper.Option> all = new ArrayList<>();
                for (Mapper.Option option : options)
                    all.add(option);
                all.add(Mapper.Option.saveNullFields(false));
                mapper.setDefaultSaveOptions(all.toArray(new Mapper.Option[0]));
            } else {
                mapper.setDefaultSaveOptions(Mapper.Option.saveNullFields(false));
            }
            return async.accept(list, mapper::saveAsync);
        } finally {
            mapper.resetDefaultSaveOptions();
        }
    }

    public <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls) {
        Async async = getProvision().open().apply(Resources.class, Async::new);
        Mapper<X> mapper = getMapper(cls);
        mapper.setDefaultGetOptions(Mapper.Option.consistencyLevel(ConsistencyLevel.SERIAL));
        return async.accept(list, k -> mapper.getAsync(k.getKeys()));
    }

    public <T, E> Transform<T,E> transform(E[] array, Class<T> targetClass, BiConsumer<Transform<T, E>, E> consumer) {
        Transform<T, E> keyMap = new Transform<>(this, targetClass);
        for (E e : array) {
            consumer.accept(keyMap, e);
        }
        return keyMap.load();
    }

    public PrimaryKey getPrimaryKey(Object instance) {
        return getInspector((Class) instance.getClass()).getPrimaryKey(instance);
    }

    public void setPrimaryKey(PrimaryKey key, Object instance) {
        getInspector(instance.getClass()).setPrimaryKey(key, instance);
    }

    public String tableName() {
        return getInspector(getSourceClass()).tableName();
    }

    public LastUpdate getLastUpdate() {
        String name = extractor();
        LastUpdate lastUpdate = open().apply(Resources.class, resources -> {
            Class<? extends LastUpdate> cls = resources.getInstance(SessionProvider.class).getLastUpdateClass();
            return resources.getInstance(MappingManager.class).mapper(cls).get(name);
        });

        if (lastUpdate == null) {
            lastUpdate = new LastUpdate();
            lastUpdate.setExtractor(name);
            if (extractAll) {
                // UUID
                if (UUID.class.isAssignableFrom(getPartitionKeyType())) {
                    lastUpdate.setLastUpdate(new UUID(Long.MIN_VALUE, Long.MIN_VALUE).toString());
                } else {
                    lastUpdate.setLastUpdate("0");
                }
            } else {
                lastUpdate.setLastUpdate("" + cutoffOrUpdate(false));
            }
            lastUpdate.setDataType(getGenerator().getDataType(getPartitionKeyType()));
            if (getTimeUnit() != null)
                lastUpdate.setUnit(getTimeUnit().toString());
            else
                lastUpdate.setUnit("1");
        }
        return lastUpdate;
    }

    public Object getLastUpdateValue() {
        LastUpdate lastUpdate = getLastUpdate();
        return getGenerator().getDataValue(lastUpdate.getDataType(), lastUpdate.getLastUpdate());
    }

    public Comparable getCutoff() {
        return cutoffOrUpdate(true);
    }

    private Comparable cutoffOrUpdate(boolean cutoff) {
        if (TimeUnit.DAYS.equals(getTimeUnit()))
            return startTime / DAY;
        else if (TimeUnit.HOURS.equals(getTimeUnit()))
            return startTime / HOUR;
        else if (TimeUnit.MINUTES.equals(getTimeUnit()))
            return startTime / MINUTE;
        else if (TimeUnit.SECONDS.equals(getTimeUnit()))
            return startTime / SECOND;
        else if (TimeUnit.MILLISECONDS.equals(getTimeUnit()))
            return startTime;
        else {
            if (cutoff) {
                if (UUID.class.isAssignableFrom(getPartitionKeyType())) {
                    return new UUID(Long.MAX_VALUE, Long.MAX_VALUE);
                } else {
                    return Long.MAX_VALUE;
                }
            } else {
                if (UUID.class.isAssignableFrom(getPartitionKeyType())) {
                    return new UUID(Long.MIN_VALUE, Long.MIN_VALUE);
                } else {
                    return 0L;
                }
            }
        }
    }

    public Inspector getInspector() {
        return getInspector(getSourceClass());
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

    public void initialize() {
        if (initialized)
            return;
        initialized = true;
        Inspector inspector = getInspector(getSourceClass());

        if (timeUnit == null)
            timeUnit = inspector.getTimeUnit();
    }
}
