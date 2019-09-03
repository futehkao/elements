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

import net.e6tech.elements.cassandra.SessionProvider;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.UnitOfWork;
import net.e6tech.elements.common.util.SystemException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ETLContext {
    public static final long DAY = 24 * 60 * 60 * 1000L;
    public static final long HOUR = 60 * 60 * 1000L;
    public static final long MINUTE = 60 * 1000L;
    public static final long SECOND = 1000L;
    public static final long MONTH = DAY * 30;  // not to be used for deriving a partition key
    public static final long YEAR = DAY * 365;  // not to be used for deriving a partition key
    public static final long TIME_LAG = 5 * 60 * 1000L; // 5 minutes
    public static final int BATCH_SIZE = 2000;

    private Provision provision;
    private ETLSettings settings = new ETLSettings();
    private int importedCount;
    private String extractorName;
    private String initialUpdate;
    private Class sourceClass;
    private TimeUnit timeUnit;
    private boolean initialized = false;
    private Class<LastUpdate> lastUpdateClass;
    private LastUpdate lastUpdate;

    public ETLContext() {
        settings.batchSize(BATCH_SIZE)
                .timeLag(TIME_LAG)
                .extractAll(true)
                .startTime(System.currentTimeMillis());
    }

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

    public int getBatchSize() {
        return settings.getBatchSize();
    }

    public void setBatchSize(int batchSize) {
        settings.setBatchSize(batchSize);
    }

    public long getTimeLag() {
        return settings.getTimeLag();
    }

    public void setTimeLag(long timeLag) {
        settings.setTimeLag(timeLag);
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
        return settings.getStartTime();
    }

    public void setStartTime(long startTime) {
        settings.setStartTime(startTime);
    }

    public boolean isExtractAll() {
        return settings.getExtractAll();
    }

    public void setExtractAll(boolean extractAll) {
        settings.setExtractAll(extractAll);
    }

    public String getInitialUpdate() {
        return initialUpdate;
    }

    public void setInitialUpdate(String initialUpdate) {
        this.initialUpdate = initialUpdate;
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
        // to be overridden by subclass
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
                } else if (partitionKeyColumn.endsWith("_epoch")) {
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

    public String tableName() {
        return getInspector(getSourceClass()).tableName();
    }

    public void saveLastUpdate(LastUpdate lastUpdate) {
        open().accept(Sibyl.class, sibyl -> {
            if (lastUpdateClass == null)
                lastUpdateClass = getProvision().open().apply(Resources.class,
                        resources -> (Class) resources.getInstance(SessionProvider.class).getLastUpdateClass());
            sibyl.save(lastUpdateClass, lastUpdate);
            this.lastUpdate = lastUpdate;
        });
    }

    public LastUpdate lookupLastUpdate() {
        return open().apply(Sibyl.class, sibyl -> {
            if (lastUpdate != null)
                return lastUpdate;
            if (lastUpdateClass == null)
                lastUpdateClass = (Class) getProvision().getInstance(SessionProvider.class).getLastUpdateClass();
            lastUpdate = open().apply(Sibyl.class, s ->
                    s.get(lastUpdateClass, new PrimaryKey(extractor()))
            );
            return lastUpdate;
        });
    }

    @SuppressWarnings("squid:S3776")
    public LastUpdate getLastUpdate() {
        String name = extractor();
        lookupLastUpdate();

        if (lastUpdate == null) {
            try {
                lastUpdate = getProvision().getInstance(SessionProvider.class).getLastUpdateClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
            lastUpdate.setExtractor(name);
            if (initialUpdate != null) {
                lastUpdate.setLastUpdate(getInitialUpdate());
            } else {
                if (settings.getExtractAll()) {
                    // UUID
                    if (UUID.class.isAssignableFrom(getPartitionKeyType())) {
                        lastUpdate.setLastUpdate(new UUID(Long.MIN_VALUE, Long.MIN_VALUE).toString());
                    } else {
                        lastUpdate.setLastUpdate("0");
                    }
                } else {
                    lastUpdate.setLastUpdate("" + cutoffOrUpdate(false));
                }
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

    @SuppressWarnings("squid:S3776")
    private Comparable cutoffOrUpdate(boolean cutoff) {
        long startTime = settings.getStartTime();
        long timeLag = settings.getTimeLag();
        if (TimeUnit.DAYS.equals(getTimeUnit()))
            return (startTime - timeLag)/ DAY;
        else if (TimeUnit.HOURS.equals(getTimeUnit()))
            return (startTime - timeLag) / HOUR;
        else if (TimeUnit.MINUTES.equals(getTimeUnit()))
            return (startTime - timeLag) / MINUTE;
        else if (TimeUnit.SECONDS.equals(getTimeUnit()))
            return (startTime - timeLag)  / SECOND;
        else if (TimeUnit.MILLISECONDS.equals(getTimeUnit()))
            return startTime - timeLag;
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
        return provision.getInstance(SessionProvider.class).getInspector(cls);
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
