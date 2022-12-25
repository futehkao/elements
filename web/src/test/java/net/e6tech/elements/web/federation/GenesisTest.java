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

import net.e6tech.elements.common.federation.Registry;
import net.e6tech.elements.common.resources.Atom;
import net.e6tech.elements.common.resources.BindClass;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.catalyst.Reactor;
import net.e6tech.elements.network.cluster.catalyst.SimpleCatalyst;
import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.scalar.Count;
import net.e6tech.elements.network.cluster.catalyst.scalar.Max;
import net.e6tech.elements.network.cluster.catalyst.transform.MapTransform;
import org.junit.jupiter.api.Test;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenesisTest {
    int seedPort;

    private GenesisImpl create(int port) {
        if (seedPort == 0)
            seedPort = port;
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);

        GenesisImpl genesis = rm.newInstance(GenesisImpl.class);
        genesis.getCluster().setHostAddress("http://127.0.0.1:" + port + "/restful");
        genesis.getCluster().setSeeds(new String[] {"http://127.0.0.1:" + seedPort + "/restful"});
        genesis.initialize(null);

        Registry registry = genesis.getCluster().getServiceProvider(Registry.class);
        List<String> routes = registry.register("blah", Reactor.class, new Reactor() {
                }, (target,  method, arguments) -> {
                        System.out.println("Method " + target.getClass().getName() + "::" + method.getName());
                        return method.invoke(target, arguments);
                    }
                );
        return genesis;
    }

    @Test
    void basic() throws ExecutionException, InterruptedException {
        int size = 10;
        List<GenesisImpl> gens = new LinkedList<>();
        List<AtomicBoolean> recvs = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            gens.add(create(3903 + i));
            recvs.add(new AtomicBoolean(false));
            gens.get(i).getCluster().register("x", X.class, new XImpl(gens.get(i).getCluster()));
        }

        while (gens.get(0).getRegistry().routes("blah", Reactor.class).size() < size)
            Thread.sleep(100);

        Async<X> async = gens.get(0).getCluster().async("x", X.class);
        for (int i = 0; i < size; i++) {
            CompletionStage<String> stage = async.apply(proxy -> proxy.sayHello("hello"));
            String response = stage.toCompletableFuture().get();
        }
    }

    @Test
    void broadcast() throws InterruptedException {
        int size = 10;
        List<GenesisImpl> gens = new LinkedList<>();
        List<AtomicBoolean> recvs = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            gens.add(create(3903 + i));
            recvs.add(new AtomicBoolean(false));
        }

        while (gens.get(0).getRegistry().routes("blah", Reactor.class).size() < size)
            Thread.sleep(100);

        for (int i = 0; i < size; i++) {
            int j = i;
            gens.get(i).getCluster().subscribe("test",
                    notice -> {
                        System.out.println("gen " + j + " " + notice);
                        recvs.get(j).set(true);
                    });
        }

        gens.get(0).getCluster().publish(new Notice<>("test", "Hello"));

        boolean done = false;
        while (!done) {
            done = recvs.stream().anyMatch(p -> ! p.get());
            Thread.sleep(100);
        }
        System.out.flush();
        Thread.sleep(500);
    }

    @Test
    void broadcast2() throws InterruptedException {
        int size = 10;
        List<GenesisImpl> gens = new LinkedList<>();
        List<AtomicBoolean> recvs = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            gens.add(create(3903 + i));
            recvs.add(new AtomicBoolean(false));
        }

        while (gens.get(0).getRegistry().routes("blah", Reactor.class).size() < size)
            Thread.sleep(100);

        for (int i = 0; i < size; i++) {
            int j = i;
            gens.get(i).getProvision().getResourceManager().getNotificationCenter().subscribe("test",
                    notice -> {
                        System.out.println("gen " + j + " " + notice);
                        recvs.get(j).set(true);
                    });
        }

        gens.get(0).getProvision().getResourceManager().getNotificationCenter().publish(new Notice<>("test", "Hello"));

        boolean done = false;
        while (!done) {
            done = recvs.stream().anyMatch(p -> ! p.get());
            Thread.sleep(100);
        }
        System.out.flush();
        Thread.sleep(500);
    }

    @Test
    void max() throws Exception {
        GenesisImpl genesis = create(3903);
        create(3904);

        while (genesis.getRegistry().routes("blah", Reactor.class).size() < 2)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            list.add(i);
        }
        SimpleCatalyst catalyst = new SimpleCatalyst("blah", genesis.getRegistry());
        catalyst.setWaitTime(1000000L);
        CollectionDataSet<Integer> dataSet = new CollectionDataSet<>(list);

        long start = System.currentTimeMillis();

        String str = catalyst.getQualifier();
        Provision provision = new Provision();
        Double max = catalyst.builder(dataSet)
                .add(new MapTransform<Reactor, Integer, Double>((operator, number) -> {
                    double sine = Math.sin(number * Math.PI / 360);
                    System.out.println(str);
                    provision.toString();
                    return sine;
                }))
                .scalar(new Max<>());
        System.out.println("Max " + max + " found in " + (System.currentTimeMillis() - start) + "ms");

        genesis.shutdown();
    }

    @Test
    void count() throws Exception {
        GenesisImpl genesis = create(3903);
        create(3904);

        while (genesis.getRegistry().routes("blah", Reactor.class).size() < 2)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            list.add(i);
        }
        SimpleCatalyst catalyst = new SimpleCatalyst("blah", genesis.getRegistry());
        catalyst.setWaitTime(1000000L);
        CollectionDataSet<Integer> dataSet = new CollectionDataSet<>(list);

        long start = System.currentTimeMillis();
        int count = catalyst.builder(dataSet)
                .add(new MapTransform<>((operator, number) -> Math.sin(number * Math.PI / 360)))
                .scalar(new Count<>());
        System.out.println("Count " + count + " found in " + (System.currentTimeMillis() - start) + "ms");
    }

    @Test
    void provision() throws ScriptException, ExecutionException, InterruptedException {
        ResourceManager rm = new ResourceManager();
        rm.load("./conf/provisioning/federation/genesis.groovy");
        Atom atom = rm.getAtom("genesis");
        GenesisImpl genesis = (GenesisImpl) atom.get("_genesis");

        Async<X> async = genesis.getCluster().async("x", X.class);
        CompletionStage<String> stage = async.apply(proxy -> proxy.sayHello("hello"));
        String response = stage.toCompletableFuture().get();
        System.out.println(response);
    }

    @BindClass(X.class)
    public interface X {
        String sayHello(String text) ;
    }

    public static class XImpl implements X {

        private CollectiveImpl collective;

        public XImpl() {
        }

        public XImpl(CollectiveImpl collective) {
            this.collective = collective;
        }

        public String sayHello(String text) {
            System.out.println(collective.getHostAddress() + " says " + text);
            return collective.getHostAddress();
        }
    }
}
