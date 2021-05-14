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

package net.e6tech.sample.web.cxf;

import net.e6tech.elements.common.resources.Atom;
import net.e6tech.elements.common.util.concurrent.ObjectPool;
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.web.cxf.SecurityAnnotationEngine;
import net.e6tech.sample.BaseCase;
import net.e6tech.sample.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by futeh.
 */
@Tags.Sample
class HelloWorldTest extends BaseCase {
    HelloWorld helloWorld;
    RestfulProxy proxy;

    @BeforeEach
    void setup() {
        proxy = new RestfulProxy("http://localhost:19001/restful");
        proxy.setSkipCertCheck(true);
        proxy.enableMeasurement(true);
        proxy.getGauge().setPeriod(200L);
        proxy.getGauge().setWindowWidth(200L);
        // proxy.setPrinter(new PrintWriter(System.out, true));
        helloWorld = proxy.newProxy(HelloWorld.class);
    }

    @Test
    void sayHello() throws Exception {
        String response = helloWorld.sayHello("hello");
        System.out.println(response);
        Thread th = new Thread(() -> {
            while (true) {
                helloWorld.sayHello("hello");
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        th.start();
        Thread.sleep(500L); // for printing out measurements.
        th.interrupt();
        System.out.println("Sleep again");
        Thread.sleep(500L); // for printing out measurements.
        helloWorld.sayHello("hello");
        Thread.sleep(500L);
    }

    @Test
    void echo() {
        String response = helloWorld.echo(null);
        assertTrue(response == null);

        response = helloWorld.echo("hello");
        assertEquals(response, "hello");
    }

    @Test
    void withParam() {
        helloWorld.withParam("1234", "WWWWWWWWWWWW");
    }

    @Test
    void withSecurity() throws Exception {
        Atom atom = provision.getResourceManager().getAtom("helloWorld");
        SecurityAnnotationEngine engine = (SecurityAnnotationEngine) atom.get("_securityAnnotation");
        assertTrue(engine.getSecurityProvider(HelloWorld.class).equals(HelloWorldRoles.class));
        Method method = HelloWorld.class.getDeclaredMethod("withSecurity", String.class);
        Set<String> roles = engine.lookupRoles(HelloWorld.class, method);
        assertTrue(roles.contains("role1"));
        assertTrue(roles.contains("role2"));
        helloWorld.withSecurity("hello");

        method = HelloWorld.class.getDeclaredMethod("sayHello", String.class);
        roles = engine.lookupRoles(HelloWorld.class, method);
        assertTrue(roles.contains("PermitAll"));
    }

    // even though the method is annotated with DenyAll, a user with PermitAll can still access
    @Test
    void readOnly() throws Exception {
        Atom atom = provision.getResourceManager().getAtom("helloWorld");
        SecurityAnnotationEngine engine = (SecurityAnnotationEngine) atom.get("_securityAnnotation");
        Method method = HelloWorld.class.getDeclaredMethod("post", HelloData.class);
        assertTrue(!engine.hasAccess(new HelloWorld(), method, new Object[] { new HelloData()}, "ReadOnly"));
    }

    @Test
    void permitAll() throws Exception {
        Atom atom = provision.getResourceManager().getAtom("helloWorld");
        SecurityAnnotationEngine engine = (SecurityAnnotationEngine) atom.get("_securityAnnotation");
        Method method = HelloWorld.class.getDeclaredMethod("withSecurity", String.class);
        assertTrue(engine.hasAccess(new HelloWorld(), method, new Object[] { "test" }, "PermitAll"));
    }

    @Test
    void post() {
        HelloData data = new HelloData();
        data.setData("hello");
        data = helloWorld.post(data);
        assertTrue(data.getData().equals("hello"));
    }

    @Test
    void badPost() {
        provision.suppressLogging(() -> {
            assertThrows(BadRequestException.class, () -> helloWorld.post(null));
            assertThrows(BadRequestException.class, () -> helloWorld.badPost(new HelloData()));
        });
    }

    @Test
    void delete() {
        HelloData data = new HelloData();
        data.setData("hello");
        helloWorld.delete("does not matter", data);
        helloWorld.delete("does not matter", null);
        helloWorld.delete2("null data");
    }

    @Test
    void list() {
        List<HelloData> list = helloWorld.list();
    }

    @Test
    void map() {
        Map<String, HelloData> map = helloWorld.map();
    }

    @SuppressWarnings("squid:S2925")
    @Test
    void response() throws InterruptedException {
        //helloWorld.echo("");

        ObjectPool<?> pool = net.e6tech.elements.network.restful.Response.objectPool;
        pool.idleTimeout(4000);

        Runnable runnable = () -> {
            try {
                Response res = helloWorld.response();
                List<HelloData> list = res.readEntity(new GenericType<List<HelloData>>() {
                });
                assertTrue(list.get(0) instanceof HelloData);
            } catch (Exception ex ) {
                ex.printStackTrace();
            }
        };
        Thread[] threads = new Thread[100];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(runnable);
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }

        System.out.println("Before pool cleanup " + pool.size());

        Thread.sleep(4000);

        pool.cleanup();

        System.out.println("After pool cleanup " + pool.size());

        assertTrue(pool.size() <= pool.getLimit());
    }
}
