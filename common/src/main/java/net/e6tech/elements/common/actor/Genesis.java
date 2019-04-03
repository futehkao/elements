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

package net.e6tech.elements.common.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.e6tech.elements.common.actor.pool.Events;
import net.e6tech.elements.common.actor.pool.WorkerPool;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.*;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by futeh.
 */
public class Genesis implements Initializable {
    public static final String WORKER_POOL_DISPATCHER = "worker-pool-dispatcher";
    private String name;
    private String configuration;
    private ActorSystem system;
    private ActorRef workerPool;
    private int initialCapacity = 1;
    private int maxCapacity = Integer.MAX_VALUE;  // ie unlimited
    private long idleTimeout = 10000L;
    private long timeout = 5000L;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public int getInitialCapacity() {
        return initialCapacity;
    }

    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        if (idleTimeout < 0) {
            throw new IllegalArgumentException();
        } else {
            this.idleTimeout = idleTimeout;
        }
    }

    @Override
    public void initialize(Resources resources) {
        if (name == null)
            throw new IllegalStateException("name is null");
        // Create an Akka system
        Config config = null;
        if (configuration != null) {
            config = ConfigFactory.parseString(configuration);
        } else {
            config = ConfigFactory.defaultApplication();
        }
        initialize(config);

        if (resources != null) {
            final ResourceManager resourceManager = resources.getResourceManager();
            resourceManager.addResourceProvider(ResourceProvider.wrap("Genesis", (OnShutdown) this::shutdown));
        }
    }

    public void initialize(Config cfg) {
        if (name == null)
            throw new IllegalStateException("name is null");

        Config config = (cfg != null) ? cfg : ConfigFactory.defaultApplication();

        if (!config.hasPath(WORKER_POOL_DISPATCHER)) {
            config = config.withFallback(ConfigFactory.parseString(
                    WORKER_POOL_DISPATCHER + " {\n" +
                            "  type = Dispatcher\n" +
                            "  thread-pool-executor {\n" +
                            "      keep-alive-time = 60s\n" +
                            "      core-pool-size-min = 8\n" +
                            "      core-pool-size-factor = 5.0\n" +
                            "      # unbounded so that max-pool-size-factor has no effect.\n" +
                            "      task-queue-size = -1\n" +
                            "      allow-core-timeout = on\n" +
                            "    }\n" +
                            "  throughput = 1\n" +
                            "}"
            ));
        }

        // Create an Akka system
        system = ActorSystem.create(name, config);

        // Create a worker pool
        workerPool =  WorkerPool.newPool(system, initialCapacity, maxCapacity, idleTimeout);
    }

    public void shutdown() {
        try {
            Await.ready(system.terminate(), Duration.create(30, TimeUnit.SECONDS));
        } catch (TimeoutException | InterruptedException e) {
            Logger.suppress(e);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public ActorRef getWorkerPool() {
        return workerPool;
    }

    public void setWorkerPool(ActorRef workerPool) {
        this.workerPool = workerPool;
    }

    public ActorSystem getSystem() {
        return system;
    }

    public CompletionStage<Void> async(Runnable runnable) {
        return async(runnable, getTimeout());
    }

    public CompletionStage<Void> async(Runnable runnable, long timeout) {
        Future future = Patterns.ask(workerPool, runnable, timeout);
        return FutureConverters.toJava(future).thenAcceptAsync(ret -> {
            // do nothing
        });
    }

    public <R> CompletionStage<R> async(Callable<R> callable) {
        return async(callable, getTimeout());
    }

    public <R> CompletionStage<R> async(Callable<R> callable, long timeout) {
        Future future = Patterns.ask(workerPool, callable, timeout);
        return FutureConverters.toJava(future).thenApplyAsync(ret -> {
            Events.Response response = (Events.Response) ret;
            return response.getValue();
        });
    }
}
