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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CatalystTest {

    public Registry create(int port)  {
        ClusterNode clusterNode = RegistryTest.create(port);
        Registry registry = clusterNode.getRegistry();
        registry.register("blah", Operator.class, new Operator() { }, new Invoker() {
            public Object invoke(Actor actor, Object target, Method method, Object[] arguments) {
                System.out.println("sender=" + actor.sender() + " handled by " + actor.self());
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

        while (registry.routes("blah", Operator.class).size() < 3)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(i);
        }
        Catalyst<Integer, Integer> propagator = new Catalyst<>("blah", registry, list);
        Catalyst<Integer, Long> p2 = propagator.map((operator, number) ->
                (long) (number * number));
        Collection<Long> collection = p2.transform();
        Thread.sleep(100);
    }

    @Test
    public void max() throws Exception {
        Registry registry = create(2552);

        while (registry.routes("blah", Operator.class).size() < 2)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000000; i++) {
            list.add(i);
        }
        Catalyst<Integer, Integer> p1 = new Catalyst<>("blah", registry, list);
        p1.setWaitTime(100000L);
        Catalyst<Integer, Double> p2 = p1.map((operator, number) ->
                 Math.sin(number * Math.PI / 360));

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
}
