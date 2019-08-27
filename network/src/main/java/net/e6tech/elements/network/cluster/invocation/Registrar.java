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
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.Props;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.GroupRouter;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.e6tech.elements.common.actor.CommonBehavior;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.actor.Typed;
import net.e6tech.elements.common.resources.NotAvailableException;
import scala.concurrent.ExecutionContextExecutor;

import java.util.*;

import static net.e6tech.elements.network.cluster.invocation.InvocationEvents.*;

public class Registrar extends CommonBehavior<InvocationEvents> {

    private Map<ServiceKey, ActorRef<InvocationEvents.Request>> routes = new HashMap<>(); // key is the context@method
    private Map<ServiceKey, Set<ActorRef<InvocationEvents.Request>>> actors = new HashMap<>();
    private Map<ActorRef<InvocationEvents.Request>, ServiceKey> actorKeys = new HashMap<>();
    private RegistryImpl registry;

    public Registrar(RegistryImpl registry) {
        this.registry = registry;
    }

    // spawn a RegistryEntry
    @Typed
    private void registration(Registration registration) {
        String dispatcher;
        ExecutionContextExecutor executor = getContext().getSystem().dispatchers().lookup(DispatcherSelector.fromConfig(RegistryImpl.REGISTRY_DISPATCHER));
        if (executor != null) {
            dispatcher = RegistryImpl.REGISTRY_DISPATCHER;
        } else {
            dispatcher = Genesis.WORKER_POOL_DISPATCHER;
        }

        // spawn a child to listen for RegistryEntry
        ServiceKey<InvocationEvents.Request> key = ServiceKey.create(InvocationEvents.Request.class, registration.getPath());
        getContext().spawnAnonymous(Behaviors.setup(
                ctx -> {
                    ctx.getSystem().receptionist().tell(Receptionist.subscribe(key, ctx.getSelf().narrow()));
                    return Behaviors.receive(Object.class)
                            .onMessage(Receptionist.Listing.class,
                            (c, msg) -> {
                                Set<ActorRef<InvocationEvents.Request>> set = actors.getOrDefault(key, Collections.emptySet());
                                for (ActorRef<InvocationEvents.Request> ref : msg.getServiceInstances(key)) {
                                    if (!set.contains(ref)) {
                                        getContext().watch(ref); // watch for Terminated event
                                        actorKeys.put(ref, key);
                                        registry.onAnnouncement(key.id());
                                    }
                                }
                                actors.put(key, new LinkedHashSet<>(msg.getServiceInstances(key)));
                                return Behaviors.same();
                            })
                    .build();
                }
        ));

        spawnAnonymous(new RegistryEntry(key, registration),
                        Props.empty().withDispatcherFromConfig(dispatcher));

        routes.computeIfAbsent(key,
                k -> {
                    GroupRouter<InvocationEvents.Request> g = Routers.group(key).withRoundRobinRouting();
                    ActorRef<InvocationEvents.Request> router = getContext().spawnAnonymous(g);
                    return router;
                });
    }

    // Forward request to router
    @Typed
    private void request(Request request) {
        ServiceKey key = ServiceKey.create(InvocationEvents.Request.class, request.getPath());
        ActorRef<InvocationEvents.Request> router = routes.get(key);
        if (router == null) {
            request.getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")));
        } else {
            router.tell(request);
        }
    }

    // received terminated from RegistryEntry
    @Typed
    private void terminated(Terminated terminated) {
        ActorRef actor = terminated.getRef();
        ServiceKey key = actorKeys.get(actor);
        if (key != null) {
            Set<ActorRef<InvocationEvents.Request>> set =  actors.get(key);
            if (set != null) {
                set.remove(actor);
                registry.onTerminated(key.id(), actor);
            }
        }
    }

    @Typed
    private void routes(Routes message) {
        ServiceKey key = ServiceKey.create(InvocationEvents.Request.class, message.getPath());
        Set<ActorRef<InvocationEvents.Request>> actors = this.actors.get(key);
        if (actors == null) {
            message.getSender().tell(new Response(getSelf(), Collections.emptySet()));
        } else {
            message.getSender().tell(new Response(getSelf(), actors));
        }
    }
}
