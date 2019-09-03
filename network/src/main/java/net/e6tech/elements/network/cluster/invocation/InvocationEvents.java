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

package net.e6tech.elements.network.cluster.invocation;

import akka.actor.typed.ActorRef;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import net.e6tech.elements.common.util.CompressionSerializer;
import net.e6tech.elements.common.util.SystemException;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.BiFunction;

@SuppressWarnings({"squid:S194", "squid:S1948"})
public interface InvocationEvents extends Serializable {

    class RegisterReference implements Serializable {
        private static final long serialVersionUID = 6460401252394771795L;
        private String path;

        public RegisterReference() {
        }

        public RegisterReference(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    class Registration implements InvocationEvents {
        private RegisterReference reference;
        private BiFunction<ActorRef, Object[], Object> function;
        private ActorRef sender;

        public Registration(String path, BiFunction<ActorRef, Object[], Object> function) {
            this.reference = new RegisterReference(path);
            this.function = function;
        }

        public Registration(ActorRef sender, String path, BiFunction<ActorRef, Object[], Object> function) {
            this.sender = sender;
            this.reference = new RegisterReference(path);
            this.function = function;
        }

        public ActorRef getSender() {
            return sender;
        }

        public BiFunction<ActorRef, Object[], Object> getFunction() {
            return function;
        }

        public String getPath() {
            return reference.getPath();
        }
    }

    class Request implements InvocationEvents, KryoSerializable {
        private static final long serialVersionUID = -264975294117974773L;
        private transient RegisterReference reference;
        private transient Object[] arguments;
        private ActorRef<InvocationEvents.Response> sender;
        private long timeout = 10000L;

        public Request(ActorRef<InvocationEvents.Response> sender, String path, long timeout, Object[] arguments)  {
            this.sender = sender;
            this.reference = new RegisterReference(path);
            this.arguments = arguments;
            this.timeout = timeout;
        }

        public Object[] arguments() {
            return arguments;
        }

        public ActorRef getSender() {
            return sender;
        }

        public long getTimeout() {
            return timeout;
        }

        public void write(Kryo kryo, Output out) {
            CompressionSerializer serializer = new CompressionSerializer();
            byte[] payload;
            try {
                payload = serializer.toBytes(arguments);
            } catch (Exception e) {
                throw new SystemException(e);
            }
            kryo.writeObjectOrNull(out, payload, byte[].class);
            kryo.writeObjectOrNull(out, reference, RegisterReference.class);
            kryo.writeObjectOrNull(out, sender, ActorRef.class);
            kryo.writeObject(out, timeout);
        }

        @SuppressWarnings("squid:S2674")
        public void read(Kryo kryo, Input in) {
            byte[] buffer = kryo.readObjectOrNull(in, byte[].class);
            reference = kryo.readObjectOrNull(in, RegisterReference.class);
            sender = kryo.readObjectOrNull(in, ActorRef.class);
            timeout = kryo.readObject(in, Long.class);
            try {
                arguments = CompressionSerializer.fromBytes(buffer);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        private void writeObject(java.io.ObjectOutputStream out)
                throws IOException {
            CompressionSerializer serializer = new CompressionSerializer();
            byte[] payload = serializer.toBytes(arguments);
            out.writeInt(payload.length);
            out.write(payload);
            out.writeObject(reference);
            out.writeObject(sender);
        }

        private void readObject(java.io.ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            sender = (ActorRef) in.readObject();
            reference = (RegisterReference) in.readObject();
            int size = in.readInt();
            byte[] buffer = new byte[size];
            in.readFully(buffer);
            arguments = CompressionSerializer.fromBytes(buffer);
        }

        public String getPath() {
            return reference.getPath();
        }
    }

    class Response implements InvocationEvents {
        private Object value;
        private ActorRef responder;

        public Response(ActorRef responder, Object value) {
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

    class Routes implements InvocationEvents {
        private ActorRef sender;
        private RegisterReference reference;

        public Routes(ActorRef sender, String path) {
            this.sender = sender;
            this.reference = new RegisterReference(path);
        }

        public ActorRef getSender() {
            return sender;
        }

        public String getPath() {
            return reference.getPath();
        }
    }
}
