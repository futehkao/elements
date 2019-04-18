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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.UnitOfWork;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class ETLContext {
    public static final long DAY = 24 * 60 * 60 * 1000L;
    public static final long HOUR = 60 * 60 * 1000L;
    public static final long MINUTE = 60 * 1000L;
    public static final long SECOND = 60 * 1000L;
    public static final long MONTH = DAY * 30;
    public static final long TIME_LAG = 5 * 60 * 1000L;
    public static final int BATCH_SIZE = 1000;

    private Provision provision;
    private int batchSize = BATCH_SIZE;
    private long timeLag = TIME_LAG;
    private int importedCount;
    private String extractorName;
    private boolean extractAll = true;
    private Class sourceClass;
    private TimeUnit timeUnit;
    private boolean initialized = false;
    private long startTime = System.currentTimeMillis();
    private Sibyl sibyl;
    private Class<LastUpdate> lastUpdateClass;
    private LastUpdate lastUpdate;

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
        if (provision != null) {
            sibyl = provision.getInstance(Sibyl.class);
        }
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

    public boolean isExtractAll() {
        return extractAll;
    }

    public void setExtractAll(boolean extractAll) {
        this.extractAll = extractAll;
    }

    public <T> Mapper<T> getMapper(Class<T> cls) {
        return sibyl.getMapper(cls);
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

    public Class<LastUpdate> getLastUpdateClass() {
        return lastUpdateClass;
    }

    public void setLastUpdateClass(Class<LastUpdate> lastUpdateClass) {
        this.lastUpdateClass = lastUpdateClass;
    }

    public Class getPartitionKeyType() {
        initialize();
        return getInspector(getSourceClass()).getPartitionKeyClass(0);
    }

    public void reset() {
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
        return getProvision().newInstance(Async.class);
    }

    public Async createAsync(String query) {
        if (sibyl != null)
            return sibyl.createAsync(query);
        return getProvision().newInstance(Async.class).prepare(query);
    }

    public Async createAsync(PreparedStatement stmt) {
        if (sibyl != null)
            return sibyl.createAsync(stmt);
        return getProvision().newInstance(Async.class).prepare(stmt);
    }

    public <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls, Mapper.Option... options) {
        Sibyl s = provision.getInstance(Sibyl.class);
        return s.save(list, cls, options);
    }

    public <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls) {
        Sibyl s = provision.getInstance(Sibyl.class);
        return s.get(list, cls);
    }

    public <T, E> Transform<T,E> transform(E[] array, Class<T> targetClass, BiConsumer<Transform<T, E>, E> consumer) {
        Transform<T, E> keyMap = new Transform<>(this, targetClass);
        for (E e : array) {
            consumer.accept(keyMap, e);
        }
        return keyMap.load();
    }

    public PrimaryKey getPrimaryKey(Object instance) {
        return getInspector(instance.getClass()).getPrimaryKey(instance);
    }

    public void setPrimaryKey(PrimaryKey key, Object instance) {
        getInspector(instance.getClass()).setPrimaryKey(key, instance);
    }

    public String tableName() {
        return getInspector(getSourceClass()).tableName();
    }

    public void saveLastUpdate(LastUpdate lastUpdate) {
        if (lastUpdateClass == null)
            lastUpdateClass = getProvision().open().apply(Resources.class,
                resources -> (Class) resources.getInstance(SessionProvider.class).getLastUpdateClass());
        getMapper(lastUpdateClass).save(lastUpdate);
        this.lastUpdate = lastUpdate;
    }

    public LastUpdate lookupLastUpdate() {
        if (lastUpdate != null)
            return lastUpdate;
        if (lastUpdateClass == null)
            lastUpdateClass = getProvision().open().apply(Resources.class,
                    resources -> (Class) resources.getInstance(SessionProvider.class).getLastUpdateClass());
        lastUpdate = open().apply(Resources.class, resources -> {
            return resources.getInstance(MappingManager.class).mapper(lastUpdateClass).get(extractor());
        });
        return lastUpdate;
    }

    public LastUpdate getLastUpdate() {
        String name = extractor();
        lookupLastUpdate();

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
        LastUpdate l = getLastUpdate();
        return getGenerator().getDataValue(l.getDataType(), l.getLastUpdate());
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
        return provision.getInstance(Sibyl.class).getInspector(cls);
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
