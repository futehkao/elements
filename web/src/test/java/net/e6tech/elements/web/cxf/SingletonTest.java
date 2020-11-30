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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SingletonTest {

    private static void setupServer(int port) {
        JaxRSLauncher.create(new ResourceManager(), "http://0.0.0.0:" + port + "/restful/")
                .headerObserver(new MyObserver())
                .add(new JaxResource(HelloWorldRS.class).singleton())
                .start();
    }

    @SuppressWarnings("squid:S2925")
    @Test
    void basic() {
        setupServer(9000);
        RestfulProxy proxy = new RestfulProxy("http://localhost:9000/restful");
        HelloWorldRS hello = proxy.newProxy(HelloWorldRS.class);
        wait(() -> {
            proxy.setRequestProperty("HELLO", "1");
            hello.sayHi("hi");
        });

        proxy.setRequestProperty("HELLO", "2");
        hello.sayHi("hi");
    }

    @Test
    void viaAnnotation() {
        SingletonRS singletonRS = new SingletonRS();
        JaxRSLauncher launcher = JaxRSLauncher.create(new ResourceManager(), "http://0.0.0.0:" + 9001 + "/restful/")
                .perInstanceService(singletonRS) // should be converted to singleton because its @Singleton annotation
                .start();

        RestfulProxy proxy = new RestfulProxy("http://localhost:9001/restful");
        SingletonRS singleton = proxy.newProxy(SingletonRS.class);
        AtomicInteger id1 = new AtomicInteger(0);
        wait(() -> {
            id1.set(singleton.identity());
        });
        assertEquals(id1.get(), singletonRS.identity());
        launcher.stop();
    }

    private void wait(Runnable runnable) {
        while (true) {
            try {
                Thread.sleep(100L);
                runnable.run();
                break;
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    private static class MyObserver extends Observer {
        public void beforeInvocation(HttpServletRequest request, HttpServletResponse response, Object instance, Method method, Object[] args) {
            System.out.println(request.getHeader("HELLO"));
        }
    }
}
