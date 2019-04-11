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

import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.Registry;
import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionSegment;
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segment;
import net.e6tech.elements.network.cluster.catalyst.transform.Transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Catalyst<T, R> {
    private Registry registry;
    private long waitTime = 20000L;
    private String qualifier;
    private DataSet<T> dataSet;
    private Tuple<T, R> tuple = new Tuple<>();

    public Catalyst(String qualifier, Registry registry, Collection<T> dataSet) {
        this.qualifier = qualifier;
        this.registry = registry;
        this.dataSet = new CollectionDataSet<>(dataSet);
    }

    public Catalyst(String qualifier, Registry registry, DataSet<T> dataSet) {
        this.qualifier = qualifier;
        this.registry = registry;
        this.dataSet = dataSet;
    }

    public Catalyst(String qualifier, Registry registry, long waitTime, Function<Reactor, Collection<T>>... transformables) {
        this.qualifier = qualifier;
        this.registry = registry;
        this.waitTime = waitTime;

        if (transformables != null) {
            List<Work<Function<Reactor, Collection<T>>, Collection<T>>> workLoad = new ArrayList<>();
            for (Function<Reactor, Collection<T>> t : transformables) {
                workLoad.add(new Work(registry.async(qualifier, Reactor.class, waitTime), t,
                        (BiFunction<Reactor, Function<Reactor, Collection<T>>, Collection<T>>) (reactor, tr) -> reactor.apply(tr)));
            }
            List<T> result = new ArrayList<>();
            for (Work<Function<Reactor, Collection<T>>, Collection<T>> work : workLoad) {
                result.addAll(work.value());
            }
            this.dataSet = new CollectionDataSet<>(result);
        }
    }

    protected Catalyst(Catalyst catalyst, Collection<T> dataSet) {
        this.qualifier = catalyst.qualifier;
        this.registry = catalyst.registry;
        this.dataSet = new CollectionDataSet<>(dataSet);;
        this.waitTime = catalyst.waitTime;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    public String getQualifier() {
        return qualifier;
    }

    public Registry getRegistry() {
        return registry;
    }

    public <U> Catalyst<T, U> addTransform(Transform<R, U> transform) {
        tuple.add(transform);
        return (Catalyst) this;
    }

    public R scalar(Mapping<Reactor, Collection<R>, R> mapping) {
        Collection<R> result = collect(mapping);
        Async<Reactor> async = registry.async(qualifier, Reactor.class, waitTime);
        Tuple<R, R> nullTransforms = new Tuple<>();
        return async.apply(p -> p.apply(new Scalar<>(nullTransforms.of(new CollectionSegment<>(result)), mapping)))
                .toCompletableFuture().join();
    }

    protected Collection<R> collect(Mapping<Reactor, Collection<R>, R> mapping) {
        List<Work<Segment<T>, R>> workLoad = prepareWork((reactor, segment) ->
                reactor.apply(new Scalar<>(tuple.of(segment), mapping)));
        List<R> result = new ArrayList<>();
        for (Work<Segment<T>, R> work: workLoad) {
            work.start();
        }

        for (Work<Segment<T>, R> work: workLoad) {
            result.add(work.value());
        }
        return result;
    }

    public Catalyst<R, R> transform() {
        List<Work<Segment<T>, Collection<R>>> workLoad = prepareWork((reactor, segment) -> reactor.apply(tuple.of(segment)));
        for (Work<Segment<T>, Collection<R>> work: workLoad) {
            work.start();
        }

        List<R> result = new ArrayList<>();
        for (Work<Segment<T>, Collection<R>> work: workLoad) {
            result.addAll(work.value());
        }
        return new Catalyst<>(this, result);
    }

    private <In, Out> List<Work<In, Out>> prepareWork(BiFunction<Reactor, In, Out> work) {
        dataSet.initialize(this);
        List<Work<In, Out>> workLoad = new ArrayList<>();
        for (Segment<T> segment : dataSet.segments()) {
            workLoad.add(new Work(registry.async(qualifier, Reactor.class, waitTime), segment, work));
        }
        return workLoad;
    }

    private static class Work<In, Out> {
        Async<Reactor> async;
        In input;
        CompletableFuture<Out> future;
        BiFunction<Reactor, In, Out> work;

        Work(Async<Reactor> async, In input, BiFunction<Reactor, In, Out> work) {
            this.async = async;
            this.input = input;
            this.work = work;
        }

        void start() {
            future = async.apply(p -> work.apply(p, input)).toCompletableFuture();
        }

        Out value() {
            try {
                return future.join();
            } catch (Exception ex) {
                start();
                return future.join();
            }
        }
    }
}
