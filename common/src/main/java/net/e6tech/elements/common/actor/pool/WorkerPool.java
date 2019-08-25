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

package net.e6tech.elements.common.actor.pool;

import akka.actor.*;
import net.e6tech.elements.common.actor.Genesis;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Worker pool.  This can only be used within a JVM.
 * <p>
 * Created by futeh.
 */
public class WorkerPool extends AbstractActor {
    private int initialCapacity = 1;
    private int maxCapacity = Integer.MAX_VALUE;  // ie unlimited
    private long idleTimeout = 10000L;
    private boolean cleanupScheduled = false;
    private Set<ActorRef> workers = new LinkedHashSet<>();
    private Set<ActorRef> idleWorkers = new LinkedHashSet<>();
    private LinkedList<Task> waiting = new LinkedList<>();

    public static ActorRef newPool(akka.actor.ActorContext context, int initialCapacity, int maxCapacity, long idleTimeout) {
        return context.actorOf(Props.create(WorkerPool.class, () -> {
            WorkerPool instance = new WorkerPool();
            instance.setInitialCapacity(initialCapacity);
            instance.setMaxCapacity(maxCapacity);
            instance.setIdleTimeout(idleTimeout);
            return instance;
        }));
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
    public void preStart() {
        for (int i = 0; i < initialCapacity; i++) {
            newWorker();
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Events.IdleWorker.class, this::idle)
                .match(Terminated.class, this::terminated)
                .match(Runnable.class, this::newTask)
                .match(Callable.class, this::newTask)
                .match(Events.Cleanup.class, this::cleanup)
                .build();
    }

    private void idle(Events.IdleWorker event) {
        idle(event.getWorker());
    }

    private void idle(ActorRef worker) {
        if (!waiting.isEmpty()) {
            Task task = waiting.removeFirst();
            worker.tell(task.getWork(), task.getSender());
        } else {
            idleWorkers.add(worker);
            cleanup();
        }
    }

    private void terminated(Terminated event) {
        workers.remove(event.actor());
        idleWorkers.remove(event.actor());
    }

    private void newTask(Object event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef> iterator = idleWorkers.iterator();
            ActorRef worker = iterator.next();
            iterator.remove();
            worker.forward(event, getContext());
        } else if (workers.size() < maxCapacity) {
            // put in waiting list.  When a work becomes idled, it will be picked up
            waiting.add(new Task(getSender(), event));
            newWorker();
        } else {
            waiting.add(new Task(getSender(), event));
        }
    }

    private void newWorker() {
        ActorRef worker = getContext().actorOf(Props.create(Worker.class, getSelf()).withDispatcher(Genesis.WORKER_POOL_DISPATCHER));
        workers.add(worker);
        getContext().watch(worker);
        idle(worker);
    }

    // this is for reducing the number of idle workers
    @SuppressWarnings("squid:S1172")
    private void cleanup(Events.Cleanup event) {
        if (idleWorkers.size() > initialCapacity) {
            Iterator<ActorRef> iterator = idleWorkers.iterator();
            int removeCount = idleWorkers.size() - initialCapacity;
            for (int i = 0; i < removeCount; i++) {
                ActorRef worker = iterator.next();
                iterator.remove();
                workers.remove(worker);
                worker.tell(PoisonPill.getInstance(), getSelf());
            }
        }
        cleanupScheduled = false;
    }

    private void cleanup() {
        if (cleanupScheduled)
            return;
        if (idleTimeout == 0)
            return;
        final FiniteDuration interval = Duration.create(idleTimeout, TimeUnit.MILLISECONDS);
        getContext().getSystem().scheduler().scheduleOnce(interval,
                () -> getSelf().tell(new Events.Cleanup(), getSelf()),
                getContext().dispatcher()
        );
        cleanupScheduled = true;
    }

    private class Task {
        ActorRef sender;
        Object work;

        public Task(ActorRef sender, Object work) {
            this.sender = sender;
            this.work = work;
        }

        public ActorRef getSender() {
            return sender;
        }

        public Object getWork() {
            return work;
        }
    }
}
