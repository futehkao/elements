/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.network.cluster;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Created by futeh.
 *
 * This actor bridges between service provider and the akka world.
 * It is created with a callback function in Events.Register.  When
 * messages are send to it the function is invoked.
 *
 * In addition, during registration, it sends its location to RegistraraActor
 * so that callers can find it.
 */
class RegisterEntryActor extends AbstractActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.get(getContext().system());
    Events.Registration registration;

    public RegisterEntryActor(Events.Registration registration) {
        this.registration = registration;
    }

    //subscribe to cluster changes
    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);
        // register within its own cluster.
        getContext().actorSelection(getSelf().path().root().address() + "/user/" + Registry.getPath())
                .tell(new Events.Announcement(registration), getSelf());
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder().match(ClusterEvent.MemberUp.class, member -> {
            register(member.member());
        }).match(ClusterEvent.CurrentClusterState.class, state -> {
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    register(member);
                }
            }
        }).match(ClusterEvent.UnreachableMember.class, member -> {
            log.info("Member detected as unreachable: {}", member.member());
        }).match(ClusterEvent.MemberRemoved.class, member -> {
            log.info("Member is Removed: {}", member.member());
        }).match(Events.Invocation.class, message -> {
            final ActorRef sender = getSender();
            getContext().dispatcher().execute(() -> {
                try {
                    Object ret = registration.function().apply(message.message());
                    sender.tell(new Events.Response(ret), getSelf());
                } catch (RuntimeException ex) {
                    Throwable throwable = ex.getCause();
                    if (throwable == null) throwable = ex;
                    sender.tell(new Status.Failure(throwable), getSelf());
                }
            });
        }).build();
    }

    void register(Member member) {
        getContext().actorSelection(member.address() + "/user/" + Registry.getPath())
                .tell(new Events.Announcement(registration), getSelf());
    }
}