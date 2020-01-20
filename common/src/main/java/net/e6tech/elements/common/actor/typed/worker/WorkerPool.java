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
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import net.e6tech.elements.common.actor.typed.CommonBehavior;
import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.actor.typed.Typed;
import net.e6tech.elements.common.reflection.Reflection;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

@SuppressWarnings("unchecked")
public class WorkerPool extends CommonBehavior<WorkEvents> {

    private boolean cleanupScheduled = false;
    private Set<ActorRef<WorkEvents>> workers = new LinkedHashSet<>();
    private Set<ActorRef<WorkEvents>> idleWorkers = new LinkedHashSet<>();
    private LinkedList<Task> waiting = new LinkedList<>();
    protected WorkerPoolConfig config = new WorkerPoolConfig();

    public WorkerPool(ActorContext<WorkEvents> context, WorkerPoolConfig config) {
        super(context);
        Reflection.copyInstance(this.config, config);
    }

    @Override
    public void setup(Guardian guardian) {
        super.setup(guardian);
        for (int i = 0; i < config.getInitialCapacity(); i++) {
            newWorker();
        }
    }

    @SuppressWarnings("squid:S2175")
    @Typed
    private Behavior<WorkEvents> terminated(Terminated event) {
        workers.remove(event.ref());
        idleWorkers.remove(event.ref());
        return Behaviors.same();
    }

    @Typed
    private void newRunnable(WorkEvents.RunnableTask event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
            ActorRef worker = iterator.next();
            iterator.remove();
            worker.tell(event);
        } else if (workers.size() < config.getMaxCapacity()) {
            // put in waiting list.  When a work becomes idled, it will be picked up
            waiting.add(new Task(event.getSender(), event));
            newWorker();
        } else {
            waiting.add(new Task(event.getSender(), event));
        }
    }

    @Typed
    private void newCallable(WorkEvents.CallableTask event) {
        if (!idleWorkers.isEmpty()) {
            Iterator<ActorRef<WorkEvents>> iterator = idleWorkers.iterator();
            ActorRef<WorkEvents> worker = iterator.next();
            iterator.remove();
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
        ActorRef<WorkEvents> worker = childActor(Worker.class).spawn(ctx -> new Worker(ctx, getSelf()));
        workers.add(worker);
        getContext().watch(worker);
        idle(worker);
    }

    @Typed
    private void idle(WorkEvents.IdleWorker event) {
        idle(event.getWorker());
    }

    private void idle(ActorRef<WorkEvents> worker) {
        if (!waiting.isEmpty()) {
            WorkerPool.Task task = waiting.removeFirst();
            worker.tell(task.getWork());
        } else {
            idleWorkers.add(worker);
            cleanup(new WorkEvents.Cleanup());
        }
    }

    @Typed
    private void cleanup(WorkEvents.Cleanup message) {
        if (cleanupScheduled)
            return;
        if (config.getIdleTimeout() == 0)
            return;
        final Duration interval = Duration.ofMillis(config.getIdleTimeout());
        getContext().scheduleOnce(interval, getSelf(), new WorkEvents.Cleanup());
        cleanupScheduled = true;
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
