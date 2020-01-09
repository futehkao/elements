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

package net.e6tech.elements.common.actor.typed;

import akka.actor.PoisonPill;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public abstract class CommonBehavior<S extends CommonBehavior, T> extends AbstractBehavior<T> {

    private static Cache<Class, List<MessageBuilder>> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(500)
            .build();

    private ActorContext<T> context;
    private Guardian guardian;

    @Override
    public Receive<T> createReceive() {
        ReceiveBuilder builder =  newReceiveBuilder();

        Class cls = getClass();

        Set<String> events = new HashSet<>();
        while (!cls.equals(CommonBehavior.class)) {
            builder = build(builder, cls, events);
            cls = cls.getSuperclass();
        }

        return builder.build();
    }

    @SuppressWarnings("squid:S3776")
    private ReceiveBuilder build(ReceiveBuilder builder, Class cls, Set<String> events) {
        List<MessageBuilder> list = cache.getIfPresent(cls);
        if (list == null) {
            list = new ArrayList<>();
            for (Method method : cls.getDeclaredMethods()) {
                Annotation typed = method.getAnnotation(Typed.class);
                if (typed == null)
                    continue;
                Class paramType = Reflection.getParametrizedType(getClass(), 1);
                if (method.getParameterCount() == 1
                        && (Behavior.class.isAssignableFrom(method.getReturnType()) || void.class.equals(method.getReturnType()))) {
                    method.setAccessible(true);
                    boolean behavior = Behavior.class.isAssignableFrom(method.getReturnType());
                    boolean onMessage = paramType.isAssignableFrom(method.getParameterTypes()[0]);
                    if (onMessage) {
                        list.add(new OnMessage(method, behavior));
                    } else {
                        list.add(new OnSignal(method, behavior));
                    }
                } else {
                    throw new SystemException("Invalid method signature for method " + method);
                }
            }
            cache.put(cls, list);
        }

        for (MessageBuilder mb : list) {
            if (events.contains(mb.signature()))
                continue;
            events.add(mb.signature());
            builder = mb.build(builder, this);
        }

        return builder;
    }

    public ActorContext<T> getContext() {
        return context;
    }

    public void setup(Guardian guardian, ActorContext<T> context) {
        this.guardian = guardian;
        this.context = context;
        initialize();
    }

    public ActorSystem<Void> getSystem() {
        return context.getSystem();
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public ActorRef<T> getSelf() {
        return getContext().getSelf();
    }

    protected void initialize() {
    }

    public <X extends CommonBehavior<X, Y>, Y> Spawn<X, Y> childActor(X child) {
        return new Spawn<>(this, child);
    }

    public <U> CompletionStage<U> ask(Function<ActorRef<U>, T> message,
                                  long timeoutMillis) {
        return AskPattern.ask(context.getSelf(), message::apply,
                java.time.Duration.ofMillis(timeoutMillis), context.getSystem().scheduler());
    }

    public S tell(T msg) {
        context.getSelf().tell(msg);
        return (S) this;
    }

    public <U> void stop(ActorRef<U> child) {
        context.stop(child);
    }

    public void stop() {
        AskPattern.ask((ActorRef) getSelf(), ref -> PoisonPill.getInstance(),
                java.time.Duration.ofSeconds(10), context.getSystem().scheduler());
    }

    public akka.actor.ActorRef untypedRef() {
        return Adapter.toUntyped(getSelf());
    }

    public akka.actor.ActorContext untypedContext() {
        return Adapter.toUntyped(getContext());
    }

    public akka.actor.ActorRef actorOf(akka.actor.Props props, String name) {
        return untypedContext().actorOf(props, name);
    }

    public akka.actor.ActorRef actorOf(akka.actor.Props props) {
        return untypedContext().actorOf(props);
    }

    abstract static class MessageBuilder {
        protected boolean behavior;
        protected Method method;
        private String signature;

        MessageBuilder(Method method, boolean behavior) {
            this.method = method;
            this.behavior = behavior;
            StringBuilder builder = new StringBuilder();
            builder.append(method.getName());
            builder.append("(");
            boolean first = true;
            for (Class param : method.getParameterTypes()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(",");
                }
                builder.append(param.getTypeName());
            }
            builder.append(")");
            signature = builder.toString();
        }

        abstract ReceiveBuilder build(ReceiveBuilder builder, Behavior target);

        String signature() {
            return signature;
        }
    }

    static class OnMessage extends MessageBuilder {
        OnMessage(Method method, boolean behavior) {
            super(method, behavior);
        }

        @Override
        public ReceiveBuilder build(ReceiveBuilder builder, Behavior target) {
            return builder.onMessage(method.getParameterTypes()[0],
                    m -> {
                        Object ret = method.invoke(target, m);
                        return (behavior) ? (Behavior) ret : Behaviors.same();
                    });
        }
    }

    static class OnSignal extends MessageBuilder {
        OnSignal(Method method, boolean behavior) {
            super(method, behavior);
        }

        @Override
        public ReceiveBuilder build(ReceiveBuilder builder, Behavior target) {
            return builder.onSignal(method.getParameterTypes()[0],
                    m -> {
                        Object ret = method.invoke(target, m);
                        return (behavior) ? (Behavior) ret : Behaviors.same();
                    });
        }
    }
}
