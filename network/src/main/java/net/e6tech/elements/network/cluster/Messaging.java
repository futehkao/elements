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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.Patterns;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.subscribe.Subscriber;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class Messaging implements Broadcast, Startable {

    @Inject
    ActorSystem system;

    ActorRef messaging;
    Map<String, Map<Subscriber, ActorRef>> subscribers = new HashMap<>();

    public Messaging() {
    }

    public Messaging(ActorSystem system) {
        this.system = system;
    }

    @Override
    public void start() {
        messaging = system.actorOf(Props.create(MessagingActor.class, () -> new MessagingActor()), "messaging");
    }

    public void shutdown() {
        if (messaging != null) {
            Patterns.ask(messaging, PoisonPill.getInstance(), 5000L);
        }
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        Patterns.ask(messaging, new Events.Subscribe(topic, subscriber), 5000L);
    }

    @Override
    public <T extends Serializable> void subscribe(Class<T> topic, Subscriber<T> subscriber) {
        subscribe(topic.getName(), subscriber);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        Patterns.ask(messaging, new Events.Unsubscribe(topic, subscriber), 5000L);
    }

    @Override
    public void unsubscribe(Class topic, Subscriber subscriber) {
        unsubscribe(topic.getName(), subscriber);
    }

    @Override
    public void publish(String topic, Serializable object) {
        Patterns.ask(messaging, new Events.Publish(topic, object), 5000L);
    }

    @Override
    public <T extends Serializable> void publish(Class<T> cls, T object) {
        publish(cls.getName(), object);
    }

    public void destination(String destination, Subscriber subscriber) {
        Patterns.ask(messaging, new Events.NewDestination(destination, subscriber), 5000L);
    }

    public void send(String destination, Serializable object) {
         Patterns.ask(messaging, new Events.Send(destination, object), 5000L);
    }

}
