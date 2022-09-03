/*
 * Copyright 2016 Futeh Kao
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

import com.zaxxer.hikari.HikariDataSource;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.UnitOfWork;
import net.e6tech.elements.persist.EntityManagerConfig;
import net.e6tech.elements.persist.EntityManagerExtension;
import net.e6tech.elements.persist.criteria.Select;
import net.e6tech.elements.persist.datasource.hikari.ElementsHikariDataSource;
import net.e6tech.elements.persist.mariadb.ConnectionListener;
import net.e6tech.sample.BaseCase;
import net.e6tech.sample.Tags;
import org.hibernate.internal.SessionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by futeh.
 */
@Tags.Sample
public class PersistenceTest extends BaseCase {

    private Employee employee;
    private Employee employee2;
    private Department department;

    @BeforeEach
    void setup() {
        employee = new Employee();
        employee.setFirstName("First" + System.currentTimeMillis());
        employee.setLastName("Last" + System.currentTimeMillis());
        employee.setGender('M');
        employee.setBirthDate("19701101");
        employee.setHireDate("20160101");
        employee.setAdditionalInfo("info-" + System.currentTimeMillis());

        employee2 = new Employee();
        employee2.setFirstName("First" + System.currentTimeMillis());
        employee2.setLastName("Last" + System.currentTimeMillis());
        employee2.setGender('M');
        employee2.setBirthDate("19701101");
        employee2.setHireDate("20160101");
        employee2.setAdditionalInfo("info-" + System.currentTimeMillis());

        department = new Department();
        department.setName("Test");
    }

    @Test
    void resetPool() throws SQLException {
        ElementsHikariDataSource ds = provision.getBean("dataSource");

        long lastReset = ds.getLastReset();
        String driverClassName = ds.getDriverClassName();
        Enumeration<java.sql.Driver> drivers = DriverManager.getDrivers();
        net.e6tech.elements.persist.mariadb.Driver driver = null;
        while (drivers.hasMoreElements()) {
            java.sql.Driver d = drivers.nextElement();
            if (d.getClass().getName().equals(driverClassName)) {
                driver = (net.e6tech.elements.persist.mariadb.Driver) d;
                break;
            }
        }

        int max = ds.getMaximumPoolSize();
        evictConnections(ds);

        AtomicBoolean bool = new AtomicBoolean(false);
        ConnectionListener listener = () -> {
            if (bool.get())
                return;
            bool.set(true);
            try {
                Thread.sleep(ds.getConnectionTimeout() + 500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        List<Connection> connections = new ArrayList<>(max);
        for (int i = 0; i < max / 2; i++) {
            connections.add(ds.getConnection());
        }

        // now introduce a delay when getting a connection in the loop below
        driver.addListener(listener);
        for (int i = max / 2; i < max; i++) {
            connections.add(ds.getConnection());
        }
        driver.removeListener(listener);

        // verify we get all the connections successfully
        assertEquals(connections.size(), max);

        // clean up
        for (Connection connection : connections) {
            connection.close();
        }
        connections.clear();

        // verify the pool was reset.
        assertTrue(ds.getLastReset() > lastReset);

        listener = () -> {
            try {
                Thread.sleep(ds.getConnectionTimeout() + 500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        driver.addListener(listener);
        try {
            assertThrows(SQLException.class, () -> {
                evictConnections(ds);
                // now introduce a delay when getting a connection in the loop below
                for (int i = 0; i < max; i++) {
                    connections.add(ds.getConnection());
                }
            });
        } finally {
            driver.removeListener(listener);
        }
    }

    private void evictConnections(ElementsHikariDataSource ds) throws SQLException {
        int max = ds.getMaximumPoolSize();
        List<Connection> connections = new ArrayList<>(max);
        // clear out all connections in the pool
        for (int i = 0; i < max; i++) {
            connections.add(ds.getConnection());
        }
        connections.forEach(conn -> ds.evictConnection(conn));
        connections.clear();
    }

    @Test
    void selectForUpdate() {

        provision.open().accept(EntityManager.class, Resources.class, (em, res) -> {
            SessionImpl session = em.unwrap(SessionImpl.class);
            session.setJdbcBatchSize(20);
            em.persist(employee);
            em.persist(employee2);
            em.flush();
        });

        UnitOfWork uow1 = new UnitOfWork(provision.getResourceManager());
        uow1.annotate(EntityManagerConfig.class, EntityManagerConfig::names, (String[]) null)
                .annotate(EntityManagerConfig.class, EntityManagerConfig::timeout, 100000L);
        UnitOfWork uow2 = new UnitOfWork(provision.getResourceManager());
        uow2.annotate(EntityManagerConfig.class, EntityManagerConfig::names, (String[]) null)
                .annotate(EntityManagerConfig.class, EntityManagerConfig::timeout, 100000L);

        Resources r1 = uow1.open();
        Resources r2 = uow2.open();

        EntityManagerExtension em1 = r1.getInstance(EntityManagerExtension.class);
        em1.lockTimeout(2000L);
        Employee e = em1.find(Employee.class, employee.getId(), LockModeType.PESSIMISTIC_WRITE);

        long start = 0;
        EntityManagerExtension em2 = r2.getInstance(EntityManagerExtension.class);
        r2.getInstance(EntityManagerExtension.class).lockTimeout(0);
        Query query = em2.createQuery("select p.id from Employee p where p.id = :id")
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setParameter("id", employee.getId());
        try {
            start = System.currentTimeMillis();
            query.getSingleResult();
            // Employee e3 = em2.find(Employee.class, employee.getId(), LockModeType.PESSIMISTIC_WRITE);
        } catch (Throwable ex) {
            System.out.println("lock timeout duration=" + (System.currentTimeMillis() - start));
        }

        try {
            start = System.currentTimeMillis();
            query.getSingleResult();
        } catch (Throwable ex) {
            System.out.println("lock timeout duration=" + (System.currentTimeMillis() - start));
        }

        r1.abort();
        r2.abort();

        for (int i = 0; i < 10; i++) {
            UnitOfWork uow = new UnitOfWork(provision.getResourceManager());
            Resources r = uow.open();
            EntityManagerExtension em = r.getInstance(EntityManagerExtension.class);
            Number num = (Number) em.createNativeQuery("select @@innodb_lock_wait_timeout").getSingleResult();
            System.out.println("innodb_lock_wait_timeout=" + num);
        }

    }

    @Test
    void otherPersistence() {
        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, new String[]{"default", "sample-rw"})
                .accept(EntityManager.class, Resources.class, (em, res) -> {
                    assertNotNull(res.getMapVariable(EntityManager.class).get("sample-rw"));
                    EntityManagerExtension info = (EntityManagerExtension) res.getMapVariable(EntityManager.class).get("sample-rw");
                    assertNotNull(info.getAlias());
                    assertNotNull(info.getResources());
                    assertNotNull(info.getProvider());
                    assertNotNull(info.getConfig());
                    EntityManager emDefault = res.getMapVariable(EntityManager.class).get("default");
                    assertTrue(em == emDefault);
                });

        provision
                .open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, new String[]{"sample-rw"})
                .accept(EntityManager.class, Resources.class, (em, res) -> {
                    EntityManager emDefault = res.getMapVariable(EntityManager.class).get("sample-rw");
                    assertTrue(em == emDefault);
                });

        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, (String[]) null)
                .accept(EntityManager.class, Resources.class, (em, res) -> {
                    EntityManager emOther = res.getMapVariable(EntityManager.class).get("default");
                    assertTrue(em == emOther);
                });

        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, new String[]{"delegate"})
                .accept(EntityManager.class, Resources.class, (em, res) -> {
                    EntityManager emOther = res.getMapVariable(EntityManager.class).get("delegate");
                    assertTrue(em == emOther);
                });

    }


    @Test
    void insert() {
        provision.open().accept(EntityManager.class, Resources.class, (em, res) -> {
            SessionImpl session = em.unwrap(SessionImpl.class);
            session.setJdbcBatchSize(20);
            em.persist(employee);
            em.persist(employee2);
            em.flush();
        });

        provision.open().accept(EntityManager.class, (em) -> {
            Employee e = em.find(Employee.class, employee.getId());
            assertTrue(e != null);
        });

        employee.setId(null);
        provision.open().accept(EntityManager.class, (em) -> {
            em.persist(employee);
        });

        provision.open().accept(EntityManager.class, (em) -> {
            Employee e = em.find(Employee.class, employee.getId());
            assertTrue(e != null);
            e.setHireDate("20170401");
        });
    }

    @Test
    void testInsertDepartment() {

        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::timeoutExtension, 100000L)
                .accept(EntityManager.class, (em) -> {
                    try {
                        Department d = Select.create(em, Department.class)
                                .where(new Department() {{
                                    setName(department.getName());
                                }})
                                .getSingleResult();
                        department = d;
                    } catch (NoResultException ex) {
                        em.persist(department);
                    }
                });

        int size = provision.open().apply(EntityManager.class, (em) -> {
            em.persist(employee);
            Department d = em.find(Department.class, department.getId());
            d.getEmployees().add(employee);
            return d.getEmployees().size();
        });

        provision.open().accept(EntityManager.class, (em) -> {
            Department d = em.find(Department.class, department.getId());
            assertTrue(d.getEmployees().size() == size);
        });
    }

    @Test
    void isNull() {
        provision.open().accept(EntityManager.class, Resources.class, (em, res) -> {
            employee.setAdditionalInfo(null);
            em.persist(employee);
        });

        provision.open().accept(EntityManager.class, Resources.class, (em, res) -> {
            List<Employee> list = Select.create(em, Employee.class)
                    .where(new Employee() {{
                        setAdditionalInfo(null);
                    }}).getResultList();
            assertTrue(list.size() > 0);
        });
    }

    @Test
    void entityManagerInfo() {
        provision.open().accept(EntityManager.class, em -> {
            EntityManagerExtension info = (EntityManagerExtension) em;
            assertNotNull(info.getAlias());
            assertNotNull(info.getResources());
            assertNotNull(info.getProvider());
            assertNotNull(info.getConfig());
        });
    }
}
