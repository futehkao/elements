package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.notification.ShutdownNotification;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.web.cxf.JaxRSServer;
import net.e6tech.elements.web.cxf.JaxResource;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.util.*;

public class Federation implements Startable {
    protected Provision provision;
    private String hostAddress;
    private List<Member> seeds = new LinkedList<>();
    private Map<String, Member> hostedMembers = new HashMap<>();
    protected Beacon beacon;
    private int fanout = 5;
    private int cycle = 8;
    private int eventCacheInitialCapacity = 10;
    private long eventCacheExpire = 60000L;
    private long seedRefreshInterval = 5000L;
    private long eventInterval = 500L;
    private long syncInterval = 2 * 60 * 1000L;  // 2 minutes.  This for syncing with one other node's data, anti-entropy.
    private Long renewalInterval = 10 * 1000L;  // 10 seconds.  Periodically announcing presence, rumor mongering.
    private Long renewalPadding = 30 * 1000L; // 30 seconds
    private int connectionTimeout = 15000; // 15 seconds
    private int readTimeout = 10000; // 10 seconds
    protected JaxRSServer server;
    private int instanceId = 1;
    private Map<String, Object> resolver = new HashMap<>();
    private List<MemberListener> listeners = new LinkedList<>();
    private AuthObserver authObserver;

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public Map<String, Member> getHostedMembers() {
        return hostedMembers;
    }

    public void setHostedMembers(Map<String, Member> hostedMembers) {
        this.hostedMembers = hostedMembers;
    }

    public Federation addHostedMember(String memberId, String name) {
        Member member = new Member();
        member.setName(name);
        member.setMemberId(memberId);
        refresh(member);
        hostedMembers.put(memberId, member);
        if (server != null && server.isStarted())
            beacon.announce(member);
        return this;
    }

    public List<Member> getSeeds() {
        return seeds;
    }

    public void setSeeds(List<Member> seeds) {
        this.seeds = seeds;
    }

    public Federation addSeed(String memberId, String hostAddress) {
        Member member = new Member();
        member.setHostAddress(hostAddress);
        member.setMemberId(memberId);
        getSeeds().add(member);
        return this;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String address) {
        this.hostAddress = address.trim();
        while (hostAddress.endsWith("/"))
            hostAddress = hostAddress.substring(0, hostAddress.length() - 1);
    }

    public int getFanout() {
        return fanout;
    }

    public void setFanout(int fanout) {
        this.fanout = fanout;
    }

    public int getCycle() {
        return cycle;
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
    }

    public int getEventCacheInitialCapacity() {
        return eventCacheInitialCapacity;
    }

    public void setEventCacheInitialCapacity(int eventCacheInitialCapacity) {
        this.eventCacheInitialCapacity = eventCacheInitialCapacity;
    }

    public long getEventCacheExpire() {
        return eventCacheExpire;
    }

    public void setEventCacheExpire(long eventCacheExpire) {
        this.eventCacheExpire = eventCacheExpire;
    }

    public long getSeedRefreshInterval() {
        return seedRefreshInterval;
    }

    public void setSeedRefreshInterval(long seedRefreshInterval) {
        this.seedRefreshInterval = seedRefreshInterval;
    }

    public long getEventInterval() {
        return eventInterval;
    }

    public void setEventInterval(long eventInterval) {
        this.eventInterval = eventInterval;
    }

    public long getSyncInterval() {
        return syncInterval;
    }

    public void setSyncInterval(long syncInterval) {
        this.syncInterval = syncInterval;
    }

    public Long getRenewalInterval() {
        return renewalInterval;
    }

    public void setRenewalInterval(Long renewalInterval) {
        this.renewalInterval = renewalInterval;
    }

    public Long getRenewalPadding() {
        return renewalPadding;
    }

    public void setRenewalPadding(Long renewalPadding) {
        this.renewalPadding = renewalPadding;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Collection<Member> members() {
        return beacon.members();
    }

    public void onEvent(@Nonnull Event event) {
        beacon.onEvent(event);
    }

    public List<MemberListener> getListeners() {
        return listeners;
    }

    public void setListeners(List<MemberListener> listeners) {
        this.listeners = listeners;
    }

    public void addListener(MemberListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MemberListener listener) {
        listeners.remove(listener);
    }

    public AuthObserver getAuthObserver() {
        return authObserver;
    }

    @Inject(optional = true)
    public void setAuthObserver(AuthObserver authObserver) {
        this.authObserver = authObserver;
    }

    protected Member refresh(Member member) {
        long duration = getRenewalInterval() + getCycle() * getEventInterval() + getRenewalPadding();
        member.setExpiration(System.currentTimeMillis() + duration);
        member.setHostAddress(getHostAddress());
        return member;
    }

    @Override
    public void start() {
        createServer();
        createServices();
        startServer();
        startServices();

        provision.getResourceManager().getNotificationCenter().addNotificationListener(ShutdownNotification.class,
                notification -> shutdown());
    }

    protected void createServer() {
        server = provision.newInstance(JaxRSServer.class);
        try {
            server.setAddresses(Arrays.asList(hostAddress));
        } catch (MalformedURLException e) {
            throw new SystemException(e);
        }
        server.setResolver(key -> resolver.get(key));
        server.setHeaderObserver(authObserver);
    }

    protected void createServices() {
        if (hostAddress == null)
            throw new IllegalStateException("hostAddress is null.");
        if (cycle <= 0)
            throw new IllegalStateException("cycle needs to be greater than 0.");
        if (fanout <= 0)
            throw new IllegalStateException("fanout needs to be greater than 0.");

        long duration = getRenewalInterval() + getCycle() * getEventInterval() + getRenewalPadding();
        hostedMembers.forEach((id, m) -> {
            if (m.getMemberId() == null)
                throw new IllegalStateException("memberId is null.");
            if (m.getName() == null)
                throw new IllegalStateException("member name is null.");
            if (!m.getMemberId().equals(id))
                throw new IllegalStateException("memberId does not match hostedMembers' key");
            refresh(m);
        });

        beacon = new Beacon();
        beacon.setFederation(this);
        beacon.seeds(seeds);
        provision.inject(beacon);

        BeaconAPI prototype = new BeaconAPI();
        prototype.setFederation(this);
        perInstanceService(prototype);
    }

    protected <T> T sharedService(Class<T> cls) {
        T api = provision.newInstance(cls);
        server.add(new JaxResource(cls)
                .prototype(Integer.toString(instanceId))
                .singleton());
        resolver.put(Integer.toString(instanceId), api);
        instanceId ++;
        return api;
    }

    protected <T> void perInstanceService(Class<T> cls) {
        server.add(new JaxResource(cls));
    }

    protected <T> void perInstanceService(T prototype) {
        server.add(new JaxResource(prototype.getClass())
                .prototype(Integer.toString(instanceId)));
        resolver.put(Integer.toString(instanceId), prototype);
        instanceId ++;
    }

    protected void startServer() {
        // start JaxRSServer
        provision.open().accept(Resources.class, res -> {
            server.initialize(res);
            server.start();
        });
    }

    protected void startServices() {
        beacon.start();
    }

    public void shutdown() {
        beacon.shutdown();
        server.stop();
    }
}
