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

import java.util.Map;

public interface ExtensionEvents {

    class Extensions extends Ask implements ExtensionEvents {
        public Extensions(ActorRef<ExtensionsResponse> sender) {
            setSender(sender);
        }
    }

    class ExtensionsResponse implements ExtensionEvents {
        private Trait owner;
        private Map<Class, Trait> extensions;
        private ActorRef responder;

        public ExtensionsResponse(ActorRef responder, Trait owner, Map<Class, Trait> extensions) {
            this.responder = responder;
            this.owner = owner;
            this.extensions = extensions;
        }

        @SuppressWarnings("unchecked")
        public <T extends Trait<?,?>> T getOwner() {
            return (T) owner;
        }

        public Map<Class, Trait> getExtensions() {
            return extensions;
        }

        public ActorRef getResponder() {
            return responder;
        }
    }
}
