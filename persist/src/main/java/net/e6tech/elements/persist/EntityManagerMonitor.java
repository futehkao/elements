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

import javax.persistence.EntityManager;

public class EntityManagerMonitor {
    private static final Logger logger = Logger.getLogger();

    private EntityManager entityManager;
    private long expiration;
    private Throwable throwable;

    EntityManagerMonitor(EntityManager entityManager, long expiration, Throwable throwable) {
        this.entityManager = entityManager;
        this.expiration = expiration;
        this.throwable = throwable;
    }

    public long getExpiration() {
        return expiration;
    }

    public void addExpiration(long extension) {
        expiration += extension;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    void rollback() {
        if (entityManager.isOpen()) {
            entityManager.getTransaction().setRollbackOnly();
            entityManager.close();
            logger.warn("EntityManagerProvider timeout", throwable);
        }
    }
}
