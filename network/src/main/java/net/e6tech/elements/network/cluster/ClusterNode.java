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

import akka.actor.*;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.subscribe.Broadcast;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class ClusterNode implements Initializable {

    @Inject
    private Provision provision;
    private String name;
    private String configuration;
    private ActorSystem system;
    private ActorRef membership;
    private Map<Address, Member> members = new HashMap<>();
    private Messaging broadcast;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public Broadcast getBroadcast() {
        return broadcast;
    }

    public void initialize(Resources resources) {
        Config config = ConfigFactory.parseString(configuration);

        // Create an Akka system
        system = ActorSystem.create(name, config);
        membership = system.actorOf(Props.create(Membership.class,() -> {
            return new Membership();
        }));
        provision.getResourceManager().bind(ActorSystem.class, system);
        resources.bind(ActorSystem.class, system);
        broadcast = resources.newInstance(Messaging.class);
        broadcast.start();
    }

    class Membership extends AbstractActor {
        akka.cluster.Cluster cluster = akka.cluster.Cluster.get(getContext().system());

        //subscribe to cluster changes
        @Override
        public void preStart() {
            cluster.subscribe(getSelf(), ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);
        }

        //re-subscribe when restart
        @Override
        public void postStop() {
            cluster.unsubscribe(getSelf());
        }

        @Override
        public AbstractActor.Receive createReceive() {
            return receiveBuilder().match(ClusterEvent.MemberUp.class, member -> {
                members.put(member.member().address(), member.member());
            }).match(ClusterEvent.CurrentClusterState.class, state -> {

            }).match(ClusterEvent.UnreachableMember.class, member -> {
                members.remove(member.member().address());
            }).match(ClusterEvent.MemberRemoved.class, member -> {
                members.remove(member.member().address());
            }).build();
        }
    }

}
