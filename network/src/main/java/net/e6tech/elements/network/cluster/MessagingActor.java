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
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import net.e6tech.elements.common.resources.NotAvailableException;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
class MessagingActor extends AbstractActor {

    private static final String SUBSCRIBER_PREFIX = "subscriber-";
    private static final String DESTINATION_PREFIX = "destination-";

    // activate the extension
    ActorRef mediator = DistributedPubSub.lookup().get(getContext().system()).mediator();
    Map<String, Map<Subscriber, ActorRef>> subscribers = new HashMap<>();
    Map<String, ActorRef> destinations = new HashMap<>();

    @Override
    public void postStop() {
        for (Map<Subscriber, ActorRef> map : subscribers.values()) {
            for (ActorRef ref : map.values()) {
                ref.tell(PoisonPill.getInstance(), getSender());
            }
        }
        subscribers.clear();

        for (ActorRef ref : destinations.values()) {
            ref.tell(PoisonPill.getInstance(), getSender());
        }
        destinations.clear();
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(Events.Subscribe.class, event -> {
                    Map<Subscriber, ActorRef> map = subscribers.computeIfAbsent(event.topic, topic -> new HashMap<>());
                    map.computeIfAbsent(event.subscriber,
                            sub -> getContext().actorOf(Props.create(SubscriberActor.class,
                                    () -> new SubscriberActor(event.topic, event.subscriber)), SUBSCRIBER_PREFIX + event.topic + System.identityHashCode(event.subscriber)));
                })
                .match(Events.Unsubscribe.class, event -> {
                    Map<Subscriber, ActorRef> map = subscribers.get(event.topic);
                    if (map != null) {
                        ActorRef child = map.get(event.subscriber);
                        if (child != null) {
                            mediator.tell(new DistributedPubSubMediator.Unsubscribe(event.topic, child), getSelf());
                            child.tell(PoisonPill.getInstance(), getSender());
                            map.remove(event.subscriber);
                        }
                    }
                })
                .match(Events.NewDestination.class, event -> {
                    if (destinations.get(event.destination) != null) {
                        getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")), getSender());
                    } else {
                        ActorRef dest = getContext().actorOf(Props.create(DestinationActor.class, () -> new DestinationActor(event.subscriber)), DESTINATION_PREFIX + event.destination);
                        destinations.put(event.destination, dest);
                    }
                })
                .match(Events.RemoveDestination.class, event -> {
                    ActorRef child = destinations.get(event.destination);
                    if (child != null) {
                        mediator.tell(new DistributedPubSubMediator.Remove(child.path().name()), getSelf());
                        child.tell(PoisonPill.getInstance(), getSender());
                        destinations.remove("/user/" + getSelf().path().name() + "/" + DESTINATION_PREFIX + event.destination);
                    }
                })
                .match(Events.Publish.class, publish -> mediator.tell(new DistributedPubSubMediator.Publish(publish.topic, publish), getSelf())
                )
                .match(Events.Send.class, send ->
                    mediator.tell(new DistributedPubSubMediator.Send("/user/" + getSelf().path().name() + "/" + DESTINATION_PREFIX + send.destination,
                            send, true), getSender())
                )
                .build();
    }

}