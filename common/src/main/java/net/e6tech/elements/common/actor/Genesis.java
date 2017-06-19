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
import akka.actor.Props;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.e6tech.elements.common.actor.pool.WorkerPool;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.concurrent.CompletionStage;

/**
 * Created by futeh.
 */
public class Genesis implements Initializable {
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
        if (name == null) throw new IllegalStateException("name is null");
        // Create an Akka system
        Config config = null;
        if (configuration != null) {
            config = ConfigFactory.parseString(configuration);
        }
        initialize(config);
    }

    public void initialize(Config config) {
        if (name == null) throw new IllegalStateException("name is null");
        // Create an Akka system
        if (config != null) {
            system = ActorSystem.create(name, config);
        } else {
            system = ActorSystem.create(name);
        }

        workerPool =  WorkerPool.newPool(system, initialCapacity, maxCapacity, idleTimeout);
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

    public CompletionStage<Void> async(Runnable runnable, long timeout) {
        Future future = Patterns.ask(workerPool, runnable, timeout);
        return FutureConverters.toJava(future);
    }
}
