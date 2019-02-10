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

package net.e6tech.elements.network.cluster.simple;


import akka.actor.*;
import akka.cluster.Cluster;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.e6tech.elements.common.resources.NotAvailableException;

import java.util.ArrayList;
import java.util.List;

public class SimpleJob extends AbstractActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.lookup().get(getContext().system());
    List<ActorRef> actors = new ArrayList<>();

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .matchEquals(SimpleMessage.REGISTRATION, message -> {
                getContext().watch(getSender()); // watch for Terminated event
                actors.add(getSender());
                System.out.println("Registering " + getSender());
            })
            .match(Terminated.class, terminated -> {
                actors.remove(terminated.getActor());
            })
            .match(SimpleMessage.class, message -> actors.isEmpty(), message -> {
                getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")), getSelf());

            })
            .match(SimpleMessage.class,  message -> {
                actors.get(0).tell(message, getSender()); // forward
            })
            .build();
    }
}
