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
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;
import akka.cluster.typed.Unsubscribe;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.actor.typed.CommonBehavior;
import net.e6tech.elements.common.actor.typed.Typed;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.cluster.invocation.Registry;
import net.e6tech.elements.network.cluster.invocation.RegistryImpl;
import net.e6tech.elements.network.cluster.messaging.Messaging;

import java.util.*;

@SuppressWarnings("unchecked")
public class ClusterNode implements Initializable {

    public static final long DEFAULT_TIME_OUT = 10000L;

    private String name;
    private Genesis genesis;
    private Membership membership;
    private Messaging broadcast;
    private Registry registry;
    private Class<? extends Registry> registryClass = RegistryImpl.class;
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
        try {
            return membership.talk(MemberEvents.class).askAndWait(MemberEvents.Members::new);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public List<MemberListener> getListeners() {
        try {
            return membership.talk(MemberEvents.class).askAndWait(MemberEvents.Listeners::new);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public void addMemberListener(MemberListener listener) {
        membership.talk(MemberEvents.class).tell(new MemberEvents.AddListener(listener));
    }

    public void removeMemberListener(MemberListener listener) {
        membership.talk(MemberEvents.class).tell(new MemberEvents.RemoveListener(listener));
    }

    public Class<? extends Registry> getRegistryClass() {
        return registryClass;
    }

    public void setRegistryClass(Class<? extends Registry> registryClass) {
        this.registryClass = registryClass;
    }

    public void initialize(Resources resources) {
        if (genesis == null) {
            genesis = new Genesis();
            genesis.setName(getName());
            genesis.setTimeout(getTimeout());
            genesis.initialize(resources);
        }

        initialize(genesis);
    }

    public void initialize(Genesis genesis) {
        this.genesis = genesis;
        setName(genesis.getName());
        setTimeout(genesis.getTimeout());
        start();
    }

    public void start() {
        if (started)
            return;

        if (membership == null)
            membership = genesis.getGuardian().childActor(Membership.class).spawnNow(Membership::new);

        if (broadcast == null) {
            broadcast = new Messaging();
            broadcast.setTimeout(timeout);
        }

        if (registry == null) {
            try {
                registry = getRegistryClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
            registry.setTimeout(timeout);
        }

        broadcast.start(genesis.getGuardian());
        registry.start(genesis.getGuardian());
        started = true;
    }

    public void shutdown() {
        membership.talk().stop();
        broadcast.shutdown();
        registry.shutdown();
        genesis.terminate();
        started = false;
    }

    // listener to cluster events
    static class Membership extends CommonBehavior<ClusterEvent.ClusterDomainEvent> {
        private Map<Address, Member> members = new HashMap<>();
        private List<MemberListener> memberListeners = new ArrayList<>();

        public Membership(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
            super(context);
        }

        /**
         * Tells akka Cluster that this Membership want to subscribe to ClusterEvent.ClusterDomainEvent
         */
        @Override
        public void initialize() {
            addExtension((ctx, owner) -> new MembershipExtension(ctx, members, memberListeners));
            Cluster cluster = Cluster.get(getContext().getSystem());
            cluster.subscriptions().tell(new Subscribe<>(getContext().getSelf(), ClusterEvent.ClusterDomainEvent.class));
        }

        @Typed
        void memberUp(ClusterEvent.MemberUp member) {
            members.put(member.member().address(), member.member());
            memberListeners.forEach(listener -> listener.memberUp(member.member().address().toString()));
        }

        @Typed
        void currentState(ClusterEvent.CurrentClusterState state) {
             for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    members.put(member.address(), member);
                    memberListeners.forEach(listener -> listener.memberUp(member.address().toString()));
                }
            }
        }

        @Typed
        void removed(ClusterEvent.MemberRemoved member) {
            members.remove(member.member().address());
            memberListeners.forEach(listener -> listener.memberDown(member.member().address().toString()));
        }

        @Typed
        void unreachable(ClusterEvent.UnreachableMember member) {
            members.remove(member.member().address());
            memberListeners.forEach(listener -> listener.memberDown(member.member().address().toString()));
        }

        @SuppressWarnings("unchecked")
        @Typed
        public void postStop(PostStop postStop) {
            Cluster cluster = Cluster.get(getContext().getSystem());
            cluster.subscriptions().tell(new Unsubscribe(getContext().getSelf()));
        }

        @Typed
        public Behavior<ClusterEvent.ClusterDomainEvent> terminated(Terminated terminated) {
            return Behaviors.stopped();
        }
    }

    static class MembershipExtension extends CommonBehavior<MemberEvents> {
        private Map<Address, Member> members;
        private List<MemberListener> memberListeners;

        protected MembershipExtension(ActorContext<MemberEvents> context, Map<Address, Member> members, List<MemberListener> memberListeners) {
            super(context);
            this.members = members;
            this.memberListeners = memberListeners;
        }

        @Typed
        public void members(MemberEvents.Members members) {
            members.sender.tell(new HashMap<>(this.members));
        }

        @Typed
        public void listeners(MemberEvents.Listeners listeners) {
            listeners.sender.tell(new ArrayList<>(this.memberListeners));
        }

        @Typed
        public void addListener(MemberEvents.AddListener add) {
            memberListeners.add(add.listener);
        }

        @Typed
        public void removeListener(MemberEvents.RemoveListener remove) {
            memberListeners.remove(remove.listener);
        }
    }

    interface MemberEvents {

        class Members implements MemberEvents {
            ActorRef<Map<Address, Member>> sender;
            public Members(ActorRef<Map<Address, Member>> sender) {
                this.sender = sender;
            }
        }

        class Listeners implements MemberEvents {
            ActorRef<List<MemberListener>> sender;
            public Listeners(ActorRef<List<MemberListener>> sender) {
                this.sender = sender;
            }
        }

        class AddListener implements MemberEvents {
            MemberListener listener;
            public AddListener(MemberListener listener) {
                this.listener = listener;
            }
        }

        class RemoveListener implements MemberEvents {
            MemberListener listener;
            public RemoveListener(MemberListener listener) {
                this.listener = listener;
            }
        }
    }

}
