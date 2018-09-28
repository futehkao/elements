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
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.web.cxf.SecurityAnnotationEngine;
import net.e6tech.sample.BaseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class HellowWorldTest extends BaseCase {
    HelloWorld helloWorld;
    RestfulProxy proxy;

    @BeforeEach
    public void setup() {
        proxy = new RestfulProxy("http://localhost:19001/restful");
        proxy.setSkipCertCheck(true);
        // proxy.setPrinter(new PrintWriter(System.out, true));
        helloWorld = proxy.newProxy(HelloWorld.class);
    }

    @Test
    public void sayHello() {
        helloWorld.ping();
        String response = helloWorld.sayHello("hello");
        response = helloWorld.sayHello2("hello2", "blah");
    }

    @Test
    public void withParam() {
        helloWorld.withParam("1234", "WWWWWWWWWWWW");
    }

    @Test
    public void withSecurity() throws Exception {
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
    public void readOnlly() throws Exception {
        Atom atom = provision.getResourceManager().getAtom("helloWorld");
        SecurityAnnotationEngine engine = (SecurityAnnotationEngine) atom.get("_securityAnnotation");
        Method method = HelloWorld.class.getDeclaredMethod("post", HelloData.class);
        assertTrue(!engine.hasAccess(new HelloWorld(), method, new Object[] { new HelloData()}, "ReadOnly"));
    }

    @Test
    public void permitAll() throws Exception {
        Atom atom = provision.getResourceManager().getAtom("helloWorld");
        SecurityAnnotationEngine engine = (SecurityAnnotationEngine) atom.get("_securityAnnotation");
        Method method = HelloWorld.class.getDeclaredMethod("withSecurity", String.class);
        assertTrue(engine.hasAccess(new HelloWorld(), method, new Object[] { "test" }, "PermitAll"));
    }

    @Test
    public void post() {
        HelloData data = new HelloData();
        data.setData("hello");
        data = helloWorld.post(data);
        assertTrue(data.getData().equals("hello"));

        assertThrows(BadRequestException.class, () -> helloWorld.post(null));

        assertThrows(BadRequestException.class, () -> helloWorld.badPost(new HelloData()));
    }
}
