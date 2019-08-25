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

package net.e6tech.elements.network.cluster;

import akka.actor.ActorRef;
import akka.actor.ExtendedActorSystem;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.javadsl.Adapter;
import akka.serialization.Serialization;
import akka.serialization.SerializerWithStringManifest;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.util.SystemException;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

import java.lang.invoke.SerializedLambda;
import java.util.concurrent.TimeUnit;

public class Serializer extends SerializerWithStringManifest {

    private Pool<Kryo> pool;
    private Pool<Output> outputPool;
    private ActorRefSerializer actorRefSerializer;
    private TypedActorRefSerializer typedActorRefSerializer;
    private Cache<String, Class> classCache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    public Serializer(ExtendedActorSystem actorSystem) {
        pool = new Pool<Kryo>(true, false, 64) {
            protected Kryo create () {
                Kryo kryo = new Kryo();
                kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new SerializingInstantiatorStrategy()));
                kryo.setRegistrationRequired(false);
                kryo.addDefaultSerializer(ActorRef.class, actorRefSerializer);
                kryo.addDefaultSerializer(akka.actor.typed.ActorRef.class, typedActorRefSerializer);
                kryo.register(SerializedLambda.class);
                kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());
                return kryo;
            }
        };

        outputPool = new Pool<Output>(true, false, 64) {
            protected Output create () {
                return new Output(4096, -1);
            }
        };

        actorRefSerializer = new ActorRefSerializer(actorSystem);
        typedActorRefSerializer = new TypedActorRefSerializer(actorSystem);
    }

    // Pick a unique identifier for your Serializer,
    // you've got a couple of billions to choose from,
    // 0 - 40 is reserved by Akka itself
    @Override
    public int identifier() {
        return 6408;
    }

    @Override
    public String manifest(Object obj) {
        return obj.getClass().getName();
    }

    // "toBinary" serializes the given object to an Array of Bytes
    @Override
    public byte[] toBinary(Object obj) {
        Kryo kryo = pool.obtain();
        Output output = outputPool.obtain();
        kryo.writeObject(output, obj);
        output.close();
        byte[] bytes = output.toBytes();
        pool.free(kryo);
        outputPool.free(output);
        return bytes;
    }

    // "fromBinary" deserializes the given array,
    // using the type hint
    @Override
    public Object fromBinary(byte[] bytes, String manifest) {
        Kryo kryo = pool.obtain();
        Class cls = classCache.getIfPresent(manifest);
        if (cls == null) {
            try {
                cls = getClass().getClassLoader().loadClass(manifest);
                classCache.put(manifest, cls);
            } catch (ClassNotFoundException e) {
                throw new SystemException(e);
            }
        }

        Input input = new Input();
        input.setBuffer(bytes);
        Object object = kryo.readObject(input, cls);
        input.close();
        pool.free(kryo);
        return object;
    }

    public class ActorRefSerializer extends com.esotericsoftware.kryo.Serializer<ActorRef> {
        private final ExtendedActorSystem system;

        public ExtendedActorSystem system() {
            return this.system;
        }

        @Override
        public void write(final Kryo kryo, final Output output, final ActorRef obj) {
            output.writeString(Serialization.serializedActorPath(obj));
        }

        @Override
        public ActorRef read(Kryo kryo, Input input, Class<? extends ActorRef> type) {
            String str = input.readString();
            return this.system().provider().resolveActorRef(str);
        }

        public ActorRefSerializer(final ExtendedActorSystem system) {
            this.system = system;
        }
    }

    public class TypedActorRefSerializer extends com.esotericsoftware.kryo.Serializer<akka.actor.typed.ActorRef> {
        private final ExtendedActorSystem system;
        private final ActorRefResolver actorRefResolver;

        public ExtendedActorSystem system() {
            return this.system;
        }

        public void write(final Kryo kryo, final Output output, final akka.actor.typed.ActorRef obj) {
            String str = actorRefResolver.toSerializationFormat(obj);
            output.writeString(str);
        }

        @Override
        public akka.actor.typed.ActorRef read(Kryo kryo, Input input, Class<? extends akka.actor.typed.ActorRef> type) {
            String str = input.readString();
            return actorRefResolver.resolveActorRef(str);
        }

        public TypedActorRefSerializer(final ExtendedActorSystem system) {
            this.system = system;
            actorRefResolver = ActorRefResolver.get(Adapter.toTyped(system));
        }
    }
}