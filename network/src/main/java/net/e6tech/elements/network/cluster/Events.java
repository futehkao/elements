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

import akka.actor.Actor;
import akka.actor.ActorRef;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S1948") // we use kryo for serialization.
public class Events {

    private Events() {
    }

    public static class Subscribe implements Serializable {
        private static final long serialVersionUID = 7295040545418105218L;
        String topic;
        Subscriber subscriber;

        public Subscribe(String topic, Subscriber subscriber) {
            this.topic = topic;
            this.subscriber = subscriber;
        }
    }

    public static class Unsubscribe implements Serializable  {
        private static final long serialVersionUID = 5224795221846176188L;
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

    private static class RegisterReference implements Serializable {
        private static final long serialVersionUID = 6460401252394771795L;
        private String path;

        public RegisterReference(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    public static class Announcement implements Serializable {
        private static final long serialVersionUID = 6910153191195648915L;
        private RegisterReference reference;

        public Announcement(Registration register) {
            reference = register.reference;
        }

        public String path() {
            return reference.path();
        }
    }

    public static class Registration {
        private RegisterReference reference;
        private BiFunction<Actor, Object[], Object> function;
        private long timeout;

        public Registration(String path, BiFunction<Actor, Object[], Object> function, long timeout) {
            this.reference = new RegisterReference(path);
            this.function = function;
            this.timeout = timeout;
        }

        public BiFunction<Actor, Object[], Object> function() {
            return function;
        }

        public long timeout() {
            return timeout;
        }
    }

    public static class Invocation implements Serializable {
        private static final long serialVersionUID = -264975294117974773L;
        private RegisterReference reference;
        private Object[] arguments;

        public Invocation(String path, Object[] arguments) {
            this.reference = new RegisterReference(path);
            this.arguments = arguments;
        }

        public Object[] arguments() {
            return arguments;
        }

        public String path() {
            return reference.path();
        }
    }

    public static class Routes {
        private RegisterReference reference;
        private Function<Object[], Object> function;
        private long timeout;

        public Routes(String path) {
            this.reference = new RegisterReference(path);
        }

        public String path() {
            return reference.path();
        }
    }

    public static class Response implements Serializable {
        private Object value;
        private ActorRef responder;

        public Response() {
        }

        public Response(Object value, ActorRef responder) {
            this.value = value;
            this.responder = responder;
        }

        public Object getValue() {
            return value;
        }

        public ActorRef getResponder() {
            return responder;
        }

    }
}
