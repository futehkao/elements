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
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.e6tech.elements.common.actor.typed.CommonBehavior;
import net.e6tech.elements.common.actor.typed.Typed;

public class RegistryEntry extends CommonBehavior<RegistryEntry, InvocationEvents.Request> {
    private InvocationEvents.Registration registration;

    public RegistryEntry(InvocationEvents.Registration registration) {
        this.registration = registration;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (registration.getSender() != null)
            registration.getSender().tell(this.getSelf());
    }

    @Typed
    private void request(InvocationEvents.Request request) {
        final ActorRef sender = request.getSender();
        final ActorRef self = getSelf();
        try {
            getGuardian().async(() -> {
                try {
                    Object ret = registration.getFunction().apply(self, request.arguments());
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
    }

}
