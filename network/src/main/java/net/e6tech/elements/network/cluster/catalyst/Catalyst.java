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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Catalyst<T, R> {
    private Registry registry;
    private long waitTime = 20000L;
    private List<Collection<T>> segments = new ArrayList<>();
    private String qualifier;
    private Collection<T> collection;
    private Transforms<T, R> transforms = new Transforms<>();

    public Catalyst(String qualifier, Registry registry, Collection<T> collection) {
        this.qualifier = qualifier;
        this.registry = registry;
        this.collection = collection;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    private void segment() {
        int routes = registry.routes(qualifier, Operator.class).size();
        int split = 0;
        if (routes > 1) {
            double ln = Math.log(routes) / Math.log(2);
            int whole = (int) ln;
            if (Math.pow(2, ln) - Math.pow(2, whole) > whole * .2) {
                whole++;
            }
            split = whole;
        }

        List<Spliterator<T>> tmp = new ArrayList<>();
        Spliterator<T> spliterator = collection.spliterator();
        List<Spliterator<T>> spliterators = new ArrayList<>();
        spliterators.clear();
        spliterators.add(spliterator);
        for (int i = 0; i < split; i++) {
            tmp.clear();
            for (Spliterator<T> segment : spliterators) {
                Spliterator<T> second = segment.trySplit();
                if (second != null) {
                    tmp.add(second);
                }
                tmp.add(segment);
            }
            spliterators.clear();
            spliterators.addAll(tmp);
        }
        for (Spliterator<T> s : spliterators) {
            segments.add(StreamSupport.stream(s, false).collect(Collectors.toList()));
        }
    }

    public <U> Catalyst<T, U> map(Mapping<Operator, R, U> mapping) {
        transforms.add(new MapTransform<>(mapping));
        return (Catalyst) this;
    }

    public R scalar(Mapping<Operator, Collection<R>, R> mapping) {
        Collection<R> result = collect(mapping);
        Async<Operator> async = registry.async(qualifier, Operator.class, waitTime);
        Transforms<R, R> nullTransforms = new Transforms<>();
        return async.apply(p -> p.scalar(new Scalar<>(nullTransforms.of(result), mapping)))
                .toCompletableFuture().join();
    }

    protected Collection<R> collect(Mapping<Operator, Collection<R>, R> mapping) {
        List<Work<T>> workLoad = prepareWork();
        List<R> result = new ArrayList<>();
        for (Work<T> work: workLoad) {
            try {
                R value = work.async.apply(p -> p.scalar(new Scalar<>(transforms.of(work.segment), mapping)))
                        .toCompletableFuture().join();
                result.add(value);
            } catch (Exception ex) {
                R value = work.async.apply(p -> p.scalar(new Scalar<>(transforms.of(work.segment), mapping)))
                        .toCompletableFuture().join();
                result.add(value);
            }

        }
        return result;
    }

    public Collection<R> transform() {
        List<Work<T>> workLoad = prepareWork();
        List<R> result = new ArrayList<>();
        for (Work<T> work : workLoad) {
            try {
                Collection<R> collection = work.async.apply(p -> p.transform(transforms.of(work.segment))).toCompletableFuture().join();
                result.addAll(collection);
            } catch (Exception ex) {
                Collection<R> collection = work.async.apply(p -> p.transform(transforms.of(work.segment))).toCompletableFuture().join();
                result.addAll(collection);
            }
        }
        return result;
    }

    private List<Work<T>> prepareWork() {
        segment();
        List<Work<T>> workLoad = new ArrayList<>();
        for (Collection<T> segment : segments) {
            workLoad.add(new Work(registry.async(qualifier, Operator.class, waitTime), segment));
        }
        return workLoad;
    }

    private static class Work<T> {
        Async<Operator> async;
        Collection<T> segment;

        Work(Async<Operator> async, Collection<T> segment) {
            this.async = async;
            this.segment = segment;
        }
    }
}
