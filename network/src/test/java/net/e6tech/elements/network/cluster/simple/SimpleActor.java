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

package net.e6tech.elements.network.cluster.simple;


import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class SimpleActor extends AbstractActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.get(getContext().system());

    //subscribe to cluster changes
    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), MemberEvent.class, UnreachableMember.class);
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder().match(MemberUp.class, member -> {
                register(member.member());
            }).match(ClusterEvent.CurrentClusterState.class, state -> {
                for (Member member : state.getMembers()) {
                    if (member.status().equals(MemberStatus.up())) {
                        register(member);
                    }
                }
            }).match(UnreachableMember.class, member -> {
                log.info("Member detected as unreachable: {}", member.member());
            }).match(MemberRemoved.class, member -> {
                log.info("Member is Removed: {}", member.member());
            }).match(SimpleMessage.class, message -> {
                getSender().tell(message, getSelf());
        })
            .build();
    }

    void register(Member member) {
            getContext().actorSelection(member.address() + "/user/*").tell(
                    SimpleMessage.REGISTRATION, getSelf());
    }
}
