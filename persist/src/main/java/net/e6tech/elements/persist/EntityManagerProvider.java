/*
Copyright 2015-2019 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.persist;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.reflection.Annotator;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.subscribe.Broadcast;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.metamodel.Metamodel;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by futeh.
 */
public abstract class EntityManagerProvider implements ResourceProvider, Initializable {
    private static final String DEFAULT_NAME = "default";
    private static final String[] DEFAULT_PROVIDER_NAMES = new String[] {DEFAULT_NAME};
    private static Logger logger = Logger.getLogger();
    private ExecutorService threadPool;
    private NotificationCenter notificationCenter;
    protected EntityManagerFactory emf;
    private String persistenceUnitName;
    private Map persistenceProperties;
    private Broadcast broadcast;
    private long transactionTimeout = 0;
    private boolean monitorTransaction = true;
    private long longTransaction = 200L;  // queries that exceeds this value is considered a long transaction.
    private boolean firstQuery = true;
    private AtomicInteger ignoreInitialLongTransactions = new AtomicInteger(1);
    private BlockingQueue<EntityManagerMonitor> monitorQueue = new LinkedBlockingQueue<>();
    private final List<EntityManagerMonitor> entityManagerMonitors = new ArrayList<>();
    private volatile boolean shutdown = false;
    private String providerName = DEFAULT_NAME;
    private ResourceManager resourceManager;
    private InvocationListener<EntityManager> entityManagerListener;
    private InvocationListener<Query> queryListener;

    public EntityManagerProvider() {
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    @Inject(optional = true)
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public NotificationCenter getNotificationCenter() {
        return notificationCenter;
    }

    @Inject(optional = true)
    public void setNotificationCenter(NotificationCenter center) {
        this.notificationCenter = center;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    @Inject
    public void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public Broadcast getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(Broadcast broadcast) {
        this.broadcast = broadcast;
    }

    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    public void setPersistenceUnitName(String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    public Map getPersistenceProperties() {
        return persistenceProperties;
    }

    public void setPersistenceProperties(Map persistenceProperties) {
        this.persistenceProperties = persistenceProperties;
    }

    public long getTransactionTimeout() {
        return transactionTimeout;
    }

    public void setTransactionTimeout(long transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    public long getLongTransaction() {
        return longTransaction;
    }

    public void setLongTransaction(long longTransaction) {
        this.longTransaction = longTransaction;
    }

    public boolean isMonitorTransaction() {
        return monitorTransaction;
    }

    public void setMonitorTransaction(boolean monitorTransaction) {
        this.monitorTransaction = monitorTransaction;
    }

    public int getIgnoreInitialLongTransactions() {
        if (ignoreInitialLongTransactions == null)
            return 0;
        return ignoreInitialLongTransactions.get();
    }

    public void setIgnoreInitialLongTransactions(int n) {
        this.ignoreInitialLongTransactions = new AtomicInteger(n);
    }

    public List<EntityManagerMonitor> getEntityManagerMonitors() {
        return entityManagerMonitors;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public InvocationListener<EntityManager> getEntityManagerListener() {
        return entityManagerListener;
    }

    public void setEntityManagerListener(InvocationListener<EntityManager> entityManagerListener) {
        this.entityManagerListener = entityManagerListener;
    }

    public InvocationListener<Query> getQueryListener() {
        return queryListener;
    }

    public void setQueryListener(InvocationListener<Query> queryListener) {
        this.queryListener = queryListener;
    }

    protected void evictCollectionRegion(EvictCollectionRegion notification) {
    }

    protected void evictEntityRegion(EvictEntityRegion region) {
    }

    protected void evictEntity(EvictEntity ref) {
    }

    public void initialize(Resources resources) {
        startMonitoring();

        emf = Persistence.createEntityManagerFactory(persistenceUnitName, persistenceProperties);

        EntityManager em = null;
        try {
            em = emf.createEntityManager();
            Metamodel meta = emf.getMetamodel();
            meta.getManagedTypes().forEach(type -> {
                type.getDeclaredAttributes();
                type.getPersistenceType();
            });
        } finally {
            if (em != null)
                em.close();
        }

        NotificationCenter center = resources.getNotificationCenter();
        center.subscribe(EvictCollectionRegion.class,
                notice -> evictCollectionRegion(notice.getUserObject()));

        center.subscribe(EvictEntityRegion.class,
                notice -> evictEntityRegion(notice.getUserObject()));

        center.subscribe(EvictEntity.class,
                notice -> evictEntity(notice.getUserObject()));
    }

    private String[] providerNames(Resources resources) {
        Optional<EntityManagerConfig> config = resources.configurator().annotation(EntityManagerConfig.class);
        String[] names = config.map(EntityManagerConfig::names).orElse(DEFAULT_PROVIDER_NAMES);
        if (names.length == 0)
            names = DEFAULT_PROVIDER_NAMES;
        return names;
    }

    private void shouldOpen(Resources resources, String alias) {
        Optional<EntityManagerConfig> config = resources.configurator().annotation(EntityManagerConfig.class);
        if (config.isPresent() && config.get().disable())
            throw new NotAvailableException();

        String[] names = providerNames(resources);

        // caller did not provide a list of named provider so that we would let every configured EntityManagerProvider through.
        if (names.length == 0)
            return;

        boolean found = false;
        for (String n : names) {
            if (n.equals(alias)) {
                found = true;
                break;
            }
        }

        Map<String, EntityManager> map = resources.getMapVariable(EntityManager.class);
        if (map.get(alias) != null) {
            throw new IllegalStateException("There is already an EntityManagerProvider named " + alias + " configured!");
        }

        if (!found)
            throw new NotAvailableException();
    }

    protected EntityManagerConfig configuration(Resources resources, String alias) {

        Optional<EntityManagerConfig> config = resources.configurator().annotation(EntityManagerConfig.class);

        // timeout
        long timeout = config.map(EntityManagerConfig::timeout).orElse(transactionTimeout);
        if (timeout == 0L)
            timeout = transactionTimeout;

        // timout extension
        long timeoutExt = config.map(EntityManagerConfig::timeoutExtension).orElse(0L);
        timeout += timeoutExt;

        // should monitor
        boolean monitor = config.map(EntityManagerConfig::monitor).orElse(monitorTransaction);

        // longQuery time
        long longQuery = config.map(EntityManagerConfig::longTransaction).orElse(longTransaction);
        if (longQuery == 0L)
            longQuery = longTransaction;

        if (firstQuery) {
            firstQuery = false;
            if (longQuery < 1000L)
                longQuery = 1000L;
        }

        long longQueryFinal = longQuery;
        long timeoutFinal = timeout;
        String[] names = providerNames(resources);
        EntityManagerConfig result = Annotator.create(EntityManagerConfig.class,
                (v, a) ->
            v.set(a::names, names)
                    .set(a::disable, config.map(EntityManagerConfig::disable).orElse(false))
                    .set(a::timeout, timeoutFinal)
                    .set(a::longTransaction, longQueryFinal)
                    .set(a::monitor, monitor)
                    .set(a::timeoutExtension, timeoutExt)
        );

        resources.getMapVariable(EntityManagerConfig.class)
                .put(alias, result);
        return result;
    }

    @Override
    public final void onOpen(Resources resources) {
        shouldOpen(resources, getProviderName());
        EntityManagerConfig config = configuration(resources, getProviderName());
        onOpen(resources, getProviderName(), config);
    }

    protected void onOpen(Resources resources, String alias, EntityManagerConfig config) {
        EntityManager em = resources.getInstance(EntityManagerBuilder.class, () -> (r, a, f) -> f.createEntityManager())
                .build(resources, alias, emf);

        if (config.monitor()) {
            EntityManagerMonitor entityManagerMonitor = new EntityManagerMonitor(alias, threadPool, this,
                    resources,
                    em, System.currentTimeMillis() + config.timeout(), new Throwable());
            monitor(entityManagerMonitor);
            resources.getMapVariable(EntityManagerMonitor.class)
                    .put(alias, entityManagerMonitor);
        }

        EntityManagerInvocationHandler emHandler = new EntityManagerInvocationHandler(resources,
                em, alias, this, config,
                getEntityManagerListener(), getQueryListener());
        emHandler.setLongTransaction(config.longTransaction());
        emHandler.setIgnoreInitialLongTransactions(ignoreInitialLongTransactions);

        EntityManager proxy = (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{EntityManager.class, EntityManagerInfo.class}, emHandler);

        // first come first win unless it is the DEFAULT
        if (!resources.hasInstance(EntityManager.class)) {
            resources.bind(EntityManager.class, proxy);
        } else if (alias.equals(DEFAULT_NAME)) {
            resources.rebind(EntityManager.class, proxy);
        }

        resources.getMapVariable(EntityManager.class)
                .put(alias, proxy);

        resources.getMapVariable(EntityManagerProvider.class)
                .put(alias, this);

        em.getTransaction().begin();
    }

    // Submits a thread task to monitor expired EntityManagers.
    // the thread would break out after monitorIdle time.
    // when another monitor shows up, the thread task would resume.
    @SuppressWarnings({"squid:S1188", "squid:S134", "squid:S3776", "squid:S899"})
    private void monitor(EntityManagerMonitor monitor) {
        // entityManagerMonitors contains open, committed and aborted entityManagers.
        if (!shutdown)
            monitorQueue.offer(monitor);
    }

    @SuppressWarnings({"squid:S3776", "squid:S1181", "squid:S1141"})
    protected void startMonitoring() {
        // starting a thread to monitor
        if (threadPool == null) {
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "EntityManagerProvider");
                thread.setName("EntityManagerProvider-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }

        threadPool.execute(()-> {
            while (!shutdown) {
                try {
                    long expiration = 0;
                    monitorQueue.drainTo(entityManagerMonitors);
                    Iterator<EntityManagerMonitor> iterator = entityManagerMonitors.iterator();
                    while (iterator.hasNext()) {
                        EntityManagerMonitor m = iterator.next();
                        if (!m.getEntityManager().isOpen()) { // already closed
                            iterator.remove();
                        } else if (m.getExpiration() < System.currentTimeMillis()) {
                            iterator.remove();
                            m.rollback();  // rollback
                        } else {
                            // for find out the shortest sleep time
                            if (expiration == 0 || m.getExpiration() < expiration)
                                expiration = m.getExpiration();
                        }
                    }

                    long sleep = 0L;
                    if (expiration > 0) {
                        sleep = expiration - System.currentTimeMillis();
                        if (sleep < 0) {
                            // probably due to debugging
                            if (!entityManagerMonitors.isEmpty())
                                sleep = 1;
                            else
                                sleep = 0;
                        }
                    }

                    EntityManagerMonitor newMonitor = null;
                    try {
                        if (sleep == 0) {
                            newMonitor = monitorQueue.take();
                        } else {
                            // What if an EntityManager closed during the sleep?
                            newMonitor = monitorQueue.poll(sleep, TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    if (newMonitor != null) {
                        entityManagerMonitors.add(newMonitor);
                    }
                } catch (Throwable ex) {
                    logger.error("Unexpected exception in EntityManagerProvider during monitoring", ex);
                }
            }
        });
    }

    @Override
    public final void afterOpen(Resources resources) {
        afterOpen(resources, getProviderName());
    }

    protected void afterOpen(Resources resources, String alias) {
    }

    @Override
    public final void onCommit(Resources resources) {
        onCommit(resources, getProviderName());
    }

    protected void onCommit(Resources resources, String alias) {
        try {
            EntityManager em = resources.getMapVariable(EntityManager.class).get(alias);
            em.getTransaction().commit();
            em.clear();
            em.close();
        } catch (InstanceNotFoundException ex) {
            Logger.suppress(ex);
        } finally {
            cleanup(resources, alias);
        }
    }

    @Override
    public final void afterCommit(Resources resources) {
        afterCommit(resources, getProviderName());
    }

    protected void afterCommit(Resources resources, String alias) {
    }

    @Override
    public final void onAbort(Resources resources) {
        onAbort(resources, getProviderName());
    }

    protected void onAbort(Resources resources, String alias) {
        try {
            EntityManager em = resources.getMapVariable(EntityManager.class).get(alias);
            em.getTransaction().rollback();
            em.clear();
            em.close();
        } catch (Exception th) {
            Logger.suppress(th);
        }  finally {
            cleanup(resources, alias);
        }
    }

    protected void cleanup(Resources resources, String alias) {
    }

    @Override
    public final void afterAbort(Resources resources) {
        afterAbort(resources, getProviderName());
    }

    protected void afterAbort(Resources resources, String alias) {
    }

    @Override
    public final void onClosed(Resources resources) {
        onClosed(resources, getProviderName());
    }

    protected void onClosed(Resources resources, String alias) {
        EntityManagerMonitor m = resources.getMapVariable(EntityManagerMonitor.class).get(alias);
        if (m != null)
            m.close();
    }

    @Override
    public final void onShutdown() {
        onShutdown(getProviderName());
    }

    @SuppressWarnings("squid:S1172")
    protected void onShutdown(String alias) {
        if (emf.isOpen()) {
            emf.close();
        }
        shutdown = true;
    }

    public void cancelQuery(Resources resources, String alias) {
    }

    @Override
    public final String getDescription() {
        return getDescription(getProviderName());
    }

    @SuppressWarnings("squid:S1172")
    protected String getDescription(String alia) { return getClass().getName(); }
}
