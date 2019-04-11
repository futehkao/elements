/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.network.cluster.catalyst;

import akka.actor.Actor;
import net.e6tech.elements.network.cluster.ClusterNode;
import net.e6tech.elements.network.cluster.Invoker;
import net.e6tech.elements.network.cluster.Registry;
import net.e6tech.elements.network.cluster.RegistryTest;
import net.e6tech.elements.network.cluster.catalyst.dataset.RemoteDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segment;
import net.e6tech.elements.network.cluster.catalyst.transform.MapTransform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CatalystTest {

    public Registry create(int port)  {
        ClusterNode clusterNode = RegistryTest.create(port);
        Registry registry = clusterNode.getRegistry();
        registry.register("blah", Reactor.class, new Reactor() { }, new Invoker() {
            public Object invoke(Actor actor, Object target, Method method, Object[] arguments) {
                System.out.println("Method " + method.getName() + " handled by " + actor.self());
                return super.invoke(actor, target, method, arguments);
            }
        }, 1000L);
        return registry;
    }

    @Test
    public void vm1() throws Exception {
        create(2551);
        synchronized (this) {
            wait();
        }
    }

    @Test
    public void vm2() throws Exception {
        create(2553);
        synchronized (this) {
            wait();
        }
    }

    @Test
    public void map() throws Exception {
        Registry registry = create(2552);

        while (registry.routes("blah", Reactor.class).size() < 3)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(i);
        }
        Catalyst<Integer, Integer> propagator = new Catalyst<>("blah", registry, list);
        Catalyst<Integer, Long> p2 = propagator.addTransform(new MapTransform<>((operator, number) ->
                (long) (number * number)));
        Catalyst<Long, Long> result = p2.transform();
        Thread.sleep(100);
    }

    @Test
    public void max() throws Exception {
        Registry registry = create(2552);

        while (registry.routes("blah", Reactor.class).size() < 2)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000000; i++) {
            list.add(i);
        }
        Catalyst<Integer, Integer> p1 = new Catalyst<>("blah", registry, list);
        p1.setWaitTime(100000L);
        Catalyst<Integer, Double> p2 = p1.addTransform(new MapTransform<>((operator, number) ->
                 Math.sin(number * Math.PI / 360)));

        long start = System.currentTimeMillis();
        Double max = p2.scalar(((operator, longs) -> {
            Double value = null;
            for (double item : longs) {
                if (value == null || value < item) {
                    value = item;
                }
            }
            return value;
        }));
        System.out.println("Max " + max + " found in " + (System.currentTimeMillis() - start) + "ms");
    }

    @Test
    public void remoteDataSet() throws Exception {
        Registry registry = create(2552);

        while (registry.routes("blah", Reactor.class).size() < 3)
            Thread.sleep(100);

        RemoteDataSet<Integer> remoteDataSet = new RemoteDataSet<>();
        Segment<Integer> segment = reactor -> {
            List<Integer> list = new ArrayList<>();
            Random random = new Random();
            for (int i = 0; i < 1000; i++) {
                list.add(random.nextInt(1000));
            }
            return list.stream();
        };

        for (int i = 0; i < 5; i ++) {
            remoteDataSet.add(segment);
        }
        Catalyst<Integer, Integer> p1 = new Catalyst<>("blah", registry, remoteDataSet);
        p1.setWaitTime(100000L);
        Catalyst<Integer, Double> p2 = p1.addTransform(new MapTransform<>((operator, number) ->
                Math.sin(number * Math.PI / 360)));

        Double max = p2.scalar(((operator, longs) -> {
            Double value = null;
            for (double item : longs) {
                if (value == null || value < item) {
                    value = item;
                }
            }
            return value;
        }));
        System.out.println("Max " + max);
    }
}
