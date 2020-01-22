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
import net.e6tech.elements.common.actor.typed.Ask;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;

@SuppressWarnings("squid:S1948")
public interface MessagingEvents extends Serializable {

    class Subscribe implements MessagingEvents {
        private static final long serialVersionUID = 7295040545418105218L;
        String topic;
        Subscriber subscriber;

        public Subscribe(String topic, Subscriber subscriber) {
            this.topic = topic;
            this.subscriber = subscriber;
        }

        public String getTopic() {
            return topic;
        }

        public Subscriber getSubscriber() {
            return subscriber;
        }
    }

    class Unsubscribe implements MessagingEvents  {
        private static final long serialVersionUID = 5224795221846176188L;
        String topic;
        Subscriber subscriber;

        public Unsubscribe() {
        }

        public Unsubscribe(String topic, Subscriber subscriber) {
            this.topic = topic;
            this.subscriber = subscriber;
        }

        public String getTopic() {
            return topic;
        }

        public Subscriber getSubscriber() {
            return subscriber;
        }
    }

    class NewDestination extends Ask implements MessagingEvents {
        private static final long serialVersionUID = -227499564362523104L;
        String destination;
        Subscriber subscriber;

        public NewDestination(ActorRef sender, String destination, Subscriber subscriber) {
            setSender(sender);
            this.destination = destination;
            this.subscriber = subscriber;
        }

        public String getDestination() {
            return destination;
        }

        public Subscriber getSubscriber() {
            return subscriber;
        }

    }

    class RemoveDestination implements MessagingEvents{
        private static final long serialVersionUID = -8552636627374108324L;
        String destination;

        public RemoveDestination(String destination) {
            this.destination = destination;
        }

        public String getDestination() {
            return destination;
        }
    }

    class Publish implements MessagingEvents{
        private static final long serialVersionUID = 3634267916819024147L;
        String topic;
        Object message;

        public Publish(String topic, Object message) {
            this.topic = topic;
            this.message = message;
        }

        public String getTopic() {
            return topic;
        }


        public Object getMessage() {
            return message;
        }
    }

    class Send implements MessagingEvents {
        private static final long serialVersionUID = -2800137846339616601L;
        String destination;
        Object message;

        public Send(String destination, Object message) {
            this.destination = destination;
            this.message = message;
        }

        public String getDestination() {
            return destination;
        }

        public Object getMessage() {
            return message;
        }
    }
}
