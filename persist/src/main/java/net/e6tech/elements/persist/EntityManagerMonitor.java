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

package net.e6tech.elements.persist;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Resources;

import javax.persistence.EntityManager;
import java.util.concurrent.ExecutorService;

public class EntityManagerMonitor {
    private static final Logger logger = Logger.getLogger();

    private EntityManagerProvider provider;
    private Resources resources;
    private EntityManager entityManager;
    private long expiration;
    private Throwable throwable;
    private ExecutorService threadPool;
    private Thread originatingThread;
    private volatile boolean interrupted = false;
    private String alias;

    EntityManagerMonitor(String alias, ExecutorService threadPool, EntityManagerProvider provider, Resources resources,
                         EntityManager entityManager, long expiration, Throwable throwable) {
        this.alias = alias;
        this.provider = provider;
        this.resources = resources;
        this.entityManager = entityManager;
        this.expiration = expiration;
        this.throwable = throwable;
        this.threadPool = threadPool;
        this.originatingThread = Thread.currentThread();
    }

    public long getExpiration() {
        return expiration;
    }

    public EntityManagerMonitor expire(long exp) {
        expiration = System.currentTimeMillis() + exp;
        return this;
    }

    public void addExpiration(long extension) {
        expiration += extension;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    // This method cannot throw an exception
    @SuppressWarnings("squid:S1181")
    void rollback() {
        threadPool.execute(() -> {
            // cancel query
            try {
                interrupt(); // interrupt here in case there is a blocking call during the transaction
                             // however, this interrupt needs to be clear
                if (entityManager.isOpen()) {
                    provider.cancelQuery(resources, alias);
                }
            } catch (Throwable ex) {
                logger.warn("Unexpected exception in EntityManagerMonitor cancel query", throwable);
            }

            // rollback
            try {
                if (entityManager.isOpen()) {
                    entityManager.getTransaction().setRollbackOnly();
                    entityManager.close();
                    logger.warn("EntityManagerProvider timeout", throwable);
                }
            } catch (Throwable ex) {
                logger.warn("Unexpected exception in EntityManagerMonitor during rollback", throwable);
            }
        });
    }

    private synchronized void interrupt() {
        if (!interrupted) {
            originatingThread.interrupt();
            interrupted = true;
        }
    }

    // Called by EntityManagerProvider to clear interrupt
    synchronized void close() {
        if (interrupted) {
            Thread.interrupted(); // clear current interrupt so that it won't propagate further.
        }
    }
}
