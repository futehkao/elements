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

package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.catalyst.Reactor;
import net.e6tech.elements.network.cluster.catalyst.SimpleCatalyst;
import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.scalar.Count;
import net.e6tech.elements.network.cluster.catalyst.scalar.Max;
import net.e6tech.elements.network.cluster.catalyst.transform.MapTransform;
import net.e6tech.elements.web.federation.invocation.Invoker;
import net.e6tech.elements.web.federation.invocation.InvokerRegistry;
import org.junit.jupiter.api.Test;
import scala.Int;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class GenesisTest {

    Genesis genesis;

    private void create(int port) {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);

        genesis = rm.newInstance(Genesis.class);
        genesis.getCluster().setHostAddress("http://127.0.0.1:" + port + "/restful");
        genesis.initialize(null);
    }

    @Test
    void basic() throws ExecutionException, InterruptedException {
        create(3903);
        InvokerRegistry registry = genesis.getCluster().getServiceProvider(InvokerRegistry.class);
        registry.register("x", X.class, new XImpl(genesis.getCluster()), null);
        Async<X> async = registry.async("x", X.class);
        CompletionStage<String> stage = async.apply(proxy -> proxy.sayHello("hello"));
        String response = stage.toCompletableFuture().get();
        genesis.shutdown();
    }

    @Test
    void catalyst() throws Exception {
        create(3903);
        InvokerRegistry registry = genesis.getCluster().getServiceProvider(InvokerRegistry.class);
        List<String> routes = registry.register("blah", Reactor.class, new Reactor() {
                        },
                        new Invoker() {
                            public Object invoke(Object target, Method method, Object[] arguments) {
                                System.out.println("Method " + target.getClass().getName() + "::" + method.getName());
                                return super.invoke(target, method, arguments);
                            }
                        });
        Collection collection = registry.routes("blah", Reactor.class);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            list.add(i);
        }
        SimpleCatalyst catalyst = new SimpleCatalyst("blah", registry);
        catalyst.setWaitTime(1000000L);
        CollectionDataSet<Integer> dataSet = new CollectionDataSet<>(list);

        long start = System.currentTimeMillis();

        Double max = catalyst.builder(dataSet)
                .add(new MapTransform<Reactor, Integer, Double>((operator, number) -> {
                    double sine = Math.sin(number * Math.PI / 360);
                    //System.out.println(sine);
                    return sine;
                }))
                .scalar(new Max<>());
        System.out.println("Max " + max + " found in " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        int count = catalyst.builder(dataSet)
                .add(new MapTransform<>((operator, number) -> Math.sin(number * Math.PI / 360)))
                .scalar(new Count<>());
        System.out.println("Count " + count + " found in " + (System.currentTimeMillis() - start) + "ms");
        genesis.shutdown();
    }

    @Test
    void catalyst2() throws Exception {
        create(3903);
        InvokerRegistry registry = genesis.getCluster().getServiceProvider(InvokerRegistry.class);
        List<String> routes = registry.register("blah", Reactor.class, new Reactor() {
                },
                new Invoker() {
                    public Object invoke(Object target, Method method, Object[] arguments) {
                        System.out.println("Method " + target.getClass().getName() + "::" + method.getName());
                        return super.invoke(target, method, arguments);
                    }
                });
        Collection collection = registry.routes("blah", Reactor.class);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            list.add(i);
        }
        SimpleCatalyst catalyst = new SimpleCatalyst("blah", registry);
        catalyst.setWaitTime(1000000L);
        CollectionDataSet<Integer> dataSet = new CollectionDataSet<>(list);

        long start = System.currentTimeMillis();
        int count = catalyst.builder(dataSet)
                .add(new MapTransform<>((operator, number) -> Math.sin(number * Math.PI / 360)))
                .scalar(new Count<>());
        System.out.println("Count " + count + " found in " + (System.currentTimeMillis() - start) + "ms");
    }

    public interface X {
        String sayHello(String text) ;
    }

    public static class XImpl implements X {

        private Collective collective;

        public XImpl(Collective collective) {
            this.collective = collective;
        }

        public String sayHello(String text) {
            System.out.println(collective.getHostAddress() + " says " + text);
            return collective.getHostAddress();
        }
    }
}
