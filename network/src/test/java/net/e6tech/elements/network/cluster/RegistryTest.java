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

package net.e6tech.elements.network.cluster;

import akka.actor.ActorSystem;
import com.sun.org.apache.regexp.internal.RE;
import com.sun.tools.internal.ws.processor.model.Response;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class RegistryTest {

    Registry create(int port) {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        ActorSystem system = ActorSystem.create("ClusterSystem", config);
        Registry registry = new Registry();
        registry.start(system);
        return registry;
    }

    @Test
    public void simple1() throws Exception {
        Registry registry = create(2552);

        registry.register("blah", ServiceMessage.class, String.class, (sv) -> {
            return sv.message.toUpperCase();
        });
        Thread.sleep(100L);

        // routing ServiceMessage
        ServiceMessage msg = new ServiceMessage();
        msg.message = "Hello world!";
        long start = System.currentTimeMillis();
        registry.route("blah", ServiceMessage.class, String.class, 5000L)
                .apply(msg)
                .thenAccept(result -> {
                    System.out.println(result);
                    System.out.println("duration: " + (System.currentTimeMillis() - start));
                })
                .exceptionally(error -> {
                    System.out.println(error);
                    return null;
                });

        long start2 = System.currentTimeMillis();
        registry.route("blah", ServiceMessage.class, String.class, 5000L)
                .apply(msg)
                .thenAccept(result -> {
                    System.out.println(result);
                    System.out.println("duration: " + (System.currentTimeMillis() - start2));
                });

        Thread.sleep(2000L);
    }

    static class ServiceMessage implements Serializable {
        String message;
    }

    @Test
    public void async() throws Exception {
        Registry registry = create(2552);

        registry.register("blah", X.class, new X() {
            @Override
            public int doSomething(int x) {
                return 0;
            }

            @Override
            public void returnsVoid(int x) {
            }

            @Override
            public Response request(Request request) {
                Response response = new Response();
                response.map = request.map;
                return response;
            }
        });
        Thread.sleep(100L);

        Async<X> async = registry.async("blah", X.class, 5000L);
        async.apply(p -> p::doSomething, 5)
                .thenAccept(result -> {
                    System.out.println(result);
                });

        async.accept(p -> p::returnsVoid, 5)
                .thenAccept(result -> {
                    System.out.println(result);
                });

        Thread.sleep(2000L);
    }

    @Test
    public void asyncVM1() throws Exception {
        Registry registry = create(2551);

        registry.register("blah", X.class, new X() {
            @Override
            public int doSomething(int x) {
                return x * x;
            }

            @Override
            public void returnsVoid(int x) {
            }

            @Override
            public Response request(Request request) {
                Response response = new Response();
                response.map = request.map;
                return response;
            }
        });
        synchronized (this) {
            wait();
        }
    }

    @Test
    public void asyncVM2() throws Exception {
        Registry registry = create(2552);
        long start = System.currentTimeMillis();
        AtomicInteger member = new AtomicInteger(0);

        registry.addRouteListener(new RouteListener() {
            @Override
            public void onAnnouncement(String path) {
            synchronized (member) {
                member.incrementAndGet();
                member.notifyAll();
            }
            }
        });

        synchronized (member) {
            while (member.get() == 0) {
                member.wait();
            }
        }
        System.out.println("Detected announcement in " + (System.currentTimeMillis() - start) + "ms");

        Async<X> async = registry.async("blah", X.class, 5000L);
        async.apply(p -> p::doSomething, 5)
                .thenAccept(result -> {
                    System.out.println(result);
                });

        Request request = new Request();
        request.map.put("key", "value");
        async.apply(p -> p::request, request)
                .thenAccept(result -> {
                    System.out.println(result);
                });

        async.accept(p -> p::returnsVoid, 5)
                .thenAccept(result -> {
                    System.out.println(result);
                });
        Thread.sleep(2000L);
    }

    interface X {
        int doSomething(int x);
        void returnsVoid(int x);
        Response request(Request request);
    }

    public static class Request {
        Map map = new HashMap();
    }

    public static class Response {
        Map map = new HashMap();
    }

}
