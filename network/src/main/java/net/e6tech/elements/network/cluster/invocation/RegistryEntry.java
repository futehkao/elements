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
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.e6tech.elements.common.actor.CommonBehavior;

import net.e6tech.elements.common.actor.Guardian;

public class RegistryEntry extends CommonBehavior<InvocationEvents.Request> {
    private InvocationEvents.Registration registration;
    private Guardian guardian;
    private ServiceKey<InvocationEvents.Request> key;

    public RegistryEntry(Guardian guardian, ServiceKey<InvocationEvents.Request> key, InvocationEvents.Registration registration) {
        this.registration = registration;
        this.guardian = guardian;
        this.key = key;
    }

    protected void initialize() {
        getSystem().receptionist().tell(Receptionist.register(key, getSelf()));
    }

    @Override
    public Receive<InvocationEvents.Request> createReceive() {
        return newReceiveBuilder()
                .onMessage(InvocationEvents.Request.class, this::request)
                .build();
    }

    private Behavior<InvocationEvents.Request> request(InvocationEvents.Request request) {
        final ActorRef sender = request.getSender();
        final ActorRef self = getSelf();
        try {
            guardian.async(() -> {
                try {
                    Object ret = registration.function().apply(self, request.arguments());
                    sender.tell(new InvocationEvents.Response(self, ret));
                } catch (Exception ex) {
                    sender.tell(new Status.Failure(ex));
                }
            }, request.getTimeout());
        } catch (RuntimeException ex) {
            Throwable throwable = ex.getCause();
            if (throwable == null) throwable = ex;
            sender.tell(new Status.Failure(throwable));
        }
        return Behaviors.same();
    }

}
