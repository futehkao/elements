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

package net.e6tech.elements.network.cluster.invocation;

import akka.actor.Status;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import net.e6tech.elements.common.actor.Genesis;

public class RegistryEntry extends AbstractBehavior<InvocationEvents.Request> {
    private InvocationEvents.Registration registration;
    private Genesis genesis;
    private ActorContext<InvocationEvents.Request> context;

    public RegistryEntry(ActorContext<InvocationEvents.Request> context, Genesis genesis, InvocationEvents.Registration registration) {
        this.context = context;
        this.registration = registration;
        this.genesis = genesis;
    }

    @Override
    public Receive<InvocationEvents.Request> createReceive() {
        return newReceiveBuilder()
                .onMessage(InvocationEvents.Request.class, this::request)
                .build();
    }

    private Behavior<InvocationEvents.Request> request(InvocationEvents.Request request) {
        final ActorRef sender = request.getSender();
        final ActorRef self = context.getSelf();
        try {
            genesis.async(() -> {
                try {
                    Object ret = registration.function().apply(self, request.arguments());
                    sender.tell(new InvocationEvents.Response(self, ret));
                } catch (Exception ex) {
                    sender.tell(new Status.Failure(ex));
                }
            }, registration.getTimeout());
        } catch (RuntimeException ex) {
            Throwable throwable = ex.getCause();
            if (throwable == null) throwable = ex;
            sender.tell(new Status.Failure(throwable));
        }
        return Behaviors.same();
    }

}
