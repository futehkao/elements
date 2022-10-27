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

package net.e6tech.elements.common.util.concurrent;

import net.e6tech.elements.common.resources.BindClass;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
@BindClass({ExecutorService.class, Executor.class, ThreadPool.class})
public class ThreadPool implements java.util.concurrent.ThreadFactory, ExecutorService  {

    private static Map<String, ThreadPool> cachedThreadPools = new HashMap<>();
    private static Map<String, ThreadPool> rateLimitedThreadPools = new HashMap<>();
    private static Map<String, ThreadPool> fixedThreadPools = new HashMap<>();

    private String name;
    private boolean daemon = true;
    protected ExecutorService executorService;

    public ThreadPool(String name, ExecutorService executorService) {
        this.name = name;
        this.executorService = executorService;
    }


    public ThreadPool(String name, Function<ThreadFactory, ExecutorService> newPool) {
        this.name = name;
        this.executorService = newPool.apply(this);
    }

    /**
     * Return a thread pool that supports unlimited number of threads.  It will create threads as needed.
     * The default keep alive time for a thread is 60 seconds.
     * @param name name of the pool
     * @return ThreadPool
     */
    public static synchronized ThreadPool cachedThreadPool(String name) {
        return cachedThreadPools.computeIfAbsent(name, poolName ->
                new ThreadPool(name, Executors::newCachedThreadPool));
    }

    /*
     *
     * Using this type of threadPool may result in RejectedExecutionException when submitting a task.
     */
    @SuppressWarnings("squid:S1602")
    public static synchronized ThreadPool rateLimitedThreadPool(String name, int threadCoreSize, int threadMaxSize, long threadKeepAliveSec, int threadQueueSize) {
        return rateLimitedThreadPools.computeIfAbsent(name, poolName -> {
            return new ThreadPool(name, p ->
                new ThreadPoolExecutor(threadCoreSize, threadMaxSize, threadKeepAliveSec, TimeUnit.SECONDS, new ArrayBlockingQueue<>(threadQueueSize), p));
        });
    }

    /**
     * Returns a fixed size pool.  If there are more requests than the number of threads, they are put into a queue to be processed when
     * a thread becomes available.
     * @param name name of the pool
     * @param nThreads number of threads
     * @return ThreadPool
     */
    public static synchronized ThreadPool fixedThreadPool(String name, int nThreads) {
        return fixedThreadPools.computeIfAbsent(name, poolName ->
                new ThreadPool(name, p -> Executors.newFixedThreadPool(nThreads, p)));
    }

    public static synchronized ThreadPool fixedThreadPool(String name, int core, int max, long keepAlive) {
        return fixedThreadPools.computeIfAbsent(name, poolName ->
                new ThreadPool(name, p -> new ThreadPoolExecutor(core, max,
                        keepAlive, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        p)));
    }

    protected synchronized ExecutorService executorService() {
        return executorService;
    }

    public ThreadPool daemon() {
        return daemon(true);
    }

    public ThreadPool daemon(boolean b) {
        daemon = b;
        return this;
    }

    public ThreadPool rejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        if (executorService() instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executorService()).setRejectedExecutionHandler(handler);
        }
        return this;
    }

    public <U> Async<U> async(U service) {
        return new AsyncImpl<>(this, service);
    }

    @SuppressWarnings("unchecked")
    public <T extends ExecutorService> T unwrap() {
        return (T) executorService();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Broadcast");
        thread.setName(name + "-" + thread.getId());
        thread.setDaemon(daemon);
        return thread;
    }

    @Override
    public void shutdown() {
        if (executorService != null)
            executorService.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (executorService != null)
            return executorService.shutdownNow();
        else
            return Collections.EMPTY_LIST;
    }

    @Override
    public boolean isShutdown() {
        return executorService().isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executorService().isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService().awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executorService().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return executorService().submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executorService().submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executorService().invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return executorService().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return executorService().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return executorService().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        executorService().execute(command);
    }
}
