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

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import net.e6tech.elements.common.util.SystemException;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class DisruptorPool {
    private DisruptorConfig config = new DisruptorConfig();
    Disruptor<Event> disruptor;

    public DisruptorPool() {
        start();
    }

    public DisruptorConfig getConfig() {
        return config;
    }

    public void setConfig(DisruptorConfig config) {
        this.config = config;
    }

    public Disruptor<Event> getDisruptor() {
        return disruptor;
    }

    public void setDisruptor(Disruptor<Event> disruptor) {
        this.disruptor = disruptor;
    }

    public void start() {
        disruptor = new Disruptor<>(Event::new, config.getBufferSize(), DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new YieldingWaitStrategy());
        WorkHandler<Event> handler = Event::handle;
        WorkHandler<Event>[] workers = new WorkHandler[config.getHandlerSize()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = handler;
        }
        disruptor.handleEventsWithWorkerPool(workers)
                .then((event, sequence, endOfBatch) -> event.clear());
        disruptor.start();
    }

    public RunnableWait run(Runnable runnable) {
        return run(runnable, null);
    }

    public RunnableWait run(Runnable runnable, Consumer<Exception> exceptionHandler) {
        Result<Void> result = new Result<>();
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence, buffer) -> {
            event.runnable = runnable;
            event.result = result;
            event.exceptionHandler = exceptionHandler;
        });

        return new RunnableWait(result);
    }

    public void runAsync(Runnable runnable) {
        runAsync(runnable, null);
    }

    public void runAsync(Runnable runnable, Consumer<Exception> exceptionHandler) {
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence, buffer) -> {
            event.runnable = runnable;
            event.exceptionHandler = exceptionHandler;
        });
    }

    public <V> CallableWait<V> call(Callable<V> callable) {
        return call(callable, null);
    }

    public <V> CallableWait<V> call(Callable<V> callable, Consumer<Exception> exceptionHandler) {
        Result result = new Result();
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence, buffer) -> {
            event.callable = callable;
            event.result = result;
            event.exceptionHandler = exceptionHandler;
        });

        return new CallableWait<>(result);
    }

    public static class Wait<V> {
        Result<V> result;

        Wait(Result<V> result) {
            this.result = result;
        }

        protected void await(long timeout)  throws TimeoutException {
            long start = System.currentTimeMillis();
            boolean first = true;
            synchronized (result) {
                while (!result.done) {
                    if (!first && System.currentTimeMillis() - start > timeout) {
                        throw new TimeoutException();
                    }
                    if (first)
                        first = false;
                    try {
                        long wait = timeout - (System.currentTimeMillis() - start);
                        if (wait > 0)
                            result.wait(wait);
                        if (result.exception != null)
                            throw new SystemException(result.exception);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new SystemException(e);
                    }
                }
            }
        }
    }

    public static class RunnableWait extends Wait<Void> {
        RunnableWait(Result<Void> result) {
            super(result);
        }

        public void complete(long timeout)  throws TimeoutException {
            await(timeout);
        }
    }

    public static class CallableWait<V> extends Wait<V> {
        CallableWait(Result<V> result) {
            super(result);
        }

        public V complete(long timeout)  throws TimeoutException {
            await(timeout);
            return result.returnValue;
        }
    }

    private static class Result<V> {
        private Exception exception;
        private V returnValue;
        private volatile boolean done = false;
    }

    private static class Event<V> {
        private Result result;
        private Runnable runnable;
        private Callable<V> callable;
        private Consumer<Exception> exceptionHandler;

        void clear() {
            result = null;
            runnable = null;
            callable = null;
            exceptionHandler = null;
        }

        void handle() {
            if (runnable != null) {
                run();
            } else {
                call();
            }
            if (result != null) {
                synchronized (result) {
                    result.notifyAll();
                    result.done = true;
                }
            }
        }

        void run() {
            try {
                runnable.run();
            } catch (Exception ex) {
                if (exceptionHandler != null)
                    exceptionHandler.accept(ex);
                else if (result != null)
                    result.exception = ex;
            }
        }

        void call() {
            try {
                Object ret = callable.call();
                if (result != null)
                    result.returnValue = ret;
            } catch (Exception ex) {
                if (exceptionHandler != null)
                    exceptionHandler.accept(ex);
                else if (result != null)
                    result.exception = ex;
            }
        }
    }
}
