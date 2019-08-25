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

package net.e6tech.elements.network.cluster;

import akka.actor.Address;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.*;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;
import akka.cluster.typed.Unsubscribe;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.network.cluster.invocation.RegistryImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterNode implements Initializable {

    public static final long DEFAULT_TIME_OUT = 10000L;

    private String name;
    private String configuration;
    private Genesis genesis;
    private ActorRef<ClusterEvent.ClusterDomainEvent> membership;
    private Map<Address, Member> members = new HashMap<>();
    private Messaging broadcast;
    private Registry registry;
    private List<MemberListener> memberListeners = new ArrayList<>();
    private boolean started = false;
    private long timeout = DEFAULT_TIME_OUT;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
        if (broadcast != null)
            broadcast.setTimeout(timeout);
        if (registry != null)
            registry.setTimeout(timeout);
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
            membership = genesis.typeActorContext().spawnAnonymous(Behaviors.setup(context -> new Membership(context)));

        if (broadcast == null) {
            broadcast = new Messaging();
            broadcast.setTimeout(timeout);
        }
        if (registry == null) {
            registry = new RegistryImpl();
            registry.setTimeout(timeout);
        }
        broadcast.start(genesis);
        registry.start(genesis);
        started = true;
    }

    public void shutdown() {
        genesis.typeActorContext().stop(membership);
        broadcast.shutdown();
        registry.shutdown();
        genesis.terminate();
        members.clear();
        started = false;
    }

    // listener to cluster events
    class Membership extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {
        private ActorContext<ClusterEvent.ClusterDomainEvent> context;
        Cluster cluster;

        public Membership(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
            this.context = context;
            cluster = Cluster.get(context.getSystem());
            cluster.subscriptions().tell(new Subscribe<>(context.getSelf(), ClusterEvent.ClusterDomainEvent.class));
        }

        @Override
        public Receive<ClusterEvent.ClusterDomainEvent> createReceive() {
            ReceiveBuilder<ClusterEvent.ClusterDomainEvent> builder = newReceiveBuilder();
            return builder
                    .onMessage(ClusterEvent.MemberUp.class, this::memberUp)
                    //.onMessage(ClusterEvent.CurrentClusterState.class, this::currentState)
                    .onMessage(ClusterEvent.MemberRemoved.class, this::removed)
                    .onMessage(ClusterEvent.UnreachableMember.class, this::unreachable)
                    .onSignal(PostStop.class, this::postStop)
                    .onSignal(Terminated.class, this::terminated)
                    .build();
        }

        Behavior<ClusterEvent.ClusterDomainEvent> memberUp(ClusterEvent.MemberUp member) {
            members.put(member.member().address(), member.member());
            memberListeners.forEach(listener -> listener.memberUp(member.member().address().toString()));
            return Behaviors.same();
        }

        Behavior<ClusterEvent.ClusterDomainEvent> currentState(ClusterEvent.CurrentClusterState state) {
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    members.put(member.address(), member);
                    memberListeners.forEach(listener -> listener.memberUp(member.address().toString()));
                }
            }
            return Behaviors.same();
        }

        Behavior<ClusterEvent.ClusterDomainEvent> removed(ClusterEvent.MemberRemoved member) {
            members.remove(member.member().address());
            memberListeners.forEach(listener -> listener.memberDown(member.member().address().toString()));
            return Behaviors.same();
        }

        Behavior<ClusterEvent.ClusterDomainEvent> unreachable(ClusterEvent.UnreachableMember member) {
            members.remove(member.member().address());
            memberListeners.forEach(listener -> listener.memberDown(member.member().address().toString()));
            return Behaviors.same();
        }

        public Behavior<ClusterEvent.ClusterDomainEvent> postStop(PostStop postStop) {
            cluster.subscriptions().tell(new Unsubscribe(context.getSelf()));
            return Behaviors.same();
        }

        public Behavior<ClusterEvent.ClusterDomainEvent> terminated(Terminated terminated) {
            return Behaviors.stopped();
        }
    }

}
