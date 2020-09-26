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
import net.e6tech.elements.common.util.SystemException;

public class Spawn<T, B extends Receptor<T, B>> {

    private String name;
    private Receptor<?, ?> parent;
    private Props props;

    public Spawn(Receptor<?, ?> parent) {
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

    public B spawnNow(B child) {
        ActorRef<T> ref = spawn(child);
        ExtensionEvents.ExtensionsResponse extensions = parent.getGuardian().talk(ref, ExtensionEvents.class).askAndWait(ExtensionEvents.Extensions::new);
        return extensions.getOwner();
    }

    /**
     * Note, the factory is called in a separated thread.  Akka retrieves messages from its mailbox
     * and process the spawn message.
     */
    public ActorRef<T> spawn(B child) {
        try {
            return spawnChild(child);
        } catch (UnsupportedOperationException ex) {
            return spawnExternally(child);
        }
    }

    protected ActorRef<T> spawnChild(B child) {
        ActorContext<?> context = parent.getContext();
        if (name != null) {
            if (props != null)
                return context.spawn(setup(child), name, props);
            else
                return context.spawn(setup(child), name);
        } else {
            if (props != null)
                return context.spawnAnonymous(setup(child), props);
            else
                return context.spawnAnonymous(setup(child));
        }
    }

    public B spawnNowExternally(B child) {
        ActorRef<T> ref = spawnExternally(child);
        ExtensionEvents.ExtensionsResponse extensions =
                parent.getGuardian().talk((ActorRef<?>) ref, ExtensionEvents.class).askAndWait(ExtensionEvents.Extensions::new);
        return extensions.getOwner();
    }

    public ActorRef<T> spawnExternally(B child) {
        try {
            SpawnEvents.SpawnResponse resp = parent.talk(SpawnEvents.class).askAndWait(ref -> new SpawnEvents.SpawnRequest(ref, child, name, props));
            return resp.getSpawned();
        } catch (SystemException ex) {
            throw ex;
        }
    }

    protected Behavior<T> setup(B child) {
        return Behaviors.setup(
                ctx -> child.setup(ctx, parent.getGuardian()));
    }
}
