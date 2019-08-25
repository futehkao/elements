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

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

public class WorkerPool extends AbstractBehavior<WorkEvents> {

    private int initialCapacity = 1;
    private int maxCapacity = Integer.MAX_VALUE;  // ie unlimited
    private long idleTimeout = 10000L;
    private boolean cleanupScheduled = false;
    private Set<ActorRef<WorkEvents>> workers = new LinkedHashSet<>();
    private Set<ActorRef<WorkEvents>> idleWorkers = new LinkedHashSet<>();
    private LinkedList<Task> waiting = new LinkedList<>();

    private ActorContext<WorkEvents> context;

    public static Behavior<WorkEvents> newPool(int initialCapacity, int maxCapacity, long idleTimeout) {
        return Behaviors.setup(ctx -> {
            WorkerPool instance = new WorkerPool(ctx);
            instance.setInitialCapacity(initialCapacity);
            instance.setMaxCapacity(maxCapacity);
            instance.setIdleTimeout(idleTimeout);
            for (int i = 0; i < initialCapacity; i++) {
                instance.newWorker();
            }
            return instance;
        });
    }

    public WorkerPool(ActorContext<WorkEvents> context) {
        this.context = context;
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
        this.idleTimeout = idleTimeout;
    }

    @Override
    public Receive<WorkEvents> createReceive() {
        ReceiveBuilder<WorkEvents> builder = newReceiveBuilder();
        return builder
                .onMessage(WorkEvents.IdleWorker.class, this::idle)
                .onMessage(WorkEvents.RunnableTask.class, this::newTask)
                .onMessage(WorkEvents.CallableTask.class, this::newTask)
                .onMessage(WorkEvents.Cleanup.class, this::cleanup)
                .onSignal(Terminated.class, this::terminated)
                .build();
    }

    private Behavior<WorkEvents> terminated(Terminated event) {
        workers.remove(event.ref());
        idleWorkers.remove(event.ref());
        return Behaviors.same();
    }

    private Behavior<WorkEvents> newTask(WorkEvents.RunnableTask event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
            ActorRef worker = iterator.next();
            iterator.remove();
            worker.tell(event);
        } else if (workers.size() < maxCapacity) {
            // put in waiting list.  When a work becomes idled, it will be picked up
            waiting.add(new Task(event.getSender(), event));
            newWorker();
        } else {
            waiting.add(new Task(event.getSender(), event));
        }
        return Behaviors.same();
    }

    private Behavior<WorkEvents> newTask(WorkEvents.CallableTask event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
            ActorRef worker = iterator.next();
            iterator.remove();
            worker.tell(event);
        } else if (workers.size() < maxCapacity) {
            // put in waiting list.  When a work becomes idled, it will be picked up
            waiting.add(new Task(event.getSender(), event));
            newWorker();
        } else {
            waiting.add(new Task(event.getSender(), event));
        }
        return Behaviors.same();
    }

    private void newWorker() {
        ActorRef<WorkEvents> worker = context.spawnAnonymous(Behaviors.setup(context -> new Worker(context, this.context.getSelf())));
        workers.add(worker);
        context.watch(worker);
        idle(worker);
    }

    private Behavior<WorkEvents> idle(WorkEvents.IdleWorker event) {
        idle(event.getWorker());
        return Behaviors.same();
    }

    private void idle(ActorRef worker) {
        if (!waiting.isEmpty()) {
            WorkerPool.Task task = waiting.removeFirst();
            worker.tell(task.getWork());
        } else {
            idleWorkers.add(worker);
            cleanup(new WorkEvents.Cleanup());
        }
    }

    private Behavior<WorkEvents> cleanup(WorkEvents.Cleanup message) {
        if (cleanupScheduled)
            return Behaviors.same();
        if (idleTimeout == 0)
            return Behaviors.same();
        final Duration interval = Duration.ofMillis(idleTimeout);
        ActorRef self = context.getSelf();
        context.scheduleOnce(interval, self, new WorkEvents.Cleanup());
        cleanupScheduled = true;

        return Behaviors.same();
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
