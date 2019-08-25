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

import akka.actor.Status;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class Worker extends AbstractBehavior<WorkEvents> {

    private ActorRef pool;
    private ActorContext context;

    public Worker(ActorContext context, ActorRef pool) {
        this.pool = pool;
        this.context = context;
    }

    @Override
    public Receive<WorkEvents> createReceive() {
        return newReceiveBuilder()
                .onMessage(WorkEvents.RunnableTask.class, this::run)
                .onMessage(WorkEvents.CallableTask.class, this::call)
                .build();
    }

    private Behavior<WorkEvents> run(WorkEvents.RunnableTask message) {
        ActorRef self = context.getSelf();
        try {
            message.getRunnable().run();
            message.getSender().tell(new WorkEvents.Response());
        } catch (Exception th) {
            message.getSender().tell(new Status.Failure(th));
        } finally {
            pool.tell(new WorkEvents.IdleWorker(self));
        }
        return Behaviors.same();
    }

    private Behavior<WorkEvents> call(WorkEvents.CallableTask message) {
        ActorRef self = context.getSelf();
        try {
            Object ret = message.getCallable().call();
            message.getSender().tell(new WorkEvents.Response(ret));
        } catch (Exception th) {
            message.getSender().tell(new Status.Failure(th));
        } finally {
            pool.tell(new WorkEvents.IdleWorker(self));
        }
        return Behaviors.same();
    }
}
