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

package net.e6tech.elements.common.actor.typed;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import net.e6tech.elements.common.actor.typed.worker.WorkEvents;
import net.e6tech.elements.common.actor.typed.worker.WorkerPool;
import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.util.SystemException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public class Guardian extends CommonBehavior<Guardian, SpawnProtocol> {

    private Behavior<SpawnProtocol> main;
    private akka.actor.typed.ActorRef<WorkEvents> workerPool;
    private long timeout = 5000L;
    private String name = "galaxy";

    public Guardian() {
    }

    public Guardian boot(Config config, WorkerPoolConfig workerPoolConfig) {
        Behavior<WorkEvents> pool = WorkerPool.newPool(workerPoolConfig);
        main = Behaviors.setup(
                context -> {
                    setup(this, context);
                    return SpawnProtocol.behavior();
                });

       ActorSystem<SpawnProtocol> system = ActorSystem.create(main, name, config);

        try {
            // start the worker pool actor
            CompletionStage<ActorRef<WorkEvents>> stage = AskPattern.ask(system, // cannot use guardian.getSystem() because context is not set yet
                    replyTo -> new SpawnProtocol.Spawn(pool, workerPoolConfig.getName(), Props.empty(), replyTo),
                    java.time.Duration.ofSeconds(timeout), system.scheduler());
            stage.whenComplete((ref, throwable) -> {
                workerPool = ref;
            });
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CompletionStage<Void> async(Runnable runnable) {
        return async(runnable, timeout);
    }

    public CompletionStage<Void> async(Runnable runnable, long timeout) {
        return AskPattern.ask(workerPool, ref -> new WorkEvents.RunnableTask(ref, runnable),
                java.time.Duration.ofMillis(timeout), getSystem().scheduler());
    }

    public <R> CompletionStage<R> async(Callable<R> callable) {
        return async(callable, timeout);
    }

    public <R> CompletionStage<R> async(Callable<R> callable, long timeout) {
        CompletionStage<R> stage = AskPattern.ask(workerPool, ref -> new WorkEvents.CallableTask(ref, callable),
                java.time.Duration.ofMillis(timeout), getSystem().scheduler());
        return stage;
    }

}
