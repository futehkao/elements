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

package net.e6tech.elements.network.cluster.messaging;

import akka.actor.PoisonPill;
import akka.actor.Status;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import net.e6tech.elements.common.actor.CommonBehavior;
import net.e6tech.elements.common.actor.Typed;
import net.e6tech.elements.common.resources.NotAvailableException;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.util.HashMap;
import java.util.Map;

import static net.e6tech.elements.network.cluster.messaging.MessagingEvents.*;

public class Messenger extends CommonBehavior<MessagingEvents> {

    private static final String SUBSCRIBER_PREFIX = "subscriber-";
    private static final String DESTINATION_PREFIX = "destination-";

    // activate the extension
    private akka.actor.ActorRef mediator;
    private Map<String, Map<Subscriber, ActorRef>> subscribers = new HashMap<>();
    private Map<String, ActorRef> destinations = new HashMap<>();

    @Override
    public void initialize() {
        mediator = DistributedPubSub.lookup().get(untypedContext().system()).mediator();
    }

    @Typed
    private void postStop(PostStop postStop) {
        for (Map<Subscriber, ActorRef> map : subscribers.values()) {
            for (ActorRef ref : map.values()) {
                ref.tell(PoisonPill.getInstance());
            }
        }
        subscribers.clear();

        for (ActorRef ref : destinations.values()) {
            ref.tell(PoisonPill.getInstance());
        }
        destinations.clear();
    }

    @Typed
    private void subscribe(Subscribe event) {
        Map<Subscriber, ActorRef> map = subscribers.computeIfAbsent(event.topic, topic -> new HashMap<>());
        map.computeIfAbsent(event.subscriber,
                sub -> spawn(new SubscriberActor(event.topic, event.subscriber),
                SUBSCRIBER_PREFIX + event.topic + System.identityHashCode(event.subscriber)));
    }

    @Typed
    private void unsubscribe(Unsubscribe event) {
        Map<Subscriber, ActorRef> map = subscribers.get(event.topic);
        if (map != null) {
            ActorRef child = map.get(event.subscriber);
            if (child != null) {
                mediator.tell(new DistributedPubSubMediator.Unsubscribe(event.topic, Adapter.toUntyped(child)), untypedRef());
                child.tell(PoisonPill.getInstance());
                map.remove(event.subscriber);
            }
        }
    }

    @Typed
    private Behavior<MessagingEvents> newDestination(NewDestination event) {
        if (destinations.get(event.destination) != null) {
            event.getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")));
        } else {
            ActorRef dest = spawn(new Destination(event.subscriber), DESTINATION_PREFIX + event.destination);
            destinations.put(event.destination, dest);
        }
        return Behaviors.same();
    }

    @Typed
    private void removeDestination(RemoveDestination event) {
        ActorRef child = destinations.get(event.destination);
        if (child != null) {
            mediator.tell(new DistributedPubSubMediator.Remove(child.path().name()), untypedRef());
            child.tell(PoisonPill.getInstance());
            destinations.remove("/user/" + getSelf().path().name() + "/" + DESTINATION_PREFIX + event.destination);
        }
    }

    @Typed
    private void publish(Publish event) {
        mediator.tell(new DistributedPubSubMediator.Publish(event.getTopic(), event), untypedRef());
    }

    @Typed
    private void send(Send event) {
        mediator.tell(new DistributedPubSubMediator.Send("/user/" + getSelf().path().name() + "/" + DESTINATION_PREFIX + event.destination,
                event, true), untypedRef());
    }

}
