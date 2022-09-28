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

package net.e6tech.elements.common.actor.typed.worker;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.ActorContext;
import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.actor.typed.Receptor;
import net.e6tech.elements.common.actor.typed.Typed;
import net.e6tech.elements.common.reflection.Reflection;

import java.time.Duration;
import java.util.*;

@SuppressWarnings("unchecked")
public class WorkerPool extends Receptor<WorkEvents, WorkerPool> {

    private boolean cleanupScheduled = false;
    private Set<ActorRef<WorkEvents>> workers = new LinkedHashSet<>();
    private Set<ActorRef<WorkEvents>> idleWorkers = new LinkedHashSet<>();
    private Set<ActorRef<WorkEvents>> busyWorkers = new LinkedHashSet<>();
    private LinkedList<Task> waiting = new LinkedList<>();
    protected WorkerPoolConfig config = new WorkerPoolConfig();
    private boolean stopped = true;

    // for proxy
    public WorkerPool() {
    }

    public WorkerPool(WorkerPoolConfig config) {
        Reflection.copyInstance(this.config, config);
    }

    public synchronized void join() {
        while (!busyWorkers.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void stopped() {
        while (!stopped) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public Behavior<WorkEvents> setup(ActorContext<WorkEvents> ctx, Guardian guardian) {
        super.setup(ctx, guardian);
        for (int i = 0; i < config.getInitialCapacity(); i++) {
            newWorker();
        }
        stopped = false;
        return getBehavior();
    }

    @Typed
    public WorkEvents.StatusResponse status(WorkEvents.Status message) {
        WorkEvents.StatusResponse response = new WorkEvents.StatusResponse();
        response.setIdleCount(idleWorkers.size());
        response.setWorkerCount(workers.size());
        response.setBusyCount(busyWorkers.size());
        response.setWaitCount(waiting.size());
        return response;
    }

    @Typed
    public void execute(WorkEvents.RunnableTask event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
            if (iterator.hasNext()) {
                ActorRef worker = iterator.next();
                iterator.remove();
                busyWorkers.add(worker);
                worker.tell(event);
                return;
            }
        }

        if (workers.size() < config.getMaxCapacity()) {
            // put in waiting list.  When a work becomes idled, it will be picked up
            waiting.add(new Task(event.getSender(), event));
            newWorker();
        } else {
            waiting.add(new Task(event.getSender(), event));
        }
    }

    @Typed
    public void execute(WorkEvents.CallableTask event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
            ActorRef<WorkEvents> worker = iterator.next();
            iterator.remove();
            busyWorkers.add(worker);
            worker.tell(event);
        } else if (workers.size() < config.getMaxCapacity()) {
            // put in waiting list.  When a work becomes idled, it will be picked up
            waiting.add(new Task(event.getSender(), event));
            newWorker();
        } else {
            waiting.add(new Task(event.getSender(), event));
        }
    }

    private void newWorker() {
        ActorRef<WorkEvents> worker = childActor(Worker.class).spawn(new Worker(getSelf()));
        workers.add(worker);
        idle(worker);
    }

    @Typed
    private void idle(WorkEvents.IdleWorker event) {
        idle(event.getWorker());
    }

    private void idle(ActorRef<WorkEvents> worker) {
        if (!waiting.isEmpty()) {
            // there are tasks waiting.  Instead of idle this work, make it do work.
            WorkerPool.Task task = waiting.removeFirst();
            busyWorkers.add(worker);
            worker.tell(task.getWork());
        } else {
            busyWorkers.remove(worker);
            idleWorkers.add(worker);
            if (busyWorkers.isEmpty()) {
                synchronized (this) {
                    notifyAll();
                }
            }
            scheduleCleanup(new WorkEvents.ScheduleCleanup());
        }
    }

    @Typed
    private void scheduleCleanup(WorkEvents.ScheduleCleanup message) {
        if (cleanupScheduled)
            return;
        if (config.getIdleTimeout() == 0)
            return;
        if (idleWorkers.size() <= config.getInitialCapacity())
            return;
        final Duration interval = Duration.ofMillis(config.getIdleTimeout());
        getContext().scheduleOnce(interval, getSelf(), new WorkEvents.Cleanup());
        cleanupScheduled = true;
    }

    @Typed
    private void cleanup(WorkEvents.Cleanup message) {
        int count = idleWorkers.size() - config.getInitialCapacity();
        if (count < 0)
            count = 0;
        Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
        List<ActorRef<WorkEvents>> stopList = new ArrayList<>(count);

        // collect workers to be stopped
        for (int i = 0; i < count; i++) {
            if (iterator.hasNext()) {
                ActorRef worker = iterator.next();
                iterator.remove();
                stopList.add(worker);
            }
        }

        // removed them from workers and then send stop
        for (ActorRef<WorkEvents> worker : stopList) {
            workers.remove(worker);
            idleWorkers.remove(worker);
            getContext().stop(worker);
        }
        cleanupScheduled = false;
    }

    @Typed
    synchronized void stopped(PostStop message) {
        stopped = true;
        notifyAll();
    }

    private class Task {
        ActorRef sender;
        WorkEvents work;

        public Task(ActorRef sender, WorkEvents work) {
            this.sender = sender;
            this.work = work;
        }

        public ActorRef getSender() {
            return sender;
        }

        public WorkEvents getWork() {
            return work;
        }
    }
}
