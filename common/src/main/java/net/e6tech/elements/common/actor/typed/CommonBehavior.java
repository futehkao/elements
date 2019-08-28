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
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public abstract class CommonBehavior<Self extends CommonBehavior, T> extends AbstractBehavior<T> {

    private static Cache<Class, Receive> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(100)
            .build();

    private ActorContext<T> context;
    private Guardian guardian;

    @Override
    public Receive<T> createReceive() {

        Receive<T> receive = cache.getIfPresent(getClass());
        if (receive != null)
            return receive;
        ReceiveBuilder builder =  newReceiveBuilder();

        Class cls = getClass();

        Set<Class> events = new HashSet<>();
        while (!cls.equals(CommonBehavior.class)) {
            for (Method method : cls.getDeclaredMethods()) {
                Annotation typed = method.getAnnotation(Typed.class);

                if (typed == null)
                    continue;
                Class paramType = Reflection.getParametrizedType(getClass(), 1);
                if (method.getParameterCount() == 1
                        && (Behavior.class.isAssignableFrom(method.getReturnType()) || void.class.equals(method.getReturnType()))
                        && !events.contains(method.getParameterTypes()[0])) {
                    events.add(method.getParameterTypes()[0]);
                    method.setAccessible(true);
                    boolean behavior = Behavior.class.isAssignableFrom(method.getReturnType());
                    boolean onMessage = paramType.isAssignableFrom(method.getParameterTypes()[0]);
                    if (onMessage) {
                        builder = builder.onMessage(method.getParameterTypes()[0],
                                m -> {
                                    Object ret = method.invoke(this, m);
                                    return (behavior) ? (Behavior) ret : Behaviors.same();
                                });
                    } else {
                        builder = builder.onSignal(method.getParameterTypes()[0],
                                m -> {
                                    Object ret = method.invoke(this, m);
                                    return (behavior) ? (Behavior) ret : Behaviors.same();
                                });
                    }
                } else {
                    throw new SystemException("Invoiad method signature for method " + method);
                }
            }
            cls = cls.getSuperclass();
        }

        receive = builder.build();
        cache.put(getClass(), receive);
        return receive;
    }

    public ActorContext<T> getContext() {
        return context;
    }

    void setup(Guardian guardian, ActorContext<T> context) {
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

    public <U> ActorRef<U> spawn(CommonBehavior<?, U> behavior, String name) {
        return context.spawn(Behaviors.<U>setup(
                ctx -> {
                    behavior.setup(guardian, ctx);
                    return behavior;
                }),
                name);
    }

    public <U> ActorRef<U> spawnAnonymous(CommonBehavior<?, U> behavior) {
        return context.spawnAnonymous(Behaviors.<U>setup(
                ctx -> {
                    behavior.setup(guardian, ctx);
                    return behavior;
                }));
    }

    public <U> ActorRef<U> spawnAnonymous(CommonBehavior<?, U> behavior, Props props) {
        return context.spawnAnonymous(Behaviors.<U>setup(
                ctx -> {
                    behavior.setup(guardian, ctx);
                    return behavior;
                }), props);
    }

    public <U> CompletionStage<U> ask(Function<ActorRef<U>, T> message,
                                  long timeoutMillis) {
        return AskPattern.ask(context.getSelf(), r -> message.apply(r),
                java.time.Duration.ofMillis(timeoutMillis), context.getSystem().scheduler());
    }

    public Self tell(T msg) {
        context.getSelf().tell(msg);
        return (Self) this;
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

    public <T> akka.actor.ActorRef actorOf(akka.actor.Props props, String name) {
        return untypedContext().actorOf(props, name);
    }

    public <T> akka.actor.ActorRef actorOf(akka.actor.Props props) {
        return untypedContext().actorOf(props);
    }
}
