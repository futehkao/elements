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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("unchecked")
public class DisruptorPool {

    private Monitor monitor = new Monitor();
    Disruptor<Event> disruptor;
    private int bufferSize = 1024;
    private int handlerSize = Runtime.getRuntime().availableProcessors() * 2; // times 2 in case of timeout

    public DisruptorPool() {
    }

    public Disruptor<Event> getDisruptor() {
        return disruptor;
    }

    public void setDisruptor(Disruptor<Event> disruptor) {
        this.disruptor = disruptor;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getHandlerSize() {
        return handlerSize;
    }

    public void setHandlerSize(int handlerSize) {
        this.handlerSize = handlerSize;
    }

    public synchronized void start() {
        if (!monitor.isAlive()) {
            monitor.capacity = handlerSize;
            monitor.start();
        }
        if (disruptor != null)
            return;

        disruptor = new Disruptor<>(Event::new, getBufferSize(), DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new YieldingWaitStrategy());
        WorkHandler<Event> handler = Event::handle;
        WorkHandler<Event>[] workers = new WorkHandler[getHandlerSize()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = handler;
        }
        disruptor.handleEventsWithWorkerPool(workers)
                .then((event, sequence, endOfBatch) -> event.clear());
        disruptor.start();
    }

    public synchronized void restart() {
        shutdown();
        start();
    }

    public synchronized void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
            disruptor = null;
        }
        if (monitor.isAlive()) {
            monitor.shutdown = true;
            monitor.thread.interrupt();
        }
    }

    public RunnableWait run(Runnable runnable) {
        return run(runnable, null, 0L);
    }

    public RunnableWait run(Runnable runnable, long timeout) {
        return run(runnable, null, timeout);
    }

    public RunnableWait run(Runnable runnable, Handler handler) {
        return run(runnable, handler , 0L);
    }

    public RunnableWait run(Runnable runnable, Handler handler, long timeout) {
        Result<Void> result = new Result<>();
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence) -> {
            event.runnable = runnable;
            event.handler = handler;
            prepareEvent(event, result, handler, timeout);
        });

        monitor(result, timeout);

        return new RunnableWait(result);
    }

    public void async(Runnable runnable) {
        async(runnable, null, 0L);
    }

    public void async(Runnable runnable, long timeout) {
        async(runnable, null, timeout);
    }

    public void async(Runnable runnable, Handler handler, long timeout) {
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        Result result = timeout > 0 ? new Result() : null;
        ringBuffer.publishEvent((event, sequence) -> {
            event.runnable = runnable;
            prepareEvent(event, result, handler, timeout);
        });

        monitor(result, timeout);
    }

    public <V> CallableWait<V> call(Callable<V> callable) {
        return call(callable, null, 0L);
    }

    public <V> CallableWait<V> call(Callable<V> callable, long timeout) {
        return call(callable, null, timeout);
    }

    public <V> CallableWait<V> call(Callable<V> callable, Handler<V> handler) {
        return call(callable, handler, 0L);
    }

    public <V> CallableWait<V> call(Callable<V> callable, Handler<V> handler, long timeout) {
        Result result = new Result();
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence, buffer) -> {
            event.callable = callable;
            prepareEvent(event, result, handler, timeout);
        });

        monitor(result, timeout);

        return new CallableWait<>(result);
    }

    private void prepareEvent(Event event, Result result, Handler handler, long timeout ) {
        event.result = result;
        if (handler != null)
            event.handler = handler;
        event.timeout = timeout;
        event.monitor = monitor;
        event.expiration = System.currentTimeMillis() + timeout;
    }

    protected void monitor(Result result, long timeout) {
        if (result == null || timeout <= 0)
            return;
        if (true)
            return;
        RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence) -> {
            event.result = result;
            event.timeout = timeout;
        });
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
                    try {
                        if (timeout <= 0) {
                            result.wait();
                        } else {
                            if (!first && System.currentTimeMillis() - start > timeout) {
                                throw new TimeoutException();
                            }
                            if (first)
                                first = false;
                            long wait = timeout - (System.currentTimeMillis() - start);
                            if (wait > 0)
                                result.wait(wait);
                        }
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

        public void complete() {
            try {
                await(0);
            } catch (TimeoutException e) {
                // should not happen
            }
        }

        public void complete(long timeout)  throws TimeoutException {
            await(timeout);
        }
    }

    public static class CallableWait<V> extends Wait<V> {
        CallableWait(Result<V> result) {
            super(result);
        }

        public V complete()  {
            try {
                return complete(0);
            } catch (TimeoutException e) {
                // should not happen
                return null;
            }
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
        private Thread thread;
    }

    private static class Event<V> {
        private Result result;
        private Runnable runnable;
        private Callable<V> callable;
        private Handler<V> handler = defaultHandler;
        private long timeout = 0L;
        private long expiration;
        private Monitor monitor;

        void clear() {
            result = null;
            runnable = null;
            callable = null;
            handler = defaultHandler;
            timeout = 0L;
            expiration = 0L;
        }

        void handle() {
            if (timeout > 0 && result != null) {
                result.thread = Thread.currentThread();
                monitor.add(this);
            }

            if (runnable != null) {
                run();
            } else {
                call();
            }
            if (result != null) {
                synchronized (result) {
                    result.done = true;
                    result.thread = null;
                    result.notifyAll();
                }
            }
        }

        void run() {
            try {
                runnable.run();
                handler.callback(null);
            } catch (Exception ex) {
                if (!handler.exception(ex)) {
                    if (result != null)
                        result.exception = ex;
                }
            }
        }

        void call() {
            try {
                Object ret = callable.call();
                if (result != null)
                    result.returnValue = ret;
                handler.callback((V)ret);
            } catch (Exception ex) {
                if (!handler.exception(ex)) {
                    if (result != null)
                        result.exception = ex;
                }
            }
        }
    }

    static class Monitor implements Runnable {
        private Thread thread;
        private int capacity = Runtime.getRuntime().availableProcessors() * 4;
        List<Event> list;
        ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private volatile boolean shutdown = true;

        public boolean isAlive() {
            if (thread == null)
                return false;
            return thread.isAlive();
        }

        public void start() {
            if (!shutdown)
                return;
            shutdown = false;
            list = new ArrayList<>(capacity);
            thread = new Thread(this);
            thread.start();
        }

        public void shutdown() {
            shutdown = true;
            thread.interrupt();
            list.clear();
            thread = null;
        }

        public void add(Event event) {
            add(event, true);
        }

        int binarySearch(List<Event> list, int l, int r, long exp)
        {
            if (r>=l)
            {
                int mid = l + (r - l)/2;

                // If the element is present at the
                // middle itself
                if (list.get(mid).expiration == exp)
                    return mid;

                // If element is smaller than mid, then
                // it can only be present in left subarray
                if (list.get(mid).expiration > exp)
                    return binarySearch(list, l, mid-1, exp);

                // Else the element can only be present
                // in right subarray
                return binarySearch(list, mid+1, r, exp);
            }

            // We reach here when element is not present
            //  in array
            return r;
        }

        private void add(Event event, boolean interrupt) {
            final ReentrantLock lock = this.lock;
            int index = 0;
            lock.lock();
            try {
                int size = list.size();
                if (size > 0) {
                    int start = binarySearch(list, 0, list.size() - 1, event.expiration);
                    if (start < 0)
                        start = 0;
                    else if (start >= size)
                        start = size - 1;
                    Event e = list.get(start);
                    if (e.expiration < event.expiration) {
                        index = start + 1;
                    } else {
                        index = start;
                    }
                }
                list.add(index, event);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
            if (index == 0 && interrupt) {
                thread.interrupt();
            }
        }

        public void run() {
            while (!shutdown) {
                Event event = null;
                try {
                    lock.lock();
                    while (list.size() == 0)
                        notEmpty.await();
                    event = list.remove(0);
                } catch (InterruptedException e) {
                    // ignore
                } finally {
                    lock.unlock();
                }
                if (event != null && !monitor(event)) {
                    add(event, false);
                }
            }
        }

        // returns true if successfully processed, false if interrupted
        private boolean monitor(Event event) {
            Result result = event.result;
            if (result == null)
                return true;

            synchronized (result) {
                boolean firstTime = true;
                while (!result.done) {
                    try {
                        long waitTime;
                        if (firstTime) {
                            waitTime = event.expiration - System.currentTimeMillis() - 1;
                            firstTime = false;
                            if (waitTime == -1)
                                waitTime = 0;
                        } else {
                            waitTime = event.expiration - System.currentTimeMillis();
                        }

                        if (waitTime > 0) {
                            result.wait(waitTime);
                        } else {
                            if (result.thread != null) {
                                event.handler.timeout(result.thread);
                                result.thread = null;
                            }
                            break;
                        }
                    } catch (InterruptedException e) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    static Handler defaultHandler = (TimeoutHandler) (thread) -> thread.interrupt();

    public interface Handler<V>  {
        default boolean exception(Exception exception){ return false; }
        default void callback(V retVal) {}

        // this method is called when timeout happens.  The implementation need to stop the blocking operation
        // For example, if it were a socket connection, the socket needs to be close.  The default
        default void timeout(Thread thread) {
            thread.interrupt();
        }
    }

    @FunctionalInterface
    public interface ExceptionHandler<V> extends Handler<V> {
        boolean exception(Exception exception);
    }

    @FunctionalInterface
    public interface CallbackHandler<V> extends Handler<V> {
        void callback(V retVal);
    }

    @FunctionalInterface
    public interface TimeoutHandler<V> extends Handler<V> {
        void timeout(Thread thread);
    }
}
