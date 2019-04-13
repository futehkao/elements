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
import akka.pattern.Patterns;

/**
 * Created by futeh.
 * <p>
 * This actor bridges between service provider and the akka world.
 * It is created with a callback function in Events.Register.  When
 * messages are send to it the function is invoked.
 * <p>
 * In addition, during registration, it sends its location to RegistrarActor
 * so that callers can find it.
 * <p>
 * Each entry corresponds to a method.
 */
class RegistryEntryActor extends AbstractActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.lookup().get(getContext().system());
    Events.Registration registration;
    ActorRef workPool;

    public RegistryEntryActor(Events.Registration registration, ActorRef workPool) {
        this.registration = registration;
        this.workPool = workPool;
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
        return receiveBuilder()
                .match(ClusterEvent.MemberUp.class, member -> register(member.member()))
                .match(ClusterEvent.CurrentClusterState.class, state -> {
                    for (Member member : state.getMembers()) {
                        if (member.status().equals(MemberStatus.up())) {
                            register(member);
                        }
                    }
                })
                .match(ClusterEvent.UnreachableMember.class, member -> log.info("Member detected as unreachable: {}", member.member()))
                .match(ClusterEvent.MemberRemoved.class, member -> log.info("Member is Removed: {}", member.member()))
                .match(Events.Invocation.class, message -> {
                    final ActorRef sender = getSender();
                    final ActorRef self = getSelf();
                    try {
                        if (workPool != null && registration.timeout() > 0) {
                            Patterns.ask(workPool, (Runnable) () -> {
                                try {
                                    Object ret = registration.function().apply(this, message.arguments());
                                    sender.tell(new Events.Response(ret, self), self);
                                } catch (Exception ex) {
                                    sender.tell(new Status.Failure(ex), self);
                                }
                            }, registration.timeout());
                        } else {
                            Registry.getThreadPool().execute(() -> {
                                try {
                                    Object ret = registration.function().apply(this, message.arguments());
                                    sender.tell(new Events.Response(ret, self), self);
                                } catch (Exception ex) {
                                    sender.tell(new Status.Failure(ex), self);
                                }
                            });
                        }
                    } catch (RuntimeException ex) {
                        Throwable throwable = ex.getCause();
                        if (throwable == null) throwable = ex;
                        sender.tell(new Status.Failure(throwable), self);
                    }
                }).build();
    }

    // tell other member about this RegistryEntryActor
    void register(Member member) {
        if (!cluster.selfAddress().equals(member.address())) {
            getContext().actorSelection(member.address() + "/user/" + Registry.getPath())
                    .tell(new Events.Announcement(registration), getSelf());
        }
    }
}
