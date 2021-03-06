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

import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.UnitOfWork;
import net.e6tech.elements.persist.EntityManagerBuilder;
import net.e6tech.elements.persist.EntityManagerConfig;
import net.e6tech.elements.persist.EntityManagerInfo;
import net.e6tech.elements.persist.EntityManagerMonitor;
import net.e6tech.elements.persist.criteria.Select;
import net.e6tech.sample.BaseCase;
import net.e6tech.sample.Tags;
import org.hibernate.internal.SessionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.LockTimeoutException;
import javax.persistence.NoResultException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void selectForUpdate() {

        provision.open().accept(EntityManager.class, Resources.class,  (em, res) -> {
            SessionImpl session = em.unwrap(SessionImpl.class);
            session.setJdbcBatchSize(20);
            em.persist(employee);
            em.persist(employee2);
            em.flush();
        });

        UnitOfWork uow1 = new UnitOfWork(provision.getResourceManager());
        uow1.annotate(EntityManagerConfig.class, EntityManagerConfig::names, (String[]) null);
        UnitOfWork uow2 = new UnitOfWork(provision.getResourceManager());
        uow2.annotate(EntityManagerConfig.class, EntityManagerConfig::names, (String[]) null)
                .annotate(EntityManagerConfig.class, EntityManagerConfig::timeout, 10000L);
        Map<String,Object> timeoutProperties = new HashMap<>();
        timeoutProperties.put("javax.persistence.lock.timeout", 1000);

        Resources r1 = uow1.open();
        Resources r2 = uow2.open();

        EntityManager em = r1.getInstance(EntityManager.class);

        Employee e = em.find(Employee.class, employee.getId(), LockModeType.PESSIMISTIC_WRITE, timeoutProperties);

        try {
            EntityManager em2 = r2.getInstance(EntityManager.class);
            //em2.createNativeQuery("SET SESSION innodb_lock_wait_timeout = 0")
            //        .executeUpdate();
            EntityManagerMonitor monitor = r2.getMapVariable(EntityManagerMonitor.class).get("default");
            monitor.expire(500L);
            em2.createQuery("select p from Employee p where p.id = :id ")
                    .setHint("javax.persistence.lock.timeout", 0)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setParameter("id", employee.getId())
                    .getSingleResult();
            // Employee e2 = em2.find(Employee.class, employee.getId(), LockModeType.PESSIMISTIC_WRITE, timeoutProperties);
        } catch (LockTimeoutException ex){
            ex.printStackTrace();
        }

        r1.abort();
        r2.abort();

    }

    @Test
    void otherPersistence() {
        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, new String[] {"default", "sample-rw"})
                .accept(EntityManager.class, Resources.class,  (em, res) -> {
                    assertNotNull(res.getMapVariable(EntityManager.class).get("sample-rw"));
                    EntityManagerInfo info = (EntityManagerInfo) res.getMapVariable(EntityManager.class).get("sample-rw");
                    assertNotNull(info.getAlias());
                    assertNotNull(info.getResources());
                    assertNotNull(info.getProvider());
                    assertNotNull(info.getConfig());
                    EntityManager emDefault = res.getMapVariable(EntityManager.class).get("default");
                    assertTrue(em == emDefault);
                });

        provision
                .open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names,  new String[] {"sample-rw"})
                .accept(EntityManager.class, Resources.class,  (em, res) -> {
                    EntityManager emDefault = res.getMapVariable(EntityManager.class).get("sample-rw");
                    assertTrue(em == emDefault);
                });

        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, (String[]) null)
                .accept(EntityManager.class, Resources.class,  (em, res) -> {
                    EntityManager emOther = res.getMapVariable(EntityManager.class).get("default");
                    assertTrue(em == emOther);
                });

        provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names,  new String[] {"delegate"})
                .accept(EntityManager.class, Resources.class,  (em, res) -> {
                    EntityManager emOther = res.getMapVariable(EntityManager.class).get("delegate");
                    assertTrue(em == emOther);
                });

    }


    @Test
    void insert() {
        provision.open().accept(EntityManager.class, Resources.class,  (em, res) -> {
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
        provision.open().accept(EntityManager.class, Resources.class,  (em, res) -> {
            employee.setAdditionalInfo(null);
            em.persist(employee);
        });

        provision.open().accept(EntityManager.class, Resources.class,  (em, res) -> {
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
            EntityManagerInfo info = (EntityManagerInfo) em;
            assertNotNull(info.getAlias());
            assertNotNull(info.getResources());
            assertNotNull(info.getProvider());
            assertNotNull(info.getConfig());
        });
    }
}
