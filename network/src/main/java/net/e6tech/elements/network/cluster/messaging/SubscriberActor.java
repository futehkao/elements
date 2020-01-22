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

import akka.actor.ActorRef;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import net.e6tech.elements.common.actor.typed.Trait;
import net.e6tech.elements.common.actor.typed.Typed;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;

public class SubscriberActor extends Trait<MessagingEvents, SubscriberActor> {

    private Subscriber subscriber;
    private String topic;

    public SubscriberActor() {
    }

    public SubscriberActor(String topic, Subscriber subscriber) {
        this.subscriber = subscriber;
        this.topic = topic;
    }

    @Override
    protected void initialize() {
        ActorRef mediator = DistributedPubSub.lookup().get(untypedContext().system()).mediator();
        mediator.tell(new DistributedPubSubMediator.Subscribe(topic, untypedRef()), untypedRef());

        getContext().spawnAnonymous(Behaviors.receive(DistributedPubSubMediator.SubscribeAck.class)
                .onMessage(DistributedPubSubMediator.SubscribeAck.class,
                        msg -> {
                            getSystem().log().info("subscribed to {}", msg.toString());
                            return Behaviors.same();
                }).build());
    }

    @SuppressWarnings("unchecked")
    @Typed
    private void publish(MessagingEvents.Publish publish) {
        getContext().getSystem().dispatchers().lookup(DispatcherSelector.defaultDispatcher())
                .execute(() -> subscriber.receive(new Notice(publish.getTopic(), (Serializable) publish.getMessage())));
    }
}
