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

    class Extensions implements ExtensionEvents {
        private ActorRef<ExtensionsResponse> sender;  // event class to extension class

        public Extensions(ActorRef<ExtensionsResponse> sender) {
            this.sender = sender;
        }

        public ActorRef<ExtensionsResponse> getSender() {
            return sender;
        }
    }

    class ExtensionsResponse implements ExtensionEvents {
        private CommonBehavior owner;
        private Map<Class, CommonBehavior> extensions;
        private ActorRef responder;

        public ExtensionsResponse(ActorRef responder, CommonBehavior owner, Map<Class, CommonBehavior> extensions) {
            this.responder = responder;
            this.owner = owner;
            this.extensions = extensions;
        }

        @SuppressWarnings("unchecked")
        public <T extends CommonBehavior> T getOwner() {
            return (T) owner;
        }

        public Map<Class, CommonBehavior> getExtensions() {
            return extensions;
        }

        public ActorRef getResponder() {
            return responder;
        }
    }
}
