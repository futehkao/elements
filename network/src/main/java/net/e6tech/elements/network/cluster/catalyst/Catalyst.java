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
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.Segments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Catalyst<Re extends Reactor> {
    private Registry registry;
    private long waitTime = 20000L;
    private String qualifier = "";
    private Class<Re> reactorClass;

    public Catalyst(String qualifier, Class<Re> reactorClass, Registry registry) {
        this.qualifier = qualifier;
        this.registry = registry;
        this.reactorClass = reactorClass;
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

    public <T, R> R scalar(DataSet<T> dataSet, Series<T, R> series, Mapping<Re, Collection<R>, R> mapping) {
        Collection<R> result = collect(dataSet, series, mapping);
        Async<Re> async = registry.async(qualifier, reactorClass, waitTime);
        Series<R, R> emptySeries = new Series<>();
        return async.apply(p -> p.apply(new Scalar<>(emptySeries.allocate(new CollectionDataSet<>(result).segment(this)), mapping)))
                .toCompletableFuture().join();
    }

    public <T, R> Collection<R> collect(DataSet<T> dataSet, Series<T, R> series, Mapping<Re, Collection<R>, R> mapping) {
        List<Work<T, R>> workLoad = prepareWork(dataSet,
                segments -> new Scalar<>(series.allocate(segments), mapping));
        List<R> result = new ArrayList<>();
        for (Work<T, R> work: workLoad) {
            work.start();
        }
        for (Work<T, R> work: workLoad) {
            result.add(work.value());
        }
        return result;
    }

    public  <T, R> List<R> transformToList(DataSet<T> dataSet, Series<T, R> series) {
        List<Work<T, Collection<R>>> workLoad =
                prepareWork(dataSet, segments -> series.allocate(segments));
        for (Work<T, Collection<R>> work: workLoad) {
            work.start();
        }

        List<R> result = new ArrayList<>();
        for (Work<T, Collection<R>> work: workLoad) {
            result.addAll(work.value());
        }
        return result;
    }

    private <T, O> List<Work<T, O>> prepareWork(DataSet<T> dataSet, Function<Segments<T>, Function<? extends Reactor, O>> work) {
        Segments<T> segments = dataSet.segment(this);
        List<Work<T, O>> workLoad = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            workLoad.add(new Work(registry.async(qualifier, reactorClass, waitTime), segments, work));
        }
        return workLoad;
    }

    private static class Work<T, R> {
        Async<Reactor> async;
        Segments<T> segments;
        CompletableFuture<R> future;
        Function<Segments<T>, Function<? extends Reactor, R>> work;

        Work(Async<Reactor> async, Segments<T> segments, Function<Segments<T>, Function<? extends Reactor, R>> work) {
            this.async = async;
            this.segments = segments;
            this.work = work;
        }

        void start() {
            Function<? extends Reactor, R> function = work.apply(segments);
            future = async.apply(reactor -> reactor.apply(function)).toCompletableFuture();
        }

        R value() {
            try {
                return future.join();
            } catch (Exception ex) {
                start();
                return future.join();
            }
        }
    }
}
