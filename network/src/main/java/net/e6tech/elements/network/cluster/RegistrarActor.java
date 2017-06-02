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

package net.e6tech.elements.network.cluster;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Router;
import net.e6tech.elements.common.resources.NotAvailableException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by futeh.
 */
class RegistrarActor extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Cluster cluster = Cluster.get(getContext().system());
    private Map<String, Router> services = new HashMap<>();
    private Map<ActorRef, List<String>> actors = new HashMap<>();
    private Registry registry;

    public RegistrarActor(Registry registry) {
        this.registry = registry;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Events.Registration.class, message -> { // come from Registry.register
                    ActorRef entry = getContext().actorOf(Props.create(RegisterEntryActor.class, () -> new RegisterEntryActor(message)));
                })
                .match(Events.Announcement.class, message -> { // Receiving an announce event from a newly created RegisterEntry actor.
                    getContext().watch(getSender()); // watch for Terminated event
                    Router router = services.computeIfAbsent(message.path(), (cls) -> new Router(new RoundRobinRoutingLogic()));
                    router = router.addRoutee(getSender());
                    services.put(message.path(), router);
                    List<String> paths = actors.computeIfAbsent(getSender(), (ref) -> new ArrayList<>());
                    paths.add(message.path());
                    registry.onAnnouncement(message.path());
                })
                .match(Terminated.class, terminated -> { // from getContext().watch(getSender()) in handling Announcement event.
                    ActorRef actor = terminated.getActor();
                    List<String> paths = actors.get(actor);
                    if (paths != null) {
                        for (String path : paths) {
                            Router router = services.get(path);
                            if (router != null) {
                                registry.onTerminated(path, actor);
                                router = router.removeRoutee(getSender());
                                services.put(path, router);
                                if (router.routees().length() == 0) {
                                    registry.onRouteRemoved(path);
                                }
                            }
                        }
                        actors.remove(actor);
                    }

                })
                .match(Events.Invocation.class, invocation -> { // from Registry.route().apply(r)
                    Router router = services.get(invocation.path());
                    if (router == null || router.routees().length() == 0) {
                        getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")), getSelf());
                    } else {
                        router.route(invocation, getSender());
                    }
                })
                .build();
    }
}
