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

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;

/**
 * Created by futeh.
 */
class SubscriberActor extends AbstractActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Subscriber subscriber;

    public SubscriberActor(String topic, Subscriber subscriber) {
        this.subscriber = subscriber;
        ActorRef mediator = DistributedPubSub.lookup().get(getContext().system()).mediator();
        mediator.tell(new DistributedPubSubMediator.Subscribe(topic, getSelf()), getSelf());
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(Events.Publish.class, publish -> getContext().dispatcher().execute(() -> subscriber.receive(new Notice(publish.topic, (Serializable) publish.message))))
                .match(DistributedPubSubMediator.SubscribeAck.class, msg ->
                        log.info("subscribing"))
                .build();
    }
}

