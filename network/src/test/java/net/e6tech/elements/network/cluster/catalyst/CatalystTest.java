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
import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.RemoteDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segment;
import net.e6tech.elements.network.cluster.catalyst.transform.Filter;
import net.e6tech.elements.network.cluster.catalyst.transform.Intersection;
import net.e6tech.elements.network.cluster.catalyst.transform.MapTransform;
import net.e6tech.elements.network.cluster.catalyst.transform.Union;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CatalystTest {
    Registry registry;
    SimpleCatalyst catalyst;
    DataSet<Integer> dataSet;

    public Registry create(int port) {
        ClusterNode clusterNode = RegistryTest.create(port);
        registry = clusterNode.getRegistry();
        registry.register("blah", Reactor.class, new Reactor() {
                },
                new Invoker() {
                    public Object invoke(Actor actor, Object target, Method method, Object[] arguments) {
                        System.out.println("Method " + target.getClass().getName() + "::" + method.getName() + " handled by " + actor.self());
                        return super.invoke(actor, target, method, arguments);
                    }
                }, 1000L);
        return registry;
    }

    void prepareDateSet() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add(i);
        }
        catalyst = new SimpleCatalyst("blah", registry);
        catalyst.setWaitTime(1000000L);
        dataSet = new CollectionDataSet<>(list);
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

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        Series<Integer, Long> t = Series.from(new MapTransform<>((reactor, number) ->
                (long) (number * number)));

        DataSet<Long> result = t.transform(catalyst, dataSet);
        assertEquals(result.asCollection().size(), dataSet.asCollection().size());
    }

    @Test
    public void filter() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        Series<Integer, Integer> t = Series.from(new Filter<>((reactor, number) ->
                number % 2 == 0));

        DataSet<Integer> result = t.transform(catalyst, dataSet);
        assertEquals(result.asCollection().size(), dataSet.asCollection().size() / 2);
    }

    @Test
    public void intersection() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        Set<Integer> set = new HashSet<>();
        set.add(-1);
        set.add(0);
        set.add(1);
        Series<Integer, Integer> t = Series.from(new Intersection<>(set));

        DataSet<Integer> result = t.transform(catalyst, dataSet);
        assertTrue(result.asCollection().size() == set.size() - 1); // not including -1
    }

    @Test
    public void union() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        Set<Integer> set = new HashSet<>();
        set.add(-1);
        set.add(0);
        set.add(1);
        Series<Integer, Integer> t = Series.from(new Union<>(new CollectionDataSet<>(set)));

        DataSet<Integer> result = t.transform(catalyst, dataSet);
        assertEquals(result.asCollection().size(), dataSet.asCollection().size() + 1); // including -1
    }


    @Test
    public void max() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 3)
            Thread.sleep(100);

        prepareDateSet();
        Series<Integer, Double> p2 = Series.from(new MapTransform<>((operator, number) ->
                Math.sin(number * Math.PI / 360)));

        long start = System.currentTimeMillis();
        Scalar<Integer, Double> scalar = new Scalar<>(p2, ((reactor, numbers) -> {
            Double value = null;
            for (double item : numbers) {
                if (value == null || value < item) {
                    value = item;
                }
            }
            return value;
        }));
        Double max = scalar.scalar(catalyst, dataSet);
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

        for (int i = 0; i < 5; i++) {
            remoteDataSet.add(segment);
        }

        SimpleCatalyst catalyst = new SimpleCatalyst("blah", registry);
        catalyst.setWaitTime(1000000L);

        Series<Integer, Double> transforms = Series.from(new MapTransform<>((operator, number) ->
                Math.sin(number * Math.PI / 360)));

        Scalar<Integer, Double> scalar = new Scalar<>(transforms, ((reactor, numbers) -> {
            Double value = null;
            for (double item : numbers) {
                if (value == null || value < item) {
                    value = item;
                }
            }
            return value;
        }));
        Double max = scalar.scalar(catalyst, remoteDataSet);
        System.out.println("Max " + max);
    }

    @Test
    public void distinct() throws Exception {
        create(2552);
        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add(i);
        }
        prepareDateSet();

        Series<Integer, Double> p2 = Series.from(new MapTransform<>((operator, number) ->
                Math.sin(number * Math.PI / 360)));

        DataSet<Double> distinct = new Distinct<>(p2).distinct(catalyst, dataSet);
        DataSet<Double> distinct2 = new Distinct<Double, Double>().distinct(catalyst, distinct);
    }

    @Test
    public void reduce() throws Exception {
        create(2552);
        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();

        Series<Integer, Double> p2 = Series.from(new MapTransform<>((operator, number) ->
                Math.sin(number * Math.PI / 360)));
        Double reduce = new Reduce<>(p2).reduce(catalyst, dataSet, (a, b) -> a + b);
        System.out.println(reduce);
    }
}
