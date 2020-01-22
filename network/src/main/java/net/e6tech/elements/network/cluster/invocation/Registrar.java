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
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.GroupRouter;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.actor.typed.Trait;
import net.e6tech.elements.common.actor.typed.Typed;
import net.e6tech.elements.common.resources.NotAvailableException;
import scala.concurrent.ExecutionContextExecutor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.e6tech.elements.network.cluster.invocation.InvocationEvents.*;

public class Registrar extends Trait<InvocationEvents, Registrar> {

    private Map<String, ActorRef<InvocationEvents.Request>> routes = new HashMap<>(); // key is the context@method
    private Map<String, Set<ActorRef<InvocationEvents.Request>>> actors = new ConcurrentHashMap<>();
    private Map<ActorRef<InvocationEvents.Request>, String> actorKeys = new ConcurrentHashMap<>();
    private RegistryImpl registry;

    public Registrar(RegistryImpl registry) {
        this.registry = registry;
    }

    // spawn a RegistryEntry
    @Typed
    private void registration(Registration registration) {
        String dispatcher;
        ExecutionContextExecutor executor = getContext()
                .getSystem()
                .dispatchers()
                .lookup(DispatcherSelector.fromConfig(RegistryImpl.REGISTRY_DISPATCHER));
        if (executor != null) {
            dispatcher = RegistryImpl.REGISTRY_DISPATCHER;
        } else {
            dispatcher = Genesis.WORKER_POOL_DISPATCHER;
        }

        // spawn a child to listen for RegistryEntry
        ServiceKey<InvocationEvents.Request> key = ServiceKey.create(InvocationEvents.Request.class, registration.getPath());
        getContext().spawnAnonymous(Behaviors.setup(
                ctx -> {
                    // Subscribe to Receptionist using key
                    ctx.getSystem().receptionist().tell(Receptionist.subscribe(key, ctx.getSelf().narrow()));

                    // watch for Receptionist.Listing and modify actors and actorKeys
                    // need to be synchronized because the method terminated also modify actors and actorKeys.
                    return Behaviors.receive(Object.class)
                            .onMessage(Receptionist.Listing.class,
                                    msg -> {
                                        synchronized (actors) {
                                            Set<ActorRef<InvocationEvents.Request>> set = actors.getOrDefault(registration.getPath(), Collections.emptySet());
                                            // remove from actorKeys because actors with registration.getPath() will be replaced with a new list
                                            for (ActorRef<InvocationEvents.Request> ref : set) {
                                                actorKeys.remove(ref);
                                            }

                                            // record new actors from msg.getServiceInstance
                                            // for each record, we need to save in actorKeys because the previous step to clear those
                                            // actors from actorKeys.
                                            for (ActorRef<InvocationEvents.Request> ref : msg.getServiceInstances(key)) {
                                                actorKeys.put(ref, registration.getPath());
                                                if (!set.contains(ref)) {
                                                    getContext().watch(ref); // watch for Terminated event
                                                    registry.onAnnouncement(registration.getPath());
                                                }
                                            }
                                            actors.put(registration.getPath(), new LinkedHashSet<>(msg.getServiceInstances(key)));
                                        }
                                        return Behaviors.same();
                                    })
                            .build();
                }
        ));

        // spawn a RegistryEntry and register it with Receptionist using key
        ActorRef<InvocationEvents.Request> registryEntry = this.childActor(RegistryEntry.class)
                .withProps(DispatcherSelector.fromConfig(dispatcher))
                //.afterSetup(child -> getSystem().receptionist().tell(Receptionist.register(key, child.getSelf())))
                .spawn(new RegistryEntry(registration));
        getSystem().receptionist().tell(Receptionist.register(key, registryEntry));

        routes.computeIfAbsent(registration.getPath(),
                k -> {
                    GroupRouter<Request> g = Routers.group(key).withRoundRobinRouting();
                    return getContext().spawnAnonymous(g);
                });
    }

    // Forward request to router
    @SuppressWarnings("unchecked")
    @Typed
    private void request(Request request) {
        ActorRef<InvocationEvents.Request> router = routes.get(request.getPath());
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
        String key;
        synchronized (actors) {
            key = actorKeys.get(actor);
            if (key != null) {
                Set<ActorRef<InvocationEvents.Request>> set = actors.get(key);
                if (set != null) {
                    set.remove(actor);
                }
            }
            actorKeys.remove(actor);
        }

        if (key != null)
            registry.onTerminated(key, actor);
    }

    @SuppressWarnings("unchecked")
    @Typed
    private void routes(Routes message) {
        Set<ActorRef<InvocationEvents.Request>> actorsForKey = this.actors.get(message.getPath());
        if (actorsForKey == null) {
            message.getSender().tell(new Response(getSelf(), Collections.emptySet()));
        } else {
            message.getSender().tell(new Response(getSelf(), actorsForKey));
        }
    }
}
