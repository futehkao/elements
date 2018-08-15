/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

public class AsyncImpl<U> implements Async<U> {

    private ThreadPool threadPool;
    private U service;

    public AsyncImpl(ThreadPool threadPool, U service) {
        this.threadPool = threadPool;
        this.service = service;
    }

    @Override
    public long getTimeout() {
        return 0;
    }

    @Override
    public void setTimeout(long timeout) {
        //
    }

    @Override
    public <R> CompletionStage<R> apply(Function<U, R> function) {
        return CompletableFuture.supplyAsync(() -> function.apply(service), threadPool);
    }

    @Override
    public CompletionStage<Void> accept(Consumer<U> consumer) {
        return CompletableFuture.runAsync(() -> consumer.accept(service));
    }
}
