/*
 * Copyright 2015-2020 Futeh Kao
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

package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.network.restful.RestfulProxy;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class SingletonTest {

    private static void setupServer(int port) {
        JaxRSLauncher.create(new ResourceManager(), "http://0.0.0.0:" + port + "/restful/")
                .setHeaderObserver(new MyObserver())
                .add(new JaxResource(HelloWorldRS.class).singleton())
                .start();
    }

    @Test
    void basic() {
        setupServer(9000);
        RestfulProxy proxy = new RestfulProxy("http://localhost:9000/restful");
        HelloWorldRS hello = proxy.newProxy(HelloWorldRS.class);
        while (true) {
            try {
                Thread.sleep(100L);
                proxy.setRequestProperty("HELLO", "1");
                hello.sayHi("hi");
                break;
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }

        proxy.setRequestProperty("HELLO", "2");
        hello.sayHi("hi");
    }

    private static class MyObserver extends Observer {
        public void beforeInvocation(HttpServletRequest request, HttpServletResponse response, Object instance, Method method, Object[] args) {
            System.out.println(request.getHeader("HELLO"));
        }
    }
}
