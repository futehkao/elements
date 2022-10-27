package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.federation.*;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.notification.ShutdownNotification;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.subscribe.DefaultBroadcast;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.common.subscribe.Subscriber;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.web.cxf.JaxRSLauncher;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;

@SuppressWarnings("unchecked")
public abstract class CollectiveImpl implements Startable, Collective, Registry {
    protected static Logger logger = Logger.getLogger();
    public enum Type {
        CLUSTER,
        FEDERATION
    }

    protected Provision provision;
    protected String domainName;
    protected String hostAddress;
    protected String apiAddress;
    protected String[] seeds = new String[0];
    protected Map<String, Member> hostedMembers = new HashMap<>(); // Members hosted by this Federation.  The key is the memberId.
    private Map<String, Member> unmodifiableHosted = Collections.unmodifiableMap(hostedMembers);
    protected Beacon beacon;
    protected int fanout = 5;
    protected int cycle = 8;
    protected int eventCacheInitialCapacity = 10;
    protected long eventCacheExpire = 60000L;
    protected long seedRefreshInterval = 5000L;
    protected long eventInterval = 500L;
    protected long syncInterval = 2 * 60 * 1000L;  // 2 minutes.  This for syncing with one other node's data, anti-entropy.
    protected Long renewalInterval = 10 * 1000L;  // 10 seconds.  Periodically announcing presence, rumor mongering.
    protected Long renewalPadding = 30 * 1000L; // 30 seconds

    protected Long deadMemberRenewalInterval = 15 * 60 * 1000L;  // 15 min.  Periodically announcing presence, rumor mongering.
    protected int connectionTimeout = 15000; // 15 seconds
    protected int readTimeout = 15000; // 15 seconds
    protected JaxRSLauncher launcher;
    protected List<MemberListener> listeners = new LinkedList<>();
    protected AuthObserver authObserver;
    protected List<Service> services = new LinkedList<>();
    protected Map<Class, Service> serviceMap = new LinkedHashMap<>();
    private volatile boolean started = false;
    private SubZero subZero = new SubZero();
    private Executor executor = runnable -> new Thread(runnable).start();
    private DefaultBroadcast broadcast = new DefaultBroadcast(executor);

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    @Override
    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public Host[] getHosts() {
        Host[] hosts = new Host[hostedMembers.size()];
        int n = 0;
        for (Member m : hostedMembers.values()) {
            hosts[n] = new Host();
            hosts[n].setMemberId(m.getMemberId());
            hosts[n].setName(m.getName());
            n ++;
        }
        return hosts;
    }

    public void setHosts(Host[] hosts) {
        if (hosts == null) {
            hostedMembers.clear();
            return;
        }
        for (Host h : hosts) {
            addHostedMember(h.getMemberId(), h.getName());
        }
    }

    @Override
    public Map<String, Member> getHostedMembers() {
        return unmodifiableHosted;
    }

    public CollectiveImpl addHostedMember(String memberId, String name) {
        Member member = new Member();
        member.setName(name);
        member.setMemberId(memberId);
        return addHostedMember(member);
    }

    public CollectiveImpl addHostedMember(String memberId) {
        Member member = new Member();
        member.setMemberId(memberId);
        return addHostedMember(member);
    }

    public CollectiveImpl addHostedMember(Member member) {
        refresh(member);
        hostedMembers.put(member.getMemberId(), member);
        if (launcher != null && launcher.isStarted())
            beacon.announce(member);
        return this;
    }

    public String[] getSeeds() {
        return seeds;
    }

    public void setSeeds(String[] seeds) {
        this.seeds = seeds;
    }

    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String address) {
        this.hostAddress = address.trim();
        while (hostAddress.endsWith("/"))
            hostAddress = hostAddress.substring(0, hostAddress.length() - 1);
    }

    @Override
    public String getApiAddress() {
        return apiAddress;
    }

    public void setApiAddress(String apiAddress) {
        this.apiAddress = apiAddress.trim();
        while (apiAddress.endsWith("/"))
            apiAddress = apiAddress.substring(0, apiAddress.length() - 1);
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

    public Long getDeadMemberRenewalInterval() {
        return deadMemberRenewalInterval;
    }

    public void setDeadMemberRenewalInterval(Long deadMemberRenewalInterval) {
        this.deadMemberRenewalInterval = deadMemberRenewalInterval;
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

    public abstract Type getType();

    public Collection<Member> members() {
        return beacon.members();
    }

    @Override
    public Collection<Frequency> frequencies() {
        Collection<HailingFrequency> frequencies = beacon.frequencies().values();
        List<Frequency> list = new ArrayList<>(frequencies);
        return list;
    }

    public abstract void onEvent(@Nonnull Event event);

    public void onAnnounced(@Nonnull Event event) {
    }

    public List<MemberListener> getListeners() {
        return listeners;
    }

    public void setListeners(List<MemberListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void addListener(MemberListener listener) {
        listeners.add(listener);
    }

    @Override
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

    public SubZero getSubZero() {
        return subZero;
    }

    public void setSubZero(SubZero subZero) {
        this.subZero = subZero;
    }

    public Executor getExecutor() {
        return executor;
    }

    @Inject(optional = true)
    public void setExecutor(Executor executor) {
        this.executor = executor;
        broadcast.setExecutor(executor);
    }

    public DefaultBroadcast getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(DefaultBroadcast broadcast) {
        this.broadcast = broadcast;
    }

    protected Member refresh(Member member) {
        long duration = getRenewalInterval() + getCycle() * getEventInterval() + getRenewalPadding();
        member.setExpiration(System.currentTimeMillis() + duration);
        member.setAddress(getHostAddress());
        List<String> serviceList = new LinkedList<>();
        services.forEach(rs -> serviceList.add(rs.getServiceClass().getName()));
        member.setServices(serviceList);
        return member;
    }

    @Override
    public void start() {
        createLauncher();
        createServices();
        startServer();
        startBeacon();
        started = true;
        provision.getResourceManager().getNotificationCenter().addNotificationListener(ShutdownNotification.class,
                notification -> shutdown());
    }

    protected void createLauncher() {
        try {
            URL url;
            if (apiAddress != null) {
                url = new URL(apiAddress);
            } else {
                url = new URL(hostAddress);
                url = new URL(url.getProtocol(), "0.0.0.0", url.getPort(), url.getFile());
            }
            launcher = JaxRSLauncher.create(provision, url.toExternalForm())
                    .headerObserver(authObserver);
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
    }

    public void addService(Service service) {
        services.add(service);
    }

    public Service removeService(Class cls) {
        Iterator<Service> iterator = services.iterator();
        Service s = null;
        while (iterator.hasNext()) {
            Service service = iterator.next();
            if (service.getServiceClass().equals(cls)) {
                iterator.remove();
                s = service;
            }
        }
        return s;
    }

    public <T> T getServiceProvider(Class<T> cls) {
        Iterator<Service> iterator = services.iterator();
        Service s = null;
        while (iterator.hasNext()) {
            Service service = iterator.next();
            if (cls.isAssignableFrom(service.getProvider().getClass())) {
                s = service;
                break;
            }
        }
        return s != null ? (T) s.getProvider() : null;
    }

    @SuppressWarnings("unchecked")
    protected void createServices() {
        if (hostAddress == null)
            throw new IllegalStateException("hostAddress is null.");
        if (cycle <= 0)
            throw new IllegalStateException("cycle needs to be greater than 0.");
        if (fanout <= 0)
            throw new IllegalStateException("fanout needs to be greater than 0.");

        hostedMembers.forEach((id, m) -> {
            if (m.getMemberId() == null)
                throw new IllegalStateException("memberId is null.");
            if (m.getName() == null)
                m.setName(m.getMemberId());
            if (!m.getMemberId().equals(id))
                throw new IllegalStateException("memberId does not match hostedMembers' key");
        });

        setupBeacon();
        setupServices();

        List<String> serviceList = new LinkedList<>();
        services.forEach(rs -> serviceList.add(rs.getServiceClass().getName()));

        beacon.seeds(seeds);

        hostedMembers.forEach((id, m) -> {
            refresh(m);
        });
    }

    protected void setupBeacon() {
        beacon = new Beacon();
        beacon.setCollective(this);
        if (provision != null)
            provision.inject(beacon);

        BeaconAPI prototype = new BeaconAPI();
        prototype.setCollective(this);
        Service<Beacon, BeaconAPI> beaconAPI = new Service<>(beacon, BeaconAPI.class, prototype);
        services.add(0, beaconAPI);
    }

    protected void setupServices() {
        services.forEach( s -> {
            if (s.getServiceClass() == null && s.getPrototype() == null)
                return;
            serviceMap.put(s.getServiceClass(), s);
            if (s.getPrototype() != null)
                perInstanceService(s.getPrototype());
            else
                perInstanceService(s.getServiceClass());
        });
    }

    protected <T> void sharedService(Class<T> cls) {
       launcher.sharedService(cls);
    }

    protected <T> void perInstanceService(Class<T> cls) {
        launcher.perInstanceService(cls);
    }

    protected <T> void perInstanceService(T prototype) {
        launcher.perInstanceService(prototype);
    }

    protected void startServer() {
        // start JaxRSServer
        launcher.start();
    }

    protected void startBeacon() {
        beacon.start();
    }

    public void shutdown() {
        if (!started)
            return;

        try {
            if (beacon != null) {
                beacon.shutdown();
            }
            if (launcher != null) {
                launcher.stop();
            }
            beacon = null;
            launcher = null;
        } catch (Exception ex ) {
            logger.warn("Error shutting down collective with domain name=" + getDomainName(), ex);
        } finally {
            started = false;
        }
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        broadcast.subscribe(topic, subscriber);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        broadcast.unsubscribe(topic, subscriber);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publish(Notice<?> notice) {
        if (!notice.isExternalOnly())
            publishInternal(notice);
        beacon.broadcast(notice);
    }

    public Notice publishInternal(Notice<?> notice) {
        broadcast.publish(notice);
        return notice;
    }

    @Override
    public <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation) {
        return getRegistry().register(qualifier, interfaceClass, implementation);
    }

    @Override
    public <T> List<String> register(String qualifier, Class<T> interfaceClass, T implementation, InvocationHandler invoker) {
        return getRegistry().register(qualifier, interfaceClass, implementation, invoker);
    }

    @Override
    public Collection routes(String qualifier, Class interfaceClass) {
        return getRegistry().routes(qualifier, interfaceClass);
    }

    @Override
    public <T> Async<T> async(String qualifier, Class<T> interfaceClass) {
        return getRegistry().async(qualifier, interfaceClass);
    }

    @Override
    public <T> Async<T> async(String qualifier, Class<T> interfaceClass, long timeout, Routing routing) {
        return getRegistry().async(qualifier, interfaceClass, timeout, routing);
    }
}
