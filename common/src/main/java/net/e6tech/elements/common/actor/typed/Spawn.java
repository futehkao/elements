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


public class Spawn<T, B extends Receptor<T,B>> {

    private String name;
    private Receptor<?,?> parent;
    private Props props;

    public Spawn(Receptor<?,?> parent) {
        this.parent = parent;
    }

    public Spawn<T, B> withProps(Props props) {
        this.props = props;
        return this;
    }

    public Spawn<T, B> withName(String name) {
        this.name = name;
        return this;
    }

    public B spawnNow(B common) {
        ActorRef<T> ref = spawn(common);
        ExtensionEvents.ExtensionsResponse extensions = parent.getGuardian().talk(ref, ExtensionEvents.class).askAndWait(ExtensionEvents.Extensions::new);
        return extensions.getOwner();
    }

    /**
     * Note, the factory is called in a separated thread.  Akka retrieves messages from its mailbox
     * and process the spawn message.
     */
    public ActorRef<T> spawn(B common) {
        ActorContext<?> context = parent.getContext();
        if (name != null) {
            if (props != null)
                return context.spawn(setup(common), name, props);
            else
                return context.spawn(setup(common), name);
        } else {
            if (props != null)
                return context.spawnAnonymous(setup(common), props);
            else
                return context.spawnAnonymous(setup(common));
        }
    }

    protected Behavior<T> setup(B common) {
        return Behaviors.setup(
                ctx -> common.setup(ctx, parent.getGuardian()));
    }
}
