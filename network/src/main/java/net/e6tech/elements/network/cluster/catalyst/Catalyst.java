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

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.Async;
import net.e6tech.elements.network.cluster.Registry;
import net.e6tech.elements.network.cluster.catalyst.dataset.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    public <T> Builder<Re, T, T> builder(DataSet<T> dataSet) {
        return new Builder<Re, T, T>(this, dataSet);
    }

    public <T> Builder<Re, T, T> builder(Series<Re, T, T> series, DataSet<T> dataSet) {
        return new Builder<>(this, series, dataSet);
    }

    public <T, R> R scalar(Scalar<Re, T, R> scalar, DataSet<T> dataSet) {
        Collection<R> result = collect(scalar, dataSet);
        Async<Re> async = registry.async(qualifier, reactorClass, waitTime);
        Series<Re, R, R> emptySeries = new Series<>();
        Scalar<Re, R, R> copy;
        try {
            copy = (Scalar) scalar.clone();
            copy.setSeries(emptySeries.allocate(new CollectionDataSet<>(result).segment(this)));
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return async.apply(p -> p.apply(copy))
                .toCompletableFuture().join();
    }

    public <T, R> Collection<R> collect(Scalar<Re, T, R> scalar, DataSet<T> dataSet) {
        List<Work<T, R>> workLoad = prepareWork(dataSet,
                segments -> {
                    try {
                        Scalar<Re, T, R> copy = scalar.clone();
                        copy.setSeries(scalar.getSeries().allocate(segments));
                        return copy;
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                });
        List<R> result = new ArrayList<>();
        for (Work<T, R> work: workLoad) {
            work.start();
        }
        for (Work<T, R> work: workLoad) {
            result.add(work.value());
        }
        return result;
    }

    public void run(Runnable ... runnables) {
        RemoteDataSet<?> dataSet = new RemoteDataSet<>();
        for (Runnable runnable : runnables)
            dataSet.add(reactor -> {
                runnable.run();
                return Collections.EMPTY_LIST.stream();
            });
        transform(new Series<>(), dataSet);
    }

    public <T, R> Collection<R> transform(Series<Re, T, R> series, DataSet<T> dataSet) {
        List<Work<T, Collection<R>>> workLoad =
                prepareWork(dataSet, segments -> series.allocate(segments));
        for (Work<T, Collection<R>> work: workLoad) {
            work.start();
        }

        Gatherer<R> gatherer = series.gatherer();
        for (Work<T, Collection<R>> work: workLoad) {
            gatherer.gather(work.value());
        }
        return gatherer.collection;
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
        Function<? extends Reactor, R> function;

        Work(Async<Reactor> async, Segments<T> segments, Function<Segments<T>, Function<? extends Reactor, R>> work) {
            this.async = async;
            this.segments = segments;
            this.work = work;
        }

        void start() {
            if (function == null) {
                // this will create a Series, Scalar or other Function<Reactor, R> for submiting to Reactor
                // the result should contain a segment removed from segments.  Therefore, work.apply should only be called once.
                function = work.apply(segments);
            }
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
