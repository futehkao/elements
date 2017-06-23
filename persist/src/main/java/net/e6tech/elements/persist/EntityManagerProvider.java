/*
Copyright 2015 Futeh Kao

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

import com.google.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.subscribe.Broadcast;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.metamodel.Metamodel;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by futeh.
 */
public abstract class EntityManagerProvider implements ResourceProvider, Initializable {

    private static final String MONITOR_TRANSACTION = EntityManagerProvider.class.getName() + ".transaction.monitor";
    private static final String LONG_TRANSACTION = EntityManagerProvider.class.getName() + ".transaction.longTransaction";

    private static final Logger logger = Logger.getLogger();

    @Inject(optional = true)
    private ExecutorService threadPool;

    @Inject(optional = true)
    private NotificationCenter center;

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
    private List<EntityManagerMonitor> entityManagerMonitors = new ArrayList<>();
    private long monitorIdle = 60000;
    private boolean monitoring = false;

    public long getMonitorIdle() {
        return monitorIdle;
    }

    public void setMonitorIdle(long monitorIdle) {
        this.monitorIdle = monitorIdle;
    }

    public EntityManagerProvider() {
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
        if (ignoreInitialLongTransactions == null) return 0;
        return ignoreInitialLongTransactions.get();
    }

    public void setIgnoreInitialLongTransactions(int n) {
        this.ignoreInitialLongTransactions = new AtomicInteger(n);
    }

    protected void evictCollectionRegion(EvictCollectionRegion notification) {
    }

    protected void evictEntityRegion(EvictEntityRegion region) {
    }

    protected void evictEntity(EvictEntity ref) {
    }

    public void initialize(Resources resources) {
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
            if (em != null) em.close();
        }

        NotificationCenter center = resources.getNotificationCenter();
        center.subscribe(EvictCollectionRegion.class, (notice) -> {
            evictCollectionRegion((EvictCollectionRegion) notice.getUserObject());
        });

        center.subscribe(EvictEntityRegion.class, (notice) -> {
            evictEntityRegion((EvictEntityRegion) notice.getUserObject());
        });

        center.subscribe(EvictEntity.class, (notice) -> {
            evictEntity(notice.getUserObject());
        });
    }

    @Override
    public void onOpen(Resources resources) {
        Optional<EntityManagerConfig> config = resources.configurator().annotation(EntityManagerConfig.class);
        if (config.isPresent() && config.get().disable()) throw new NotAvailableException();

        long timeout = config.map(c -> c.timeout()).orElse(transactionTimeout);
        if (timeout == 0L) timeout = transactionTimeout;
        long timeoutExt = config.map(c -> c.timeoutExtension()).orElse(0L);
        timeout += timeoutExt;

        boolean monitor = config.map(c -> c.monitor()).orElse(monitorTransaction);

        long longQuery = config.map(c -> c.longTransaction()).orElse(longTransaction);
        if (longQuery == 0L) longQuery = longTransaction;

        if (firstQuery) {
            firstQuery = false;
            if (longQuery < 1000L) longQuery = 1000L;
        }

        EntityManager em = emf.createEntityManager();
        if (monitor)
            monitor(new EntityManagerMonitor(em, System.currentTimeMillis() + timeout, new Throwable()));

        EntityManagerInvocationHandler emHandler = new EntityManagerInvocationHandler(resources, em);
        emHandler.setLongTransaction(longQuery);
        emHandler.setIgnoreInitialLongTransactions(ignoreInitialLongTransactions);
        resources.bind(EntityManager.class, (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{EntityManager.class}, emHandler));
        em.getTransaction().begin();
    }

    // Submits a thread task to monitor expired EntityManagers.
    // the thread would break out after monitorIdle time.
    // when another monitor shows up, the thread task would resume.
    private void monitor(EntityManagerMonitor monitor) {

        // entityManagerMonitors contains open, committed and aborted entityManagers.
        synchronized (monitorQueue) {
            monitorQueue.offer(monitor);
            if (monitoring) {
                return;
            } else {
                monitoring = true;
            }
        }

        // starting a thread to monitor
        if (threadPool == null) {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(group, runnable, "EntityManagerProvider");
                thread.setName("EntityManagerProvider-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }

        threadPool.execute(()->{
            try {
                while (true) {
                    synchronized (monitorQueue) {
                        monitoring = true;
                    }

                    long start = System.currentTimeMillis();
                    long expiration = 0;

                    synchronized (entityManagerMonitors) {
                        monitorQueue.drainTo(entityManagerMonitors);
                        Iterator<EntityManagerMonitor> iterator = entityManagerMonitors.iterator();
                        while (iterator.hasNext()) {
                            EntityManagerMonitor m = iterator.next();
                            if (!m.entityManager.isOpen()) { // already closed
                                iterator.remove();
                            } else if (m.expiration < System.currentTimeMillis()) {
                                m.rollback();  // rollback
                                iterator.remove();
                            } else {
                                // for find out the shortest sleep time
                                if (expiration == 0 || m.expiration < expiration) expiration = m.expiration;
                            }
                        }
                    }

                    long sleep = 0L;
                    if (expiration > 0) {
                        sleep = expiration - System.currentTimeMillis();
                        if (sleep < 0) {
                            // probably due to debugging
                            if (entityManagerMonitors.size() > 0) sleep = 1;
                            else sleep = 0;
                        }
                    }

                    EntityManagerMonitor newMonitor = null;
                    try {
                        if (sleep == 0) {
                            newMonitor = monitorQueue.poll(monitorIdle, TimeUnit.MILLISECONDS);
                        } else {
                            // What if an EntityManager closed during the sleep?
                            newMonitor = monitorQueue.poll(sleep, TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                    }

                    if (newMonitor != null) {
                        synchronized (entityManagerMonitors) {
                            entityManagerMonitors.add(newMonitor);
                        }
                    } else {
                        // Some thread may just add a monitor at this point so that
                        // we need to check the monitorQueue size before we break out.
                        // Also, we need to make sure entityManagerMonitors is empty as well.
                        synchronized (monitorQueue) {
                            if (monitorQueue.size() == 0
                                    && entityManagerMonitors.size() == 0
                                    && System.currentTimeMillis() - start > monitorIdle) {
                                monitoring = false;
                                break;
                            }
                        }
                    }
                }
            } finally {
                // in case of some exception, we make sure monitoring is set to false.
                synchronized (monitorQueue) {
                    monitoring = false;
                }
            }
        });
    }

    @Override
    public void onCommit(Resources resources) {
        try {
            EntityManager em = resources.getInstance(EntityManager.class);
            em.getTransaction().commit();
            em.close();
            // to break out the
            Optional<EntityManagerConfig> config = resources.configurator().annotation(EntityManagerConfig.class);
            boolean monitor = config.map(c -> c.monitor()).orElse(monitorTransaction);
            if (monitor) monitor(new EntityManagerMonitor(em, System.currentTimeMillis(), new Throwable()));
        } catch (InstanceNotFoundException ex) {

        }
    }

    @Override
    public void afterCommit(Resources resources) {
    }

    @Override
    public void onAbort(Resources resources) {
        try {
            EntityManager em = resources.getInstance(EntityManager.class);
            em.getTransaction().rollback();
            em.close();
            Optional<EntityManagerConfig> config = resources.configurator().annotation(EntityManagerConfig.class);
            boolean monitor = config.map(c -> c.monitor()).orElse(monitorTransaction);
            if (monitor) monitor(new EntityManagerMonitor(em, System.currentTimeMillis(), new Throwable()));
            monitor(new EntityManagerMonitor(em, System.currentTimeMillis(), new Throwable()));
        } catch (Throwable th) {

        }
    }

    public void onClosed(Resources resources) {

    }

    private static class EntityManagerMonitor {
        EntityManager entityManager;
        long expiration;
        Throwable throwable;

        EntityManagerMonitor(EntityManager entityManager, long expiration, Throwable throwable) {
            this.entityManager = entityManager;
            this.expiration = expiration;
            this.throwable = throwable;
        }

        boolean rollback() {
            if (entityManager.isOpen()) {
                entityManager.getTransaction().setRollbackOnly();
                entityManager.close();
                logger.warn("EntityManagerProvider timeout", throwable);
                return true;
            }
            return false;
        }
    }
}
