package net.e6tech.elements.web.federation;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.federation.Member;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.subscribe.Notice;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Beacon {
    private static Logger logger = Logger.getLogger();
    private CollectiveImpl collective;

    private Cache<UUID, Event> events;
    private final Random random = new Random();
    private volatile boolean shutdown = false;
    private ReentrantLock seeding = new ReentrantLock();
    private List<HailingFrequency> seedFrequencies = Collections.emptyList();
    private Thread eventThread;
    private Map<String, HailingFrequency> frequencies = new ConcurrentHashMap<>(128); // memberId to frequency

    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        Beacon.logger = logger;
    }

    public CollectiveImpl getCollective() {
        return collective;
    }

    public void setCollective(CollectiveImpl collective) {
        this.collective = collective;
    }

    protected void seeds(String[] seeds) {
        if (seeds == null) {
            seedFrequencies = Collections.emptyList();
            return;
        }
        List <HailingFrequency> list = new ArrayList<>(seeds.length);
        for (String s : seeds) {
            list.add(new HailingFrequency(s, collective));
        }
        seedFrequencies = Collections.unmodifiableList(list);
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }

    public Collection<Member> members() {
        return frequencies.values().stream().map(HailingFrequency::getMember).collect(Collectors.toList());
    }

    protected int knownFrequencies() {
         return frequencies.size();
    }

    protected void trimFrequencies() {
        frequencies.values().removeIf(f -> !collective.getHostedMembers().containsKey(f.memberId()) &&
                f.getMember().getExpiration() < System.currentTimeMillis());
    }

    public HailingFrequency getFrequency(String memberId) {
        return frequencies.get(memberId);
    }

    protected HailingFrequency updateFrequency(Member member) {
        HailingFrequency f = frequencies.get(member.getMemberId());

        if (f == null) {
            f = new HailingFrequency(member, collective);
            frequencies.put(member.getMemberId(), f);
            if (f.getMember().getExpiration() < member.getExpiration())
                f.setMember(member);

            HailingFrequency f2 = f;
            collective.getExecutor().execute(() -> collective.getListeners().forEach(listener -> listener.added(f2)));
        } else if (f.getMember().getExpiration() < member.getExpiration()) {
            f.setMember(member);
        }

        return f;
    }

    protected void updateFrequencies(Collection<Member> list) {
        for (Member member : list) {
            updateFrequency(member);
        }
    }

    protected void removeFrequency(HailingFrequency frequency) {
        if (frequency != null && !collective.getHostedMembers().containsKey(frequency.getMember().getMemberId())) {
            HailingFrequency c = frequencies.remove(frequency.memberId());
            collective.getExecutor().execute(() -> collective.getListeners().forEach(listener -> listener.removed(c)));
        }
    }

    protected void removeFrequencies(Collection<Member> list) {
        for (Member member : list) {
            if (!collective.getHostedMembers().containsKey(member.getMemberId())) {
                HailingFrequency c = frequencies.remove(member.getMemberId());
                collective.getExecutor().execute(() -> collective.getListeners().forEach(listener -> listener.removed(c)));
            }
        }
    }

    protected Map<String, HailingFrequency> frequencies() {
        Map<String, HailingFrequency> map = new HashMap<>(frequencies);
        return Collections.unmodifiableMap(map);
    }

    // need to have a background thread to poll liveliness.
    public void start() {
        if (events != null) {
            events.invalidateAll();
            events.cleanUp();
        }
        events = CacheBuilder.newBuilder()
                .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
                .initialCapacity(collective.getEventCacheInitialCapacity())
                .expireAfterAccess(collective.getEventCacheExpire(), TimeUnit.MILLISECONDS)
                .build();

        shutdown = false;

        collective.getHostedMembers().values().forEach(m -> {
            collective.refresh(m);
            updateFrequency(m);
        });
        Thread thread = new Thread(this::run);
        thread.start();
    }

    private void run() {

        // wait till the api is up
        while (frequencies.size() == 0) {
            HailingFrequency frequency = frequencies.values().iterator().next();
            try {
                frequency.beacon().members();
                break;
            } catch (Exception e) {
                // ignore
            }

            try {
                Thread.sleep(100L); // wait for its own API to start.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }

        // send out new Event to seeds
        logger.trace("{} start seeding.", collective.getHostAddress());
        seeds("starting");
        logger.trace("{} done seeding.", collective.getHostAddress());

        // create a thread to process events
        eventThread = new Thread(this::events);
        eventThread.start();

        // create a thread to sync members
        Thread sync = new Thread(this::sync);
        sync.start();

        // create a thread to send out new lease events
        try {
            Thread.sleep(collective.getRenewalInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Thread renewal = new Thread(this::renewal);
        renewal.start();
    }

    private void seeds(String purpose) {
        if (!seeding.tryLock())
            return;

        try {
            while (true) {
                boolean done = syncSeeds(purpose);
                logger.trace("{} {} frequencies announce ", collective.getHostAddress(), purpose);

                if (syncMembers(frequencies().values())) {
                    announce();
                    done = true;
                }
                done = syncMembers(frequencies().values()) || done;

                if (done)
                    break;

                try {
                    Thread.sleep(collective.getSeedRefreshInterval());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            seeding.unlock();
        }
    }

    private boolean syncSeeds(String purpose) {

        // detected special situation for seeds.  If there is only one seed, and it happens to be this host then
        // we would have to announce and return.
        if (seedFrequencies.size() == 1) {
            try {
                URI hostAddress = new URI(collective.getHostAddress());
                URI seedAddress = new URI(seedFrequencies.get(0).getMember().getAddress());
                if (hostAddress.equals(seedAddress)) {
                    announce();
                    return true;
                }
            } catch (Exception e) {
                logger.warn("Invalid seed or host address", e);
            }
        }

        boolean completed = true;
        if (seedFrequencies.isEmpty()) {
            announce();
        } else if (syncMembers(seedFrequencies)) { // may the seeds are dead? If not, announce.
            logger.trace("{} {} seeds announce ", collective.getHostAddress(), purpose);
            announce();
        } else {
            completed = false;
        }

        return completed;
    }

    public void onEvent(Event event) {
        if (shutdown)
            return;

        // seen it before?
        synchronized (events) {
            Event existing = events.getIfPresent(event.getUuid());
            if (existing != null && existing.getUuid().equals(event.getUuid())) {
                event.addVisited(existing.getVisited());
                if (event.getCycle() > existing.getCycle())
                    event.setCycle(existing.getCycle());
                events.put(event.getUuid(), event);
                return;
            }
        }

        // updating frequencies.
        handleEvent(event);

        // add to events so that it can be propagated.
        if (event.getCycle() > 0) {
            synchronized (events) {
                events.put(event.getUuid(), event);
                // had to create hostedMemberIds then call event.addVisited to avoid current modification
                Set<String> hostedMemberIds = new HashSet<>(collective.getHostedMembers().size() * 2 + 1);
                collective.getHostedMembers().values().forEach(m -> hostedMemberIds.add(m.getMemberId()));
                event.addVisited(hostedMemberIds);
                event.getVisited().addAll(collective.getHostedMembers().keySet());
                events.notifyAll();
            }
        }
    }

    private void handleEvent(Event event) {
        logger.trace("{} onEvent: {}" , collective.getHostAddress(), event);
        if (Objects.equals(event.getDomainName(), collective.getDomainName()) && event.getCollectiveType() == collective.getType()) {
            if (event.getType() == Event.Type.ANNOUNCE) {
                updateFrequencies(event.getMembers());
            } else if (event.getType() == Event.Type.REMOVE) {
                removeFrequencies(event.getMembers());
            } else if (event.getType() == Event.Type.BROADCAST) {
                Notice<?> notice = collective.getSubZero().thaw(event.getPayload());
                if (notice != null)
                    collective.publishInternal(notice);
            }
        }
    }

    private void decrementCycle(Event event) {
        if (event.getCycle() > 0) {
            event.setCycle(event.getCycle() - 1);
        }
    }

    private void announce() {
        trimFrequencies();

        collective.getHostedMembers().values().forEach(collective::refresh);
        List<Member> list = new ArrayList<>(collective.getHostedMembers().size());
        list.addAll(collective.getHostedMembers().values());
        Event event = new Event(collective.getDomainName(), Event.Type.ANNOUNCE, collective.getType(), list, collective.getCycle());
        event.getVisited().addAll(collective.getHostedMembers().keySet());
        events.put(event.getUuid(), event);
        collective.onAnnounced(event);
        gossip(event, "announce");
    }

    void announce(Member member) {
        collective.refresh(member);
        List<Member> list = new ArrayList<>();
        list.add(member);
        onEvent(new Event(collective.getDomainName(), Event.Type.ANNOUNCE, collective.getType(), list, collective.getCycle()));
    }

    void broadcast(Notice<?> notice) {
        trimFrequencies();

        collective.getHostedMembers().values().forEach(collective::refresh);
        List<Member> list = new ArrayList<>(collective.getHostedMembers().size());
        list.addAll(collective.getHostedMembers().values());
        Event event = new Event(collective.getDomainName(), Event.Type.BROADCAST, collective.getType(), list, collective.getCycle());
        event.getVisited().addAll(collective.getHostedMembers().keySet());
        events.put(event.getUuid(), event);
        event.setPayload(collective.getSubZero().freeze(notice));
        gossip(event, "broadcast");
    }

    private void renewal() {
        while (!shutdown) {
            try {
                long interval = collective.getRenewalInterval() + collective.getCycle() * collective.getEventInterval();
                if (knownFrequencies() <= collective.getHostedMembers().size()) {
                    seeds("renewal");
                    Thread.sleep(interval);
                } else {
                    long now = System.currentTimeMillis();
                    logger.trace("{} renewal: ", collective.getHostAddress());
                    announce();
                    long sleep = interval - (System.currentTimeMillis() - now);
                    if (sleep < 0) // during trace
                        sleep = collective.getRenewalInterval();
                    Thread.sleep(sleep);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger.error("renewal", ex);
            }
        }
    }

    private void events() {
        while (!shutdown) {
            try {
                processEvents();
                Thread.sleep(collective.getEventInterval());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                logger.error("events", ex);
            }
        }
    }

    private void processEvents() {
        List<Event> copy;
        synchronized (events) {
            while (events.size() == 0 || knownFrequencies() <= collective.getHostedMembers().size()) {
                try {
                    events.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            copy = new LinkedList<>(events.asMap().values());
            copy.forEach(this::decrementCycle);
            copy.removeIf(e -> e.getCycle() <= 0);
            events.invalidateAll();
        }
        Set<String> seen = new HashSet<>();
        StringBuilder builder = new StringBuilder();
        copy.forEach( evt -> {
            builder.setLength(0);
            builder.append("[").append(evt.getType().name()).append("]");
            boolean first = true;
            for (Member m : evt.getMembers()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(",");
                }
                builder.append(m.getAddress()).append(".").append(m.getMemberId());
            }
            String str = builder.toString();
            if (!seen.contains(str))
                gossip(evt, "events");
            seen.add(str);
        });
    }

    private void sync() {
        while (!shutdown) {
            try {
                if (knownFrequencies() >= collective.getHostedMembers().size()) {
                    syncMembers(frequencies().values());
                }
                Thread.sleep(collective.getSyncInterval());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger.error("sync", ex);
            }
        }
    }

    private boolean syncMembers(Collection<HailingFrequency> fList) {
        if (fList.isEmpty())
            return false;

        // filter out self
        List<HailingFrequency> list = fList.stream().filter(c -> !collective.getHostedMembers().containsKey(c.memberId())).collect(Collectors.toList());

        long interval = collective.getRenewalInterval() + collective.getCycle() * collective.getEventInterval();
        list = list.stream().filter(c -> {
            boolean filter = System.currentTimeMillis() - c.getLastConnectionError() > c.getConsecutiveError() * interval;
            if (System.currentTimeMillis() - c.getLastConnectionError() > collective.getDeadMemberRenewalInterval())
                return true;
            return filter;
        })
                .collect(Collectors.toList());

        if (list.isEmpty())
            return false; // because list contain only self

        URI hostAddress = null;
        try {
            hostAddress = new URI(collective.getHostAddress());
        } catch (Exception e) {
            logger.warn("Invalid host address " + collective.getHostAddress(), e);
            return false;
        }
        while (true) {
            HailingFrequency frequency = null;
            try {
                if (list.isEmpty())
                    return false;
                int n = random.nextInt(list.size());
                frequency = list.remove(n);
                URI memberURL = new URI(frequency.getMember().getAddress());
                if (! hostAddress.equals(memberURL)) {
                    Collection<Member> list2 = frequency.beacon().members();
                    logger.trace("{} syncMembers: {}", collective.getHostAddress(), list2);
                    updateFrequencies(list2);
                    return true;
                }
            } catch (Exception ex) {
                removeFrequency(frequency);
            }
        }
    }

    public void shutdown() {
        if (shutdown)
            return;
        shutdown = true;

        long now = System.currentTimeMillis();
        collective.getHostedMembers().values().forEach(m -> m.setExpiration(now));
        List<Member> list = new ArrayList<>(collective.getHostedMembers().size());
        list.addAll(collective.getHostedMembers().values());
        Event event = new Event(collective.getDomainName(), Event.Type.REMOVE, collective.getType(), list, collective.getCycle());
        event.getVisited().addAll(collective.getHostedMembers().keySet());
        for (Member member : list) {
            HailingFrequency c = frequencies.remove(member.getMemberId());
            collective.getExecutor().execute(() -> collective.getListeners().forEach(listener -> listener.removed(c)));
        }
        gossip(event, "shutdown");

        if (eventThread != null) {
            eventThread.interrupt();
            eventThread = null;
        }
        if (events != null) {
            events.invalidateAll();
        }
    }

    /**
     * Never call gossip outside of announce , processEvent and shutdown. Otherwise, too many synchronous api calls.
     * @param event
     * @return
     */

    private void gossip(Event event, String reason) {
        // filter out already visited
        List<HailingFrequency> list = frequencies.values().stream()
                .filter(c -> !event.getVisited().contains(c.memberId()))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return;
        }

        List<HailingFrequency> chosen;
        int fanout = collective.getFanout();
        if (fanout >= list.size()) {
            chosen = list;
        } else {
            chosen = new ArrayList<>(fanout);
            while (fanout > 0) {
                fanout --;
                int n = random.nextInt(list.size());
                chosen.add(list.get(n));
                list.remove(n);
            }
        }

        collective.getExecutor().execute(() ->
            chosen.forEach(frequency -> {
                logger.trace("{}.{} gossip to {} an event {} ", collective.getHostAddress(), reason, frequency.memberId(), event);
                try {
                    frequency.beacon().onEvent(event);
                } catch (Exception ex) {
                    // remove connector from members.
                    removeFrequency(frequency);
                }
            })
        );
    }
}
