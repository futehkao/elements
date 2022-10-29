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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.cluster.ClusterNode;
import net.e6tech.elements.network.cluster.ClusterNodeTest;
import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.RemoteDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segment;
import net.e6tech.elements.network.cluster.catalyst.scalar.*;
import net.e6tech.elements.network.cluster.catalyst.transform.*;
import net.e6tech.elements.network.cluster.invocation.RegistryActor;
import org.junit.jupiter.api.Test;

import java.lang.invoke.SerializedLambda;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
public class CatalystTest {
    RegistryActor registry;
    SimpleCatalyst catalyst;
    DataSet<Integer> dataSet;

    public RegistryActor create(int port) {
        ClusterNode clusterNode = ClusterNodeTest.create(port);
        registry = clusterNode.getRegistry();
        try {
            List list = registry.register("blah", Reactor.class, new Reactor() {
                    }, (target,  method, arguments) -> {
                System.out.println("Method " + target.getClass().getName() + "::" + method.getName());
                return method.invoke(target, arguments);
            });
            assertTrue(!list.isEmpty());
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return registry;
    }

    void prepareDateSet() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
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
    void kryo() {
        Kryo kryo = new Kryo();
        kryo.register(SerializedLambda.class);
        kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());
        kryo.setRegistrationRequired(false);
        Output output = new Output(4096, 4096);
        kryo.writeClassAndObject(output, new M<Integer, Integer>());
        output.close();
        Object obj = kryo.readClassAndObject(new Input(output.toBytes()));
    }

    @Test
    public void remoteDataSet() throws Exception {

        RegistryActor registry = create(2552);

        registry.waitForRoutes("blah", Reactor.class, coll -> coll.size() >= 2, 60000L);

        RemoteDataSet<Integer> remoteDataSet = new RemoteDataSet<>();
        Segment<Integer> segment = reactor -> {
            List<Integer> list = new ArrayList<>();
            Random random = new Random();
            for (int i = 0; i < 10; i++) {
                list.add(random.nextInt(1000));
            }
            System.out.println("Done");
            try {
                Thread.currentThread().sleep(10L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return list.stream();
        };

        for (int i = 0; i < 100; i++) {
            remoteDataSet.add(segment);
        }

        SimpleCatalyst catalyst = new SimpleCatalyst("blah", registry);
        catalyst.setWaitTime(100000L);

        Double max = catalyst.builder(remoteDataSet)
                .add(new MapTransform<>((operator, number) ->
                        Math.sin(number * Math.PI / 360)))
                .scalar(new Max<>());

        System.out.println("Max " + max);
    }

    @Test
    public void map() throws Exception {
        RegistryActor registry = create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        Series<Reactor, Integer, Long> t = Series.from(new MapTransform<>((reactor, number) ->
                (long) (number * number)));

        DataSet<Long> result = catalyst.builder(dataSet)
                .add(new MapTransform<>((reactor, number) -> (long) (number * number)))
                .transform();
        assertEquals(result.asCollection().size(), dataSet.asCollection().size());
    }

    @Test
    public void filter() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        DataSet<Integer> result = catalyst.builder(dataSet)
                .add(new Filter<>((reactor, number) ->
                        number % 2 == 0))
                .transform();
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
        DataSet<Integer> result = catalyst.builder(dataSet)
                .add(new Intersection<>(set))
                .transform();

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
        DataSet<Integer> result = catalyst.builder(dataSet)
                .add(new Union<>(new CollectionDataSet<>(set)))
                .transform();

        assertEquals(result.asCollection().size(), dataSet.asCollection().size() + 1); // including -1
    }

    @Test
    public void count() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        long start = System.currentTimeMillis();

        int count = catalyst.builder(dataSet)
                .add(new MapTransform<>((operator, number) -> Math.sin(number * Math.PI / 360)))
                .scalar(new Count<>());
        System.out.println("Count " + count + " found in " + (System.currentTimeMillis() - start) + "ms");
    }


    @Test
    public void max() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 3)
            Thread.sleep(100);

        prepareDateSet();
        long start = System.currentTimeMillis();

        Double max = catalyst.builder(dataSet)
                .add(new MapTransform<>((operator, number) -> Math.sin(number * Math.PI / 360)))
                .scalar(new Max<>());
        System.out.println("Max " + max + " found in " + (System.currentTimeMillis() - start) + "ms");
    }

    @Test
    public void min() throws Exception {
        create(2552);

        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();
        long start = System.currentTimeMillis();

        Double min = catalyst.builder(dataSet)
                .add(new MapTransform<>((operator, number) -> Math.sin(number * Math.PI / 360)))
                .scalar(new Min<>());
        System.out.println("Min " + min + " found in " + (System.currentTimeMillis() - start) + "ms");
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

        DataSet<Double> distinct = catalyst.builder(new Distinct<>(), dataSet)
                .add(new MapTransform<>((operator, number) ->
                Math.sin(number * Math.PI / 360)))
                .transform();
    }

    @Test
    public void reduce() throws Exception {
        create(2552);
        while (registry.routes("blah", Reactor.class).size() < 1)
            Thread.sleep(100);

        prepareDateSet();

        Double reduce = catalyst.builder(dataSet)
            .add(new MapTransform<>((operator, number) ->
                    Math.sin(number * Math.PI / 360)))
                .scalar(new Reduce<>(Double::sum));
        System.out.println(reduce);
    }
}
