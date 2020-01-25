/*
 * Copyright 2015-2020 Futeh Kao
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

package net.e6tech.elements.web.cxf.jetty;

import net.e6tech.elements.common.actor.ActorExecutor;
import net.e6tech.elements.common.actor.typed.worker.WorkEvents;
import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.thread.ThreadPool;

public class JettyActorExecutor extends ContainerLifeCycle implements ThreadPool {
    private ActorExecutor executor;
    private Provision provision;
    private WorkerPoolConfig workerPoolConfig = new WorkerPoolConfig();

    public JettyActorExecutor() {
        String name = "jat" + hashCode();
        workerPoolConfig.setName(name);
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public WorkerPoolConfig getWorkerPoolConfig() {
        return workerPoolConfig;
    }

    public void setWorkerPoolConfig(WorkerPoolConfig workerPoolConfig) {
        this.workerPoolConfig = workerPoolConfig;
    }

    @Override
    protected void doStart() throws Exception {
        if (getWorkerPoolConfig().getName() == null)
            workerPoolConfig.setName("jat" + hashCode());
        executor = new ActorExecutor(provision, getWorkerPoolConfig());
        executor.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (executor != null)
            executor.stop();
    }

    @Override
    public void join() {
        if (executor != null)
            executor.join();
    }

    @Override
    public int getThreads() {
        WorkEvents.StatusResponse response = executor.status();
        return response.getWorkerCount();
    }

    @Override
    public int getIdleThreads() {
        WorkEvents.StatusResponse response = executor.status();
        return response.getIdleCount();
    }

    @Override
    public boolean isLowOnThreads() {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        if (executor != null)
            executor.execute(command);
        else
            command.run();
    }
}
