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

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.RecipientRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import net.e6tech.elements.common.actor.typed.worker.WorkEvents;
import net.e6tech.elements.common.actor.typed.worker.WorkerPool;
import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("unchecked")
public class Guardian extends Trait<Void, Guardian> {
    private static final Logger logger = Logger.getLogger();

    private WorkerPool workerPool;
    private long timeout = 5000L;
    private String name = "galaxy";

    public static Guardian create(String name, long timeout, Config config, WorkerPoolConfig workerPoolConfig) {
        // Create an Akka system
        long start = System.currentTimeMillis();
        Behavior<Void> main = Behaviors.setup(
                context -> new Guardian().bootstrap(context, name, timeout, workerPoolConfig));
        ActorSystem<ExtensionEvents> sys = (ActorSystem) ActorSystem.create(main, name, config);
        try {
            ExtensionEvents.ExtensionsResponse extensions = getExtensions(sys, timeout, sys.scheduler());
            // ask sys to give back an instance of Guardian
            Guardian guardian = extensions.getOwner();
            logger.info("Staring Guardian in {}ms", System.currentTimeMillis() - start);
            return guardian;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private static ExtensionEvents.ExtensionsResponse getExtensions(RecipientRef<ExtensionEvents> recipient, long timeout, Scheduler scheduler) {
        CompletionStage<ExtensionEvents.ExtensionsResponse> stage = AskPattern.ask(recipient, ExtensionEvents.Extensions::new,
                java.time.Duration.ofMillis(timeout), scheduler);
        try {
            return stage.toCompletableFuture().get();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    protected Behavior<Void> bootstrap(ActorContext<Void> context, String name, long timeout, WorkerPoolConfig workerPoolConfig) {
        setName(name);
        setTimeout(timeout);
        setup(context, this);
        workerPool = childActor(WorkerPool.class).withName(workerPoolConfig.getName())
                .spawnNow(new WorkerPool(workerPoolConfig));
        return getBehavior();
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

    public CompletionStage<Void> async(Runnable runnable, long timeout) {
        return workerPool.talk(timeout).ask(ref -> new WorkEvents.RunnableTask(ref, runnable));
    }

    public <R> CompletionStage<R> async(Callable<R> callable, long timeout) {
        return workerPool.talk(timeout).ask(ref -> new WorkEvents.CallableTask(ref, callable));
    }
}
