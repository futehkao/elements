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

package net.e6tech.elements.common.actor.concurrent;

import net.e6tech.elements.common.actor.GenesisActor;
import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.actor.typed.worker.WorkEvents;
import net.e6tech.elements.common.actor.typed.worker.WorkerPool;
import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.resources.Provision;

import java.util.concurrent.Executor;

public class ActorExecutor implements Executor {

    private WorkerPool workerPool;
    private boolean running;
    private Provision provision;
    private WorkerPoolConfig workerPoolConfig;

    public ActorExecutor(Provision provision, WorkerPoolConfig workerPoolConfig) {
        this.provision = provision;
        this.workerPoolConfig = workerPoolConfig;
        this.running = false;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running)
            return;
        Guardian guardian = new GenesisActor(provision, workerPoolConfig).getGuardian();

        if (!guardian.isEmbedded()) {
            // using system guardian.  create a separate worker pool
            workerPool = guardian.childActor(WorkerPool.class)
                    .withProps(guardian.dispatcher(workerPoolConfig.getDispatcher()))
                    .spawnNow(new WorkerPool(workerPoolConfig))
                    .virtualize();
        } else {
            // embedded so just use the default worker pool
            workerPool = guardian.getWorkerPool();
        }
        running = true;
    }

    public synchronized void stop() {
        if (workerPool != null && running) {
            if (workerPool.getGuardian().isEmbedded()) {
                workerPool.getSystem().terminate();
            } else {
                workerPool.talk().stop();
            }
            running = false;
        }
    }

    public synchronized void join() {
        if (workerPool != null)
            workerPool.join();
    }

    public WorkEvents.StatusResponse status() {
        return workerPool.status(new WorkEvents.Status());
    }

    @Override
    public void execute(Runnable command) {
        workerPool.execute(new WorkEvents.RunnableTask(command));
    }

}
