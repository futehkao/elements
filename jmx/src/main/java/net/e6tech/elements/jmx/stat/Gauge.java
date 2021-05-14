/*
 * Copyright 2015-2021 Futeh Kao
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

package net.e6tech.elements.jmx.stat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.logging.LogLevel;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiFunction;

public class Gauge implements Initializable {
    private static ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("Gauge-" + thread.getId());
        thread.setDaemon(true);
        return thread;
    });
    private static final Logger defaultLogger = Logger.getLogger();
    private long period = 5 * 60 * 1000L; // 5 min
    private int cacheInitialCapacity = 20;
    private int cacheMaxSize = 2000;
    private Cache<String, Measurement> measurements;
    private Logger logger = defaultLogger;
    private LogLevel logLevel = LogLevel.INFO;
    private long windowWidth = 5 * 60 * 1000L;
    private int maxCount = 1000;
    private boolean enabled = true;
    private BiFunction<String, Measurement, String> format = (key, measurement) -> String.format("key=%s: %s", key, measurement);
    private long lastRun = 0;
    private ScheduledFuture<?> future;

    public static void initTimerPool(int threadCount) {
        ScheduledExecutorService tmp = executor;
        executor = Executors.newScheduledThreadPool(threadCount, r -> {
            Thread thread = new Thread(r);
            thread.setName("Gauge-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
        List<Runnable> tasks = tmp.shutdownNow();
        tasks.forEach(Runnable::run);
    }

    @Override
    public synchronized void initialize(Resources resources) {
        initCache();
    }

    private synchronized void initCache() {
        measurements = CacheBuilder.newBuilder()
                .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
                .initialCapacity(cacheInitialCapacity)
                .maximumSize(cacheMaxSize)
                .expireAfterAccess(2 * period + 10000L, TimeUnit.MILLISECONDS)
                .build();
    }

    private synchronized void schedule(boolean alwaysScheduled) {
        if (future == null || future.isCancelled() || future.isDone() || alwaysScheduled) {
            Runnable task = new GaugeTask();
            if (lastRun + period <= System.currentTimeMillis())
                lastRun = System.currentTimeMillis();
            lastRun = lastRun + period;
            long delay = lastRun - System.currentTimeMillis();
            if (delay < 0)
                delay = 0;
            future = executor.schedule(task, delay, TimeUnit.MILLISECONDS);
        }
    }
    public void cancel() {
        if (future != null)
            future.cancel(false);
    }

    public void print() {
        for (Map.Entry<String, Measurement> entry : measurements.asMap().entrySet()) {
            logger.log(logLevel, format.apply(entry.getKey(), entry.getValue()), null);
        }
    }

    public int getCacheInitialCapacity() {
        return cacheInitialCapacity;
    }

    public void setCacheInitialCapacity(int cacheInitialCapacity) {
        this.cacheInitialCapacity = cacheInitialCapacity;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public Cache<String, Measurement> getMeasurements() {
        return measurements;
    }

    public void setMeasurements(Cache<String, Measurement> measurements) {
        this.measurements = measurements;
    }

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public long getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(long windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BiFunction<String, Measurement, String> getFormat() {
        return format;
    }

    public void setFormat(BiFunction<String, Measurement, String> format) {
        this.format = format;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public void add(String label, long duration) {
        Measurement measurement = getMeasurement(label);
        if (measurement != null)
            measurement.add(duration);
        schedule(false);
    }

    public synchronized Measurement getMeasurement(String key) {
        try {
            if (measurements == null) {
                initCache();
            }
            return measurements.get(key, () -> {
                Measurement m = new Measurement();
                m.setEnabled(enabled);
                m.setWindowWidth(windowWidth);
                m.setWindowMaxCount(maxCount);
                return m;
            });
        } catch (ExecutionException e) {
            return measurements.getIfPresent(key);
        }
    }

    class GaugeTask implements Runnable {
        public void run() {
            synchronized (Gauge.this) {
                Map<String, Measurement> map = measurements.asMap();
                map.values().removeIf(val -> val.getCount() <= 0);

                if (!map.isEmpty()) {
                    schedule(true);
                }
            }
            print();
        }
    }
}
