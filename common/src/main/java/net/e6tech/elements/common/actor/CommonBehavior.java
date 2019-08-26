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

package net.e6tech.elements.common.actor;

import akka.actor.PoisonPill;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.*;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public abstract class CommonBehavior<T> extends AbstractBehavior<T> {

    private ActorContext<T> context;

    public ActorContext<T> getContext() {
        return context;
    }

    void setContext(ActorContext<T> context) {
        this.context = context;
        initialize();
    }

    public ActorSystem<Void> getSystem() {
        return context.getSystem();
    }

    public ActorRef<T> getSelf() {
        return getContext().getSelf();
    }

    protected void initialize() {
    }

    public <U> ActorRef<U> spawn(CommonBehavior<U> behavior, String name) {
        return context.spawn(Behaviors.<U>setup(
                ctx -> {
                    behavior.setContext(ctx);
                    return behavior;
                }),
                name);
    }

    public <U> ActorRef<U> spawnAnonymous(CommonBehavior<U> behavior) {
        return context.spawnAnonymous(Behaviors.<U>setup(
                ctx -> {
                    behavior.setContext(ctx);
                    return behavior;
                }));
    }

    public <U> ActorRef<U> spawnAnonymous(CommonBehavior<U> behavior, Props props) {
        return context.spawnAnonymous(Behaviors.<U>setup(
                ctx -> {
                    behavior.setContext(ctx);
                    return behavior;
                }), props);
    }

    public <U> CompletionStage<U> ask(Function<ActorRef<U>, T> message,
                                  long timeoutMillis) {
        return AskPattern.ask(context.getSelf(), r -> message.apply(r),
                java.time.Duration.ofMillis(timeoutMillis), context.getSystem().scheduler());
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
