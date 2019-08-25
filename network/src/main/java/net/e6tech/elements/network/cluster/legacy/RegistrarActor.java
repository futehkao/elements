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

package net.e6tech.elements.network.cluster.legacy;

import akka.actor.*;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.resources.NotAvailableException;
import scala.collection.JavaConverters;

import java.util.*;

/**
 * Created by futeh.
 */
class RegistrarActor extends AbstractActor {
    private Map<String, Router> routes = new HashMap<>(); // key is the context@method
    private Map<ActorRef, List<String>> actors = new HashMap<>(); // key is actor, values is a list of context@method registered by the actor.
    private RegistryImpl registry;

    public RegistrarActor(RegistryImpl registry) {
        this.registry = registry;
    }

    @SuppressWarnings("squid:S3776")
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Events.Registration.class, message -> { // come from Registry.register
                    String dispatcher;
                    if (getContext().getSystem().dispatchers().hasDispatcher(RegistryImpl.REGISTRY_DISPATCHER)) {
                        dispatcher = RegistryImpl.REGISTRY_DISPATCHER;
                    } else {
                        dispatcher = Genesis.WORKER_POOL_DISPATCHER;
                    }
                    Props props = Props.create(RegistryEntryActor.class, () -> new RegistryEntryActor(registry.genesis(), message))
                            .withDispatcher(dispatcher);
                    getContext().actorOf(props); // create the actor
                })
                .match(Events.Announcement.class, message -> { // Receiving an announce event from a newly created RegisterEntry actor.
                    getContext().watch(getSender()); // watch for Terminated event
                    Router router = routes.computeIfAbsent(message.path(), cls -> new Router(new RoundRobinRoutingLogic()));
                    router = router.addRoutee(getSender());
                    routes.put(message.path(), router);
                    List<String> paths = actors.computeIfAbsent(getSender(), ref -> new ArrayList<>());
                    paths.add(message.path());
                    registry.onAnnouncement(message.path());
                })
                .match(Terminated.class, terminated -> { // from getContext().watch(getSender()) in handling Announcement event.
                    ActorRef actor = terminated.getActor();
                    List<String> paths = actors.get(actor);
                    if (paths != null) {
                        for (String path : paths) {
                            Router router = routes.get(path);
                            onTerminated(path, router, actor);
                        }
                        actors.remove(actor);
                    }
                })
                .match(Events.Invocation.class, invocation -> { // from Registry.route().apply(r)
                    Router router = routes.get(invocation.path());
                    if (router == null || router.routees().length() == 0) {
                        getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")), getSelf());
                    } else {
                        router.route(invocation, getSender());
                    }
                })
                .match(Events.Routes.class, r -> { // from Registry.route().apply(r)
                    Router router = routes.get(r.path());
                    if (router == null || router.routees().length() == 0) {
                        getSender().tell(new Events.Response(Collections.emptyList(), getSelf()), getSelf());
                    } else {
                        Collection<Routee> collection = JavaConverters.asJavaCollection(router.routees());
                        getSender().tell(new Events.Response(collection,getSelf()), getSelf());
                    }
                })
                .build();
    }

    private void onTerminated(String path, Router router, ActorRef actor) {
        if (router == null)
            return;

        registry.onTerminated(path, actor);
        Router newRouter = router.removeRoutee(getSender());
        routes.put(path, newRouter);
    }
}
