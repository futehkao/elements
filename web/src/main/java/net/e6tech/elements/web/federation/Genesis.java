/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.common.util.concurrent.ThreadPool;
import net.e6tech.elements.web.federation.invocation.InvokerRegistryImpl;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class Genesis {

    private static Logger logger = Logger.getLogger();
    private Cluster cluster = new Cluster();
    private String threadPoolName;
    private int threadCoreSize = 5;
    private int threadMaxSize = 10;
    private long threadKeepAlive = 5 * 60 * 1000L; // 5 min
    private ThreadPool threadPool;
    private Provision provision;

    public String getName() {
        return cluster.getClusterName();
    }

    public void setName(String name) {
        cluster.getClusterName();
    }

    public String getClusterName() {
        return cluster.getClusterName();
    }

    public void setClusterName(String name) {
        cluster.getClusterName();
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public String getThreadPoolName() {
        return threadPoolName;
    }

    public void setThreadPoolName(String threadPoolName) {
        this.threadPoolName = threadPoolName;
    }

    public int getThreadCoreSize() {
        return threadCoreSize;
    }

    public void setThreadCoreSize(int threadCoreSize) {
        this.threadCoreSize = threadCoreSize;
    }

    public int getThreadMaxSize() {
        return threadMaxSize;
    }

    public void setThreadMaxSize(int threadMaxSize) {
        this.threadMaxSize = threadMaxSize;
    }

    public long getThreadKeepAlive() {
        return threadKeepAlive;
    }

    public void setThreadKeepAlive(long threadKeepAlive) {
        this.threadKeepAlive = threadKeepAlive;
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public void initialize(Resources resources) {
        if (StringUtil.isNullOrEmpty(threadPoolName)) {
            threadPool  = ThreadPool.fixedThreadPool(threadPoolName, threadCoreSize, threadMaxSize, threadKeepAlive);
        } else {
            threadPool  = ThreadPool.fixedThreadPool("Genesis", threadCoreSize, threadMaxSize, threadKeepAlive);
        }

        // start cluster.
        if (!StringUtil.isNullOrEmpty(cluster.getHostAddress())) {
            InvokerRegistryImpl registry = provision.newInstance(InvokerRegistryImpl.class);
            registry.setExecutor(threadPool);
            registry.setCollective(cluster);
            registry.initialize(null);
        }

        provision.inject(cluster);
        cluster.start();
    }

    public void shutdown() {
        cluster.shutdown();
    }

    public CompletionStage<Void> async(Runnable runnable) {
        return threadPool.async(runnable).accept(Runnable::run);
    }

    public <R> CompletionStage<R> async(Supplier<R> supplier) {
        return threadPool.async(supplier).apply(Supplier::get);
    }
}
