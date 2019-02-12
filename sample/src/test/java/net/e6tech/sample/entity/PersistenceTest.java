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

import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.persist.EntityManagerConfig;
import net.e6tech.elements.persist.criteria.Select;
import net.e6tech.sample.BaseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */

public class PersistenceTest extends BaseCase {

    private Employee employee;
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

        department = new Department();
        department.setName("Test");
    }

    @Test
    void testInsert() {
        provision.open().commit(EntityManager.class, Resources.class,  (em, res) -> {
            em.persist(employee);
        });

        provision.open().commit(EntityManager.class, (em) -> {
            Employee e = em.find(Employee.class, employee.getId());
            assertTrue(e != null);
        });

        employee.setId(null);
        provision.open().commit(EntityManager.class, (em) -> {
            em.persist(employee);
        });

        provision.open().commit(EntityManager.class, (em) -> {
            Employee e = em.find(Employee.class, employee.getId());
            assertTrue(e != null);
            e.setHireDate("20170401");
        });
    }

    @Test
    void testInsertDepartment() {

        provision.resourceBuilder(EntityManagerConfig.class)
                .annotate(c -> c::timeoutExtension, 100000L)
                .open().commit(EntityManager.class, (em) -> {
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

        int size = provision.open().commit(EntityManager.class, (em) -> {
            em.persist(employee);
            Department d = em.find(Department.class, department.getId());
            d.getEmployees().add(employee);
            return d.getEmployees().size();
        });

        provision.open().commit(EntityManager.class, (em) -> {
            Department d = em.find(Department.class, department.getId());
            assertTrue(d.getEmployees().size() == size);
        });
    }

    @Test
    void testIsNull() {
        provision.open().commit(EntityManager.class, Resources.class,  (em, res) -> {
            employee.setAdditionalInfo(null);
            em.persist(employee);
        });

        provision.open().commit(EntityManager.class, Resources.class,  (em, res) -> {
            List<Employee> list = Select.create(em, Employee.class)
                    .where(new Employee() {{
                        setAdditionalInfo(null);
                    }}).getResultList();
            assertTrue(list.size() > 0);
        });
    }
}
