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

import akka.actor.typed.ActorRef;
import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.common.subscribe.Subscriber;
import net.e6tech.elements.network.cluster.ClusterNode;

import java.io.Serializable;

/**
 * Created by futeh.
 */
public class Messaging implements Broadcast {

    private Guardian guardian;
    private ActorRef<MessagingEvents> messenger;
    private String name = "messaging";
    private long timeout = ClusterNode.DEFAULT_TIME_OUT;
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

    public void start(Guardian guardian) {
        this.guardian = guardian;
        messenger = guardian.childActor(Messenger.class).withName(name).spawn(new Messenger());
    }

    public void shutdown() {
        if (guardian != null) {
            guardian.talk(messenger).stop();
        }
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        guardian.talk(messenger).tell(new MessagingEvents.Subscribe(topic, subscriber));
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        guardian.talk(messenger).tell(new MessagingEvents.Unsubscribe(topic, subscriber));
    }

    @Override
    public void publish(Notice<?> notice) {
        guardian.talk(messenger).tell(new MessagingEvents.Publish(notice.getTopic(), notice.getUserObject()));
    }

    public void destination(String destination, Subscriber subscriber) {
        guardian.talk(messenger).ask(ref -> new MessagingEvents.NewDestination(ref, destination, subscriber));
    }

    public void send(String destination, Serializable object) {
        guardian.talk(messenger).tell(new MessagingEvents.Send(destination, object));
    }
}
