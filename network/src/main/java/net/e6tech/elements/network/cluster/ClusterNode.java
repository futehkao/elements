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
import akka.cluster.MemberStatus;
import akka.pattern.Patterns;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.subscribe.Broadcast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by futeh.
 */
public class ClusterNode implements Initializable {

    private String name;
    private String configuration;
    private Genesis genesis;
    private ActorRef membership;
    private Map<Address, Member> members = new HashMap<>();
    private Messaging broadcast;
    private Registry registry;
    private List<MemberListener> memberListeners = new ArrayList<>();
    private boolean started = false;
    private long timeout = 5000L;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
        if (broadcast != null)
            broadcast.setTimeout(timeout);
        if (registry != null)
            registry .setTimeout(timeout);
    }

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

    public Genesis getGenesis() {
        return genesis;
    }

    @Inject(optional = true)
    public void setGenesis(Genesis genesis) {
        this.genesis = genesis;
    }

    public Broadcast getBroadcast() {
        return broadcast;
    }

    public Registry getRegistry() {
        return registry;
    }

    public Map<Address, Member> getMembers() {
        return members;
    }

    public void initialize(Resources resources) {
        if (genesis == null) {
            genesis = new Genesis();
            genesis.setName(getName());
            genesis.setConfiguration(getConfiguration());
            genesis.setTimeout(getTimeout());
            genesis.initialize(resources);
        }
        initialize(genesis);
    }

    public void initialize(Genesis genesis) {
        this.genesis = genesis;
        setName(genesis.getName());
        setTimeout(genesis.getTimeout());
        setConfiguration(genesis.getConfiguration());
        start();
    }

    public void start() {
        if (started)
            return;
        if (membership == null)
            membership = genesis.getSystem().actorOf(Props.create(Membership.class, Membership::new ));

        if (broadcast == null) {
            broadcast = new Messaging();
            broadcast.setTimeout(timeout);
        }
        if (registry == null) {
            registry = new Registry();
            registry.setTimeout(timeout);
        }
        registry.setWorkerPool(genesis.getWorkerPool());
        broadcast.start(genesis.getSystem());
        registry.start(genesis.getSystem());
        started = true;
    }

    public void shutdown() {
        Patterns.ask(membership, PoisonPill.getInstance(), 5000L);
        broadcast.shutdown();
        registry.shutdown();
        genesis.getSystem().terminate();
        members.clear();
        started = false;
    }

    class Membership extends AbstractActor {

        akka.cluster.Cluster cluster = akka.cluster.Cluster.lookup().get(getContext().system());

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
                memberListeners.forEach(listener -> listener.memberUp(member.member().address().toString()));
            }).match(ClusterEvent.CurrentClusterState.class, state -> {
                for (Member member : state.getMembers()) {
                    if (member.status().equals(MemberStatus.up())) {
                        members.put(member.address(), member);
                        memberListeners.forEach(listener -> listener.memberUp(member.address().toString()));
                    }
                }
            }).match(ClusterEvent.UnreachableMember.class, member -> {
                members.remove(member.member().address());
                memberListeners.forEach(listener -> listener.memberDown(member.member().address().toString()));
            }).match(ClusterEvent.MemberRemoved.class, member -> {
                members.remove(member.member().address());
                memberListeners.forEach(listener -> listener.memberDown(member.member().address().toString()));
            }).build();
        }
    }

}
