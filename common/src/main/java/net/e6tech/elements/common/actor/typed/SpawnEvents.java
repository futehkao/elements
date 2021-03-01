/*
 * Copyright 2015-2020 Futeh Kao
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
import akka.actor.typed.Props;

public interface SpawnEvents {

    class SpawnRequest extends Ask implements SpawnEvents {
        private transient Receptor<?,?> child;
        private String name;
        private Props props;
        public SpawnRequest(ActorRef<SpawnResponse> sender, Receptor<?,?> child, String name, Props props) {
            setSender(sender);
            this.child = child;
            this.name = name;
            this.props = props;
        }

        public Receptor getChild() {
            return child;
        }

        public String getName() {
            return name;
        }

        public Props getProps() {
            return props;
        }
    }

    class SpawnResponse implements SpawnEvents {
        private Receptor owner;
        private ActorRef spawned;
        private ActorRef responder;

        public SpawnResponse(ActorRef responder, Receptor owner, ActorRef spawned) {
            this.responder = responder;
            this.owner = owner;
            this.spawned = spawned;
        }

        @SuppressWarnings("unchecked")
        public <T extends Receptor<?,?>> T getOwner() {
            return (T) owner;
        }

        public ActorRef getResponder() {
            return responder;
        }

        public ActorRef getSpawned() {
            return spawned;
        }
    }
}
