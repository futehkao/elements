/*
 * Copyright 2015-2024 Futeh Kao
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

package net.e6tech.elements.common.util.concurrent;

import java.util.concurrent.*;

public class ThreadPoolConfig {

    private static Executor simple = runnable -> new Thread(runnable).start();

    private int coreSize = 2;
    private int maxSize = 50;
    private long keepAlive = 5 * 60 * 1000L; // 5 min
    private String name;
    private Executor externalExecutor;
    private ExecutorService internalExecutor;
    private boolean simpleMode = false;


    public ThreadPoolConfig() {
    }

    public ThreadPoolConfig(int coreSize, int maxSize, long keepAlive) {
        setCoreSize(coreSize);
        setMaxSize(maxSize);
        setKeepAlive(keepAlive);
    }

    public int getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public long getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(long keepAlive) {
        this.keepAlive = keepAlive;
    }

    public ThreadPool newPool() {
        return newPool(null);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSimpleMode() {
        return simpleMode;
    }

    public void setSimpleMode(boolean simpleMode) {
        this.simpleMode = simpleMode;
    }

    public ThreadPool newPool(String name) {
        return new ThreadPool(name, p -> new ThreadPoolExecutor(getCoreSize(), getMaxSize(),
                getKeepAlive(), TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), p));
    }

    public Executor getExecutor() {
        if (externalExecutor != null)
            return externalExecutor;

        if (internalExecutor != null)
            return internalExecutor;

        if (simpleMode)
            return simple;

        internalExecutor = newPool(name);
        return internalExecutor;
    }

    public synchronized void setExecutor(Executor executor) {
        if (executor != internalExecutor) {
            this.externalExecutor = executor;
            setSimpleMode(false);
            shutdown();
        }
    }

    public synchronized void shutdown() {
        if (internalExecutor != null) {
            internalExecutor.shutdown();
            internalExecutor = null;
        }
    }

}
