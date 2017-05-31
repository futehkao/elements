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
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class Events {

    public static class Subscribe implements Serializable {
        String topic;
        Subscriber subscriber;

        public Subscribe(String topic, Subscriber subscriber) {
            this.topic = topic;
            this.subscriber = subscriber;
        }
    }

    public static class Unsubscribe implements Serializable  {
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
        private String qualifier;
        private Class messageType;
        private Class returnType;

        public RegisterReference(String qualifier, Class messageType, Class returnType) {
            this.qualifier = qualifier;
            this.messageType = messageType;
            this.returnType = returnType;
        }

        public String path() {
            if (qualifier == null) return messageType.getName() + ":" + returnType.getName() ;
            else return messageType.getName() + ":" + returnType.getName()+ ":" + qualifier;
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
        private Function function;

        public Registration(String qualifier, Class messageType, Class returnType, Function function) {
            this.reference = new RegisterReference(qualifier, messageType, returnType);
            this.function = function;
        }

        public Function function() {
            return function;
        }
    }

    public static class Invocation implements Serializable {
        private static final long serialVersionUID = -264975294117974773L;
        private RegisterReference reference;
        private Object message;

        public <T> Invocation(String qualifier, Class<? super T> messageType, T message, Class returnType) {
            this.reference = new RegisterReference(qualifier, messageType, returnType);
            this.message = message;
        }

        public Object message() {
            return message;
        }

        public String path() {
            return reference.path();
        }
    }
}
