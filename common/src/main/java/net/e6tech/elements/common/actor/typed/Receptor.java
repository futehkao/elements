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

import akka.actor.Status;
import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@SuppressWarnings({"unchecked", "squid:S1172"})
public abstract class Receptor<T, R extends Receptor<T, R>> {

    private static Cache<Class, List<MessageBuilder>> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(500)
            .build();

    private static Cache<Method, BiFunction> interceptorCache = CacheBuilder.newBuilder()
            .concurrencyLevel(64)
            .initialCapacity(128)
            .maximumSize(10000)
            .build();

    private Guardian guardian;
    private AbstractBehavior<T> behavior;

    protected List<Receptor> addedExtensions = new ArrayList<>(); // contains a list of extensions that has not been set up.
    protected Map<Class, Receptor> extensions = new LinkedHashMap<>(); // event classes

    public Receptor() {
    }

    protected <U extends Receptor> U addExtension(U trait) {
        addedExtensions.add(trait);
        return trait;
    }

    public Behavior<T> setup(ActorContext<T> context, Guardian guardian) {
        this.guardian = guardian;
        behavior = new AbstractBehavior<T>(context) {
            @Override
            public Receive<T> createReceive() {
                return Receptor.this.createReceive();
            }
        };
        initialize();
        return behavior;
    }

    public R virtualize() {
        long timeout = (getGuardian() != null) ? getGuardian().getTimeout() :  Guardian.DEFAULT_TIME_OUT;
        return Interceptor.getInstance().interceptorBuilder((R) this, new ReceptorInterceptorHandler(timeout)).build();
    }

    public R virtualize(long timeout) {
        return Interceptor.getInstance().interceptorBuilder((R) this, new ReceptorInterceptorHandler(timeout)).build();
    }

    public Behavior<T> create() {
        return behavior;
    }

    public Behavior<T> getBehavior() {
        return behavior;
    }

    protected void initialize() {
    }

    @Typed
    public ExtensionEvents.ExtensionsResponse extensions(ExtensionEvents.Extensions event) {
        return new ExtensionEvents.ExtensionsResponse(getSelf(), this, new LinkedHashMap<>(extensions));
    }

    private void saveEventClass(Class cls, Receptor implementation) {
        // deduce event class
        Class c = cls;
        Class eventClass = null;
        while (!c.equals(Receptor.class)) {
            try {
                eventClass = Reflection.getParametrizedType(c, 0);
                if (eventClass != null)
                    break;
            } catch (Exception ex) {
                // ok
            }
            c = c.getSuperclass();
        }
        if (eventClass != null && !extensions.containsKey(eventClass))
            extensions.put(eventClass, implementation);
    }

    protected Receive<T> createReceive() {
        ReceiveBuilder builder =  behavior.newReceiveBuilder();

        // deduce event class
        Class cls = getClass();
        saveEventClass(cls, this);
        Set<String> events = new HashSet<>();
        while (true) {
            builder = build(this, builder, cls, events);
            if (cls.equals(Receptor.class))
                break;
            cls = cls.getSuperclass();
        }

        // build events for extension classes
        for (Receptor extension : addedExtensions) {
            extension.setup(getContext(), getGuardian());
            cls = extension.getClass();
            saveEventClass(cls, extension);
            while (true) {
                builder = build(extension, builder, cls, events);
                if (cls.equals(Receptor.class))
                    break;
                cls = cls.getSuperclass();
            }
        }
        addedExtensions.clear();
        addedExtensions = null;

        return builder.build();
    }

    @SuppressWarnings("squid:S3776")
    private ReceiveBuilder build(Receptor target, ReceiveBuilder builder, Class cls, Set<String> events) {
        List<MessageBuilder> list = cache.getIfPresent(cls);
        if (list == null) {
            list = new ArrayList<>();
            for (Method method : cls.getDeclaredMethods()) {
                Annotation typed = method.getAnnotation(Typed.class);
                if (typed == null)
                    continue;
                if (method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    if (Signal.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        list.add(new OnSignal(method));
                    } else {
                        list.add(new OnMessage(method));
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

    public ActorContext<T> getContext() {
        return behavior.getContext();
    }

    public ActorSystem<Void> getSystem() {
        return getContext().getSystem();
    }

    public Talk<T> talk() {
        return new Talk<>(getGuardian(), getSelf());
    }

    public <U> Talk<U> talk(Class<U> cls) {
        return new Talk<>(getGuardian(), getSelf().unsafeUpcast());
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
        return new Talk<>(getGuardian(), ref);
    }

    public <U, V> Talk<V> talk(ActorRef<U> recipient, Class<V> cls, long timeout) {
        ActorRef<V> ref = recipient.unsafeUpcast();
        return new Talk<>(getGuardian(), ref).timeout(timeout);
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public ActorRef<T> getSelf() {
        return getContext().getSelf();
    }

    public Scheduler getScheduler() {
        return getSystem().scheduler();
    }

    public <C extends Receptor<M, C>, M> Spawn<M, C> childActor(Class<C> commonBehaviorClass) {
        return new Spawn<>(this);
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

        MessageBuilder(Method method) {
            this.behavior = Behavior.class.isAssignableFrom(method.getReturnType());
            this.method = method;
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

        abstract ReceiveBuilder build(ReceiveBuilder builder, Object target);

        String signature() {
            return signature;
        }
    }

    static class OnMessage extends MessageBuilder {
        private static final BiConsumer<Object, Object> NO_OP = (arg, ret) -> {};

        OnMessage(Method method) {
            super(method);
        }

        @SuppressWarnings("squid:S3776")
        @Override
        public ReceiveBuilder build(ReceiveBuilder builder, Object target) {
            BiConsumer<Object, Object> consumer = NO_OP;
            if (method.getParameterTypes().length > 0 && !method.getReturnType().equals(void.class) && !method.getReturnType().equals(Void.class)) {
                Class<?> argType = method.getParameterTypes()[0];
                if (Ask.class.isAssignableFrom(argType)){
                    consumer = (arg, ret) -> {
                        ActorRef sender = ((Ask) arg).getSender();
                        if (sender != null) {
                            try {
                                sender.tell(ret);
                            } catch (Exception th) {
                                sender.tell(new Status.Failure(th));
                            }
                        }
                    };
                } else if (Asking.class.isAssignableFrom(argType)) {
                    consumer = (arg, ret) -> {
                        ActorRef sender = ((Asking) arg).getSender();
                        if (sender != null) {
                            try {
                                sender.tell(ret);
                            } catch (Exception th) {
                                sender.tell(new Status.Failure(th));
                            }
                        }
                    };
                }
            }
            BiConsumer<Object, Object> responder = consumer;
            return builder.onMessage(method.getParameterTypes()[0],
                    m -> {
                        Object ret = method.invoke(target, m);
                        responder.accept(m, ret);
                        return (behavior) ? ret : Behaviors.same();
                    });
        }
    }

    static class OnSignal extends MessageBuilder {
        OnSignal(Method method) {
            super(method);
        }

        @Override
        public ReceiveBuilder build(ReceiveBuilder builder, Object target) {
            return builder.onSignal(method.getParameterTypes()[0],
                    m -> {
                        Object ret = method.invoke(target, m);
                        return (behavior) ? ret : Behaviors.same();
                    });
        }
    }

    class ReceptorInterceptorHandler implements InterceptorHandler {
        private long timeout;
        private Receptor enclosing;

        ReceptorInterceptorHandler(long timeout) {
            this.timeout = timeout;
            enclosing = Receptor.this;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        private Talk talk() {
            return enclosing.talk(timeout);
        }

        @Override
        public Object invoke(CallFrame frame) {

            BiFunction<ReceptorInterceptorHandler, CallFrame, Object> biFunction = interceptorCache.getIfPresent(frame.getMethod());

            if (biFunction != null) {
               // do nothing
            } else if (frame.getMethod().getAnnotation(Typed.class) != null
                    && frame.getMethod().getParameterTypes().length > 0) {

                Class argType = frame.getMethod().getParameterTypes()[0];
                if (!frame.getMethod().getReturnType().equals(void.class) && !frame.getMethod().getReturnType().equals(Void.class)) {
                    if (Ask.class.isAssignableFrom(argType)) {
                        biFunction = (handler, f) ->
                            handler.talk().askAndWait(sender -> {
                                Object arg = f.getArguments()[0];
                                ((Ask) arg).setSender((ActorRef) sender);
                                return arg;
                            });
                    } else if (Asking.class.isAssignableFrom(argType)) {
                        biFunction = (handler, f) ->
                                handler.talk().askAndWait(sender -> {
                                    Object arg = f.getArguments()[0];
                                    ((Asking) arg).setSender((ActorRef) sender);
                                    return arg;
                                });
                    } else {
                        biFunction = (handler, f) -> {
                            throw new SystemException("Event " + f.getMethod().getParameterTypes()[0] + " is not a subclass of Ask nor does it implement Asking.");
                        };
                    }
                } else {
                    // tell, no need to wait for response because method declared as returning void.
                    biFunction = (handler, f) -> {
                        Object arg = f.getArguments()[0];
                        handler.talk().tell(arg);
                        return null;
                    };
                }
                interceptorCache.put(frame.getMethod(), biFunction);
            } else {
                biFunction = (handler, f) -> f.invoke();
                interceptorCache.put(frame.getMethod(), biFunction);
            }

            return biFunction.apply(this, frame);
        }
    }
}
