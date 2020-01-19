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

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;

@SuppressWarnings("unchecked")
/**
 * Base Behavior class.  T is the Message class that this Behavior response to
 */
public abstract class CommonBehavior<T> extends AbstractBehavior<T> {

    private static Cache<Class, List<MessageBuilder>> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(500)
            .build();

    private Guardian guardian;
    protected List<BiFunction<ActorContext, CommonBehavior, CommonBehavior>> extensionFactories = new ArrayList<>(); // event class to factory
    protected Map<Class, CommonBehavior> extensions = new LinkedHashMap<>(); // event classes

    protected CommonBehavior(ActorContext<T> context) {
        super(context);
    }

    protected <U extends CommonBehavior> CommonBehavior<T> addExtension(BiFunction<ActorContext, CommonBehavior<T>, U> factory) {
        extensionFactories.add((BiFunction) factory);
        return this;
    }

    public void setup(Guardian guardian) {
        this.guardian = guardian;
        initialize();
    }

    protected void initialize() {
    }

    @Typed
    private void extensions(ExtensionEvents.Extensions event) {
        final ActorRef sender = event.getSender();
        sender.tell(new ExtensionEvents.ExtensionsResponse(getSelf(), this, new LinkedHashMap<>(extensions)));
    }

    private void saveEventClass(Class cls, CommonBehavior implementation) {
        // deduce event class
        Class c = cls;
        Class eventClass = null;
        while (true) {
            try {
                eventClass = Reflection.getParametrizedType(c, 0);
                if (eventClass != null)
                    break;
            } catch (Exception ex) {
                // ok
            }
            if (c.equals(CommonBehavior.class))
                break;
            c = c.getSuperclass();
        }
        if (eventClass != null && !extensions.containsKey(eventClass))
            extensions.put(eventClass, implementation);
    }

    @Override
    public Receive<T> createReceive() {
        ReceiveBuilder builder =  newReceiveBuilder();

        // deduce event class
        Class cls = getClass();
        saveEventClass(cls, this);
        Set<String> events = new HashSet<>();
        while (true) {
            builder = build(this, builder, cls, events);
            if (cls.equals(CommonBehavior.class))
                break;
            cls = cls.getSuperclass();
        }

        // build events for extension classes
        for (BiFunction<ActorContext, CommonBehavior, CommonBehavior> factory : extensionFactories) {
            CommonBehavior extension = factory.apply(getContext(), this);
            cls = extension.getClass();
            saveEventClass(cls, extension);
            while (true) {
                builder = build(extension, builder, cls, events);
                if (cls.equals(CommonBehavior.class))
                    break;
                cls = cls.getSuperclass();
            }
        }
        extensionFactories.clear();
        extensionFactories = null;

        return builder.build();
    }

    @SuppressWarnings("squid:S3776")
    private ReceiveBuilder build(CommonBehavior target, ReceiveBuilder builder, Class cls, Set<String> events) {
        List<MessageBuilder> list = cache.getIfPresent(cls);
        if (list == null) {
            list = new ArrayList<>();
            for (Method method : cls.getDeclaredMethods()) {
                Annotation typed = method.getAnnotation(Typed.class);
                if (typed == null)
                    continue;
                if (method.getParameterCount() == 1
                        && (Behavior.class.isAssignableFrom(method.getReturnType()) || void.class.equals(method.getReturnType()))) {
                    method.setAccessible(true);
                    boolean behavior = Behavior.class.isAssignableFrom(method.getReturnType());
                    if (Signal.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        list.add(new OnSignal(method, behavior));
                    } else {
                        list.add(new OnMessage(method, behavior));
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
            builder = mb.build(builder, target);
        }

        return builder;
    }

    public ActorSystem<Void> getSystem() {
        return getContext().getSystem();
    }

    public Talk<T> talk() {
        return new Talk<>(getGuardian(), getSelf());
    }

    public <U> Talk<U> talk(Class<U> cls) {
        return new Talk<U>(getGuardian(), getSelf().unsafeUpcast());
    }

    public Talk<T> talk(long timeout) {
        return talk().timeout(timeout);
    }

    public <U> Talk<U> talk(ActorRef<U> recipient) {
        return new Talk<>(getGuardian(), recipient);
    }

    public <U> Talk<U> talk(ActorRef<U> recipient, long timeout) {
        return new Talk<>(getGuardian(), recipient).timeout(timeout);
    }

    public <U, V> Talk<V> talk(ActorRef<U> recipient, Class<V> cls) {
        ActorRef<V> ref = recipient.unsafeUpcast();
        return new Talk<V>(getGuardian(), ref);
    }

    public <U, V> Talk<V> talk(ActorRef<U> recipient, Class<V> cls, long timeout) {
        ActorRef<V> ref = recipient.unsafeUpcast();
        return new Talk<>(getGuardian(), ref).timeout(timeout);
    }

    protected Guardian getGuardian() {
        return guardian;
    }

    public ActorRef<T> getSelf() {
        return getContext().getSelf();
    }

    public Scheduler getScheduler() {
        return getSystem().scheduler();
    }

    public <B extends CommonBehavior<M>, M> Spawn<M, B> childActor(Class<B> commonBehaviorClass) {
        return new Spawn<>(this, getGuardian());
    }

    public akka.actor.ActorRef untypedRef() {
        return Adapter.toClassic(getSelf());
    }

    public akka.actor.ActorContext untypedContext() {
        return Adapter.toClassic(getContext());
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
