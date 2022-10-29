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
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;
import akka.cluster.typed.Unsubscribe;
import net.e6tech.elements.common.actor.GenesisActor;
import net.e6tech.elements.common.actor.typed.Ask;
import net.e6tech.elements.common.actor.typed.Receptor;
import net.e6tech.elements.common.actor.typed.Typed;
import net.e6tech.elements.common.federation.Registry;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.cluster.invocation.RegistryActor;
import net.e6tech.elements.network.cluster.messaging.Messaging;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class ClusterNode implements Initializable, net.e6tech.elements.common.federation.Genesis {

    public static final long DEFAULT_TIME_OUT = 10000L;

    private String name;
    private GenesisActor genesis;
    private Membership membership;
    private Messaging broadcast;
    private RegistryActor registry;
    private Class<? extends RegistryActor> registryClass = RegistryActor.class;
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

    public GenesisActor getGenesis() {
        return genesis;
    }

    @Inject(optional = true)
    public void setGenesis(GenesisActor genesis) {
        this.genesis = genesis;
    }

    public Broadcast getBroadcast() {
        return broadcast;
    }

    public RegistryActor getRegistry() {
        return registry;
    }

    public Map<Address, Member> getMembers() {
        try {
            return membership.getExtension().members(new MemberEvents.Members());
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public List<MemberListener> getListeners() {
        try {
            return membership.getExtension().listeners(new MemberEvents.Listeners());
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

    public Class<? extends RegistryActor> getRegistryClass() {
        return registryClass;
    }

    public void setRegistryClass(Class<? extends RegistryActor> registryClass) {
        this.registryClass = registryClass;
    }

    public void initialize(Resources resources) {
        if (genesis == null) {
            genesis = new GenesisActor();
            genesis.setName(getName());
            genesis.setTimeout(getTimeout());
            genesis.initialize(resources);
        }

        initialize(genesis);
    }

    public void initialize(GenesisActor genesis) {
        this.genesis = genesis;
        setName(genesis.getName());
        setTimeout(genesis.getTimeout());
        start();
    }

    public void start() {
        if (started)
            return;

        if (membership == null)
            membership = genesis.getGuardian().childActor(Membership.class).spawnNow(new Membership());

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

    @Override
    public CompletionStage<Void> async(Runnable runnable) {
        return genesis.async(runnable);
    }

    @Override
    public <R> CompletionStage<R> async(Supplier<R> callable) {
        return genesis.async(callable);
    }

    // listener to cluster events
    // NOTE Both Membership and MembershipExtension share the same ActorContext and, therfore,
    // they're thread-safe when accessing members and memberListeners
    public static class Membership extends Receptor<ClusterEvent.ClusterDomainEvent, Membership> {
        private Map<Address, Member> members = new HashMap<>();
        private List<MemberListener> memberListeners = new ArrayList<>();
        private MembershipExtension extension;

        public MembershipExtension getExtension() {
            return extension;
        }

        /**
         * Tells akka Cluster that this Membership want to subscribe to ClusterEvent.ClusterDomainEvent
         */
        @Override
        protected void initialize() {
            extension = addExtension(new MembershipExtension(members, memberListeners)).virtualize();
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

    public static class MembershipExtension extends Receptor<MemberEvents, MembershipExtension> {
        private Map<Address, Member> members;
        private List<MemberListener> memberListeners;

        public MembershipExtension() {
        }

        protected MembershipExtension(Map<Address, Member> members, List<MemberListener> memberListeners) {
            this.members = members;
            this.memberListeners = memberListeners;
        }

        @Typed
        public Map<Address, Member> members(MemberEvents.Members members) {
            return new HashMap<>(this.members);
        }

        @Typed
        public List<MemberListener> listeners(MemberEvents.Listeners listeners) {
            return new ArrayList<>(this.memberListeners);
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

        class Members extends Ask implements MemberEvents {
            public Members() {
            }

            public Members(ActorRef<Map<Address, Member>> sender) {
                setSender(sender);
            }
        }

        class Listeners extends Ask implements MemberEvents {
            public Listeners() {
            }

            public Listeners(ActorRef<List<MemberListener>> sender) {
                setSender(sender);
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
