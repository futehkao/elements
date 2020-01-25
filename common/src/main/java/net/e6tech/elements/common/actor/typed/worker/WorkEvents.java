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
import net.e6tech.elements.common.actor.typed.Ask;

import java.io.Serializable;
import java.util.concurrent.Callable;

@SuppressWarnings("squid:S1948")
public interface WorkEvents {
    class IdleWorker implements WorkEvents, Serializable {
        private static final long serialVersionUID = 3494669944533209616L;
        private ActorRef<WorkEvents> worker;

        public IdleWorker(ActorRef<WorkEvents> worker) {
            this.worker = worker;
        }

        public ActorRef<WorkEvents> getWorker() {
            return worker;
        }
    }

    class ScheduleCleanup implements WorkEvents, Serializable {
        private static final long serialVersionUID = 1391045696378516373L;
    }

    class Cleanup implements WorkEvents, Serializable {
        private static final long serialVersionUID = 1754051340286965211L;
    }

    class Status extends Ask implements WorkEvents, Serializable {
        public Status() {
        }

        public Status(ActorRef actorRef) {
            setSender(actorRef);
        }
    }

    class StatusResponse implements Serializable {
        private int idleCount;
        private int workerCount;
        private int busyCount;
        private int waitCount;

        public int getIdleCount() {
            return idleCount;
        }

        public void setIdleCount(int idleCount) {
            this.idleCount = idleCount;
        }

        public int getWorkerCount() {
            return workerCount;
        }

        public void setWorkerCount(int workerCount) {
            this.workerCount = workerCount;
        }

        public int getBusyCount() {
            return busyCount;
        }

        public void setBusyCount(int busyCount) {
            this.busyCount = busyCount;
        }

        public int getWaitCount() {
            return waitCount;
        }

        public void setWaitCount(int waitCount) {
            this.waitCount = waitCount;
        }
    }

    class RunnableTask extends Ask implements WorkEvents, Serializable {
        private static final long serialVersionUID = -8279583557717048047L;
        private Runnable runnable;

        public RunnableTask(Runnable runnable) {
            this.runnable = runnable;
        }

        public RunnableTask(ActorRef sender, Runnable runnable) {
            setSender(sender);
            this.runnable = runnable;
        }

        public Runnable getRunnable() {
            return runnable;
        }
    }

    class CallableTask extends Ask implements WorkEvents, Serializable {
        private static final long serialVersionUID = -5567603118967175000L;
        private Callable callable;

        public CallableTask(Callable callable) {
           this.callable = callable;
        }

        public CallableTask(ActorRef sender, Callable callable) {
            setSender(sender);
            this.callable = callable;
        }

        public Callable getCallable() {
            return callable;
        }
    }

    class Response implements WorkEvents {
        Object value;

        public Response() {
        }

        public Response(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}
