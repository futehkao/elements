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

import akka.actor.PoisonPill;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.subscribe.Subscriber;
import net.e6tech.elements.network.cluster.messaging.MessagingEvents;
import net.e6tech.elements.network.cluster.messaging.Messenger;

import java.io.Serializable;

/**
 * Created by futeh.
 */
public class Messaging implements Broadcast {

    private ActorRef messagingRef;
    private String name = "messaging";
    private long timeout = ClusterNode.DEFAULT_TIME_OUT;
    private Genesis genesis;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void start(Genesis genesis) {
        this.genesis = genesis;
        messagingRef = genesis.typeActorContext().spawn(Behaviors.<MessagingEvents>setup(ctx -> new Messenger(ctx)), name);
    }

    public void shutdown() {
        if (messagingRef != null) {
            AskPattern.ask(messagingRef, ref -> PoisonPill.getInstance(),
                    java.time.Duration.ofMillis(timeout), genesis.typeActorContext().getSystem().scheduler());
        }
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        AskPattern.ask(messagingRef, ref -> new MessagingEvents.Subscribe(topic, subscriber),
                java.time.Duration.ofMillis(timeout), genesis.typeActorContext().getSystem().scheduler());
    }

    @Override
    public <T extends Serializable> void subscribe(Class<T> topic, Subscriber<T> subscriber) {
        subscribe(topic.getName(), subscriber);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        AskPattern.ask(messagingRef, ref -> new MessagingEvents.Unsubscribe(topic, subscriber),
                java.time.Duration.ofMillis(timeout), genesis.typeActorContext().getSystem().scheduler());
    }

    @Override
    public void unsubscribe(Class topic, Subscriber subscriber) {
        unsubscribe(topic.getName(), subscriber);
    }

    @Override
    public void publish(String topic, Serializable object) {
        AskPattern.ask(messagingRef, ref -> new MessagingEvents.Publish(topic, object),
                java.time.Duration.ofMillis(timeout), genesis.typeActorContext().getSystem().scheduler());
    }

    @Override
    public <T extends Serializable> void publish(Class<T> cls, T object) {
        publish(cls.getName(), object);
    }

    public void destination(String destination, Subscriber subscriber) {
        AskPattern.ask(messagingRef, ref -> new MessagingEvents.NewDestination(messagingRef, destination, subscriber),
                java.time.Duration.ofMillis(timeout), genesis.typeActorContext().getSystem().scheduler());
    }

    public void send(String destination, Serializable object) {
        AskPattern.ask(messagingRef, ref -> new MessagingEvents.Send(destination, object),
                java.time.Duration.ofMillis(timeout), genesis.typeActorContext().getSystem().scheduler());
    }

}
