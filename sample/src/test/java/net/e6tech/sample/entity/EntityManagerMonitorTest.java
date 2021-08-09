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

package net.e6tech.sample.entity;

import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.persist.EntityManagerConfig;
import net.e6tech.elements.persist.EntityManagerProvider;
import net.e6tech.sample.BaseCase;
import net.e6tech.sample.Tags;
import org.hibernate.internal.SessionImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbConnection;

import javax.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("all")
@Tags.Sample
public class EntityManagerMonitorTest extends BaseCase {
    private Employee employee;
    private Department department;

    @Test
    @Disabled
    void entityManagerMonitor() throws Exception {
        AtomicInteger count = new AtomicInteger();
        while (true) {
            Thread th = new Thread(() -> {
                try {
                    int n = count.incrementAndGet();
                    if (n % 4 == 0) {
                        provision
                                .open()
                                .accept(Resources.class, res -> {
                                    SessionImpl session = (SessionImpl) res.getInstance(EntityManager.class).getDelegate();
                                    MariaDbConnection conn = session.connection().unwrap(MariaDbConnection.class);
                                    conn.lock.lock();
                                    Thread.sleep(20000L);
                                    conn.lock.unlock();
                                });
                    } else {
                        provision.open().commit(() -> {
                            Thread.sleep(100L);
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            th.start();
            EntityManagerProvider provider = provision.getBean("entityManagerProvider");
            System.out.println("EntityManagerMonitor count=" + provider.getEntityManagerMonitors().size());
            Thread.sleep(200L);
        }
    }

    @Test
    @Disabled
    void timeout() throws Exception {
        provision.open().accept(Resources.class, res -> {
            try {
                Thread.sleep(30000L);
            } catch (InterruptedException ex) {

            }

            if (res.isAborted()) {

            }

        });
    }

    @Test
    void rollback() throws Exception {
        long start = System.currentTimeMillis();
        try {
            provision.open()
                    .annotate(EntityManagerConfig.class, EntityManagerConfig::timeoutExtension, 3000L)
                    .accept(Resources.class, EntityManager.class, (res, em) -> {
                        em.createNativeQuery("DO SLEEP(10);")
                                .getResultList();
                        System.out.println("" + (System.currentTimeMillis() - start) + "ms");
                        try {
                            Thread.sleep(10000L);
                        } catch (InterruptedException ex) {
                            //
                        }

                    });
        } catch(Exception ex) {
            System.out.println("" + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
