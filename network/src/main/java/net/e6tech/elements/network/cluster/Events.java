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

import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;

/**
 * Created by futeh.
 */
public class Events {

    public static class Subscribe {
        String topic;
        Subscriber subscriber;

        public Subscribe(String topic, Subscriber subscriber) {
            this.topic = topic;
            this.subscriber = subscriber;
        }
    }

    public static class Unsubscribe {
        String topic;
        Subscriber subscriber;

        public Unsubscribe(String topic, Subscriber subscriber) {
            this.topic = topic;
            this.subscriber = subscriber;
        }
    }

    public static class NewDestination {
        String destination;
        Subscriber subscriber;

        public NewDestination(String destination, Subscriber subscriber) {
            this.destination = destination;
            this.subscriber = subscriber;
        }
    }

    public static class RemoveDestination {
        String destination;

        public RemoveDestination(String destination) {
            this.destination = destination;
        }
    }

    public static class Publish {
        String topic;
        Object message;

        public Publish(String topic, Object message) {
            this.topic = topic;
            this.message = message;
        }
    }

    public static class Send {
        String destination;
        Object message;

        public Send(String destination, Object message) {
            this.destination = destination;
            this.message = message;
        }
    }

}
