/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.web.federation.invocation;

import net.e6tech.elements.common.federation.Member;
import net.e6tech.elements.common.logging.ConsoleLogger;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.web.federation.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RegistryTest {
    private static final int SERVERS = 100;
    private static final List<FederationImpl> federations = Collections.synchronizedList(new ArrayList<>(SERVERS));

    @BeforeAll
    public static void setup() {
        // Beacon.setLogger(Logger.from(new ConsoleLogger().traceEnabled().debugEnabled()));
        new Thread(()->{
            for (int i = 0; i < SERVERS; i++) {
                try {
                    setupServer(3909 + i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void setupServer(int port) {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);
        rm.setupThreadPool();

        FederationImpl federation = rm.newInstance(FederationImpl.class);
        federation.setHostAddress("http://127.0.0.1:" + port + "/restful");
        federation.setHosts(new Host[] { new Host("" + port)});
        federation.setSeeds(new String[]{ "http://127.0.0.1:3909/restful"});
        InvokerRegistryImpl registry = new InvokerRegistryImpl();
        registry.setCollective(federation);
        registry.initialize(null);
        registry.register("x", X.class, new XImpl(federation), null);
        federation.start();
        federations.add(federation);
    }

    @AfterAll
    public static void tearDown() {
        try {
            federations.forEach(FederationImpl::shutdown);
        } finally {
            federations.clear();
        }
    }

    @SuppressWarnings("java:S2925")  // this warning is absolute BS.
    @Test
    void basic() throws ExecutionException, InterruptedException {
        BeaconAPI[] apis = new BeaconAPI[SERVERS];
        for (int i = 0; i < SERVERS; i ++) {
            int port = 3909 + i;
            RestfulProxy proxy = new RestfulProxy("http://localhost:" + port + "/restful");
            apis[i] = proxy.newProxy(BeaconAPI.class);
        }

        while (true) {
            int total = 0;
            Collection<Member> members;
            for (int i = 0; i < SERVERS; i++) {
                try {
                    members = apis[i].members();
                } catch (Exception ex) {
                    break;
                }

                total += members.size();
            }

            System.out.println("total " + total);
            if (total == SERVERS * SERVERS) {
                break;
            }

            Thread.sleep(50L);
        }

        for (int i = 0; i < SERVERS; i ++) {
            int port = 3909 + i;
            RestfulProxy proxy = new RestfulProxy("http://localhost:" + port + "/restful");
            InvokerRegistryAPI api = proxy.newProxy(InvokerRegistryAPI.class);
            Set<String> routes = api.routes();
            routes.forEach(System.out::println);
        }

        for (CollectiveImpl fed : federations) {
            InvokerRegistry registry = fed.getServiceProvider(InvokerRegistry.class);
            assertNotNull(registry);
            Async<X> async = registry.async("x", X.class);
            CompletionStage<String> stage = async.apply(proxy -> proxy.sayHello("hello"));
            System.out.println(fed.getHostAddress() + " asking " + stage.toCompletableFuture().get());
        }
    }

    public interface X {
        String sayHello(String text) ;
    }

    public static class XImpl implements X {

        private CollectiveImpl federation;

        public XImpl(CollectiveImpl federation) {
            this.federation = federation;
        }

        public String sayHello(String text) {
            System.out.println(federation.getHostAddress() + " says " + text);
            return federation.getHostAddress();
        }
    }

}
