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

package net.e6tech.elements.common.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

public class LazyThreadPool extends ThreadPool {

    private Function<ThreadFactory, ExecutorService> executorServiceFunction;

    public LazyThreadPool(String name, Function<ThreadFactory, ExecutorService> newPool) {
        super(name, (ExecutorService) null);
        this.executorServiceFunction = newPool;
    }

    protected synchronized ExecutorService executorService() {
        if (executorService == null) { // double sync lock.  Won't work during heavy multithreaded load.  However, for start up, it works fine.
            synchronized (this) {
                if (executorService == null) {
                    executorService = executorServiceFunction.apply(this);
                }
            }
        }
        return executorService;
    }

}
