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

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.util.function.BiConsumer;

public class Spawn<S extends CommonBehavior<S, T>, T> {

    private String name;
    private CommonBehavior parent;
    private S behavior;
    private Props props;
    private BiConsumer<ActorContext<T>, S> setup;

    public Spawn(CommonBehavior parent, S behavior) {
        this.parent = parent;
        this.behavior = behavior;
    }

    public Spawn<S, T> withProps(Props props) {
        this.props = props;
        return this;
    }

    public Spawn<S, T> withName(String name) {
        this.name = name;
        return this;
    }

    public Spawn<S, T> whenSetup(BiConsumer<ActorContext<T>, S> consumer) {
        this.setup = consumer;
        return this;
    }

    public ActorRef<T> spawn() {
        ActorContext context = parent.getContext();
        if (name != null) {
            if (props != null)
                return context.spawn(setup(), name, props);
            else
                return context.spawn(setup(), name);
        } else {
            if (props != null)
                return context.spawnAnonymous(setup(), props);
            else
                return context.spawnAnonymous(setup());
        }
    }

    Behavior<T> setup() {
        return Behaviors.<T>setup(
                ctx -> {
                    behavior.setup(parent.getGuardian(), ctx);
                    if (setup != null)
                        setup.accept(ctx, behavior);
                    return behavior;
                });
    }
}
