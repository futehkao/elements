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
import net.e6tech.elements.common.actor.typed.Ask;
import net.e6tech.elements.common.util.CompressionSerializer;

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

    class Registration extends Ask implements InvocationEvents {
        private RegisterReference reference;
        private BiFunction<ActorRef, Object[], Object> function;

        public Registration(ActorRef sender, String path, BiFunction<ActorRef, Object[], Object> function) {
            this.reference = new RegisterReference(path);
            this.function = function;
            setSender(sender);
        }

        public BiFunction<ActorRef, Object[], Object> getFunction() {
            return function;
        }

        public String getPath() {
            return reference.getPath();
        }
    }

    class Request extends Ask implements InvocationEvents, KryoSerializable {
        private static final long serialVersionUID = -264975294117974773L;
        private transient RegisterReference reference;
        private transient Object[] arguments;
        private long timeout;

        public Request(ActorRef<InvocationEvents.Response> sender, String path, long timeout, Object[] arguments)  {
            this.reference = new RegisterReference(path);
            this.arguments = arguments;
            this.timeout = timeout;
            setSender(sender);
        }

        public Object[] arguments() {
            return arguments;
        }

        public long getTimeout() {
            return timeout;
        }

        public void write(Kryo kryo, Output out) {
            kryo.writeObjectOrNull(out, arguments, Object[].class);
            kryo.writeObjectOrNull(out, reference, RegisterReference.class);
            kryo.writeObjectOrNull(out, getSender(), ActorRef.class);
            kryo.writeObject(out, timeout);
        }

        @SuppressWarnings({"unchecked", "squid:S2674"})
        public void read(Kryo kryo, Input in) {
            arguments = kryo.readObjectOrNull(in, Object[].class);
            reference = kryo.readObjectOrNull(in, RegisterReference.class);
            setSender(kryo.readObjectOrNull(in, ActorRef.class));
            timeout = kryo.readObject(in, Long.class);
        }

        private void writeObject(java.io.ObjectOutputStream out)
                throws IOException {
            CompressionSerializer serializer = new CompressionSerializer();
            byte[] payload = serializer.toBytes(arguments);
            out.write(payload);
            out.writeInt(payload.length);
            out.writeObject(reference);
            out.writeObject(getSender());
            out.writeLong(timeout);
        }

        @SuppressWarnings("unchecked")
        private void readObject(java.io.ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            timeout = in.readLong();
            setSender((ActorRef) in.readObject());
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

    class Routes extends Ask implements InvocationEvents {
        private RegisterReference reference;

        public Routes(ActorRef sender, String path) {
            this.reference = new RegisterReference(path);
            setSender(sender);
        }

        public String getPath() {
            return reference.getPath();
        }
    }
}
