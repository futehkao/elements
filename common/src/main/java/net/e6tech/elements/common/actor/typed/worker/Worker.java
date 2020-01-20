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

import akka.actor.Status;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import net.e6tech.elements.common.actor.typed.CommonBehavior;
import net.e6tech.elements.common.actor.typed.Typed;

@SuppressWarnings("unchecked")
public class Worker extends CommonBehavior<WorkEvents> {

    private ActorRef<WorkEvents> pool;

    public Worker(ActorContext<WorkEvents> context, ActorRef<WorkEvents> pool) {
        super(context);
        this.pool = pool;
    }

    @Typed
    private void run(WorkEvents.RunnableTask message) {
        try {
            message.getRunnable().run();
            message.getSender().tell(new WorkEvents.Response());
        } catch (Exception th) {
            message.getSender().tell(new Status.Failure(th));
        } finally {
            pool.tell(new WorkEvents.IdleWorker(getSelf()));
        }
   }

   @Typed
    private void call(WorkEvents.CallableTask message) {
        try {
            Object ret = message.getCallable().call();
            message.getSender().tell(new WorkEvents.Response(ret));
        } catch (Exception th) {
            message.getSender().tell(new Status.Failure(th));
        } finally {
            pool.tell(new WorkEvents.IdleWorker(getSelf()));
        }
    }
}
