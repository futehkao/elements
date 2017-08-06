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
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;

/**
 * Created by futeh.
 */
class Messaging implements Broadcast {

    private ActorRef messagingRef;
    private String name = "messaging";
    private long timeout = 5000L;

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

    public void start(ActorSystem system) {
        messagingRef = system.actorOf(Props.create(MessagingActor.class, MessagingActor::new), name);
    }

    public void shutdown() {
        if (messagingRef != null) {
            Patterns.ask(messagingRef, PoisonPill.getInstance(), timeout);
        }
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        Patterns.ask(messagingRef, new Events.Subscribe(topic, subscriber), timeout);
    }

    @Override
    public <T extends Serializable> void subscribe(Class<T> topic, Subscriber<T> subscriber) {
        subscribe(topic.getName(), subscriber);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        Patterns.ask(messagingRef, new Events.Unsubscribe(topic, subscriber), timeout);
    }

    @Override
    public void unsubscribe(Class topic, Subscriber subscriber) {
        unsubscribe(topic.getName(), subscriber);
    }

    @Override
    public void publish(String topic, Serializable object) {
        Patterns.ask(messagingRef, new Events.Publish(topic, object), timeout);
    }

    @Override
    public <T extends Serializable> void publish(Class<T> cls, T object) {
        publish(cls.getName(), object);
    }

    public void destination(String destination, Subscriber subscriber) {
        Patterns.ask(messagingRef, new Events.NewDestination(destination, subscriber), timeout);
    }

    public void send(String destination, Serializable object) {
         Patterns.ask(messagingRef, new Events.Send(destination, object), timeout);
    }

}
