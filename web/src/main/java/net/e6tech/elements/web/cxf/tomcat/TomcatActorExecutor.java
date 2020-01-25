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

package net.e6tech.elements.web.cxf.tomcat;

import net.e6tech.elements.common.actor.ActorExecutor;
import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;

import java.util.concurrent.Executor;

public class TomcatActorExecutor implements Executor {
    private ActorExecutor executor;
    private Provision provision;
    protected WorkerPoolConfig workerPoolConfig = new WorkerPoolConfig();

    public TomcatActorExecutor() {
        String name = "tae" + hashCode();
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

    public void start() {
        if (executor != null)
            executor.stop();
        if (getWorkerPoolConfig().getName() == null)
            workerPoolConfig.setName("tae" + hashCode());
        executor = new ActorExecutor(getProvision(), getWorkerPoolConfig());
        executor.start();
    }

    public void stop() {
        if (executor != null)
            executor.stop();
    }

    @Override
    public void execute(Runnable command) {
        if (executor != null)
            executor.execute(command);
        else
            command.run();
    }
}
