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

import net.e6tech.elements.common.Tags;
import net.e6tech.elements.common.util.SystemException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("all")
@Tags.Common
class BalancerTest {

    @Test
    void basic() throws Exception {
        MyBalancer balancer = new MyBalancer();
        balancer.setThreadSafe(true);
        balancer.setTimeout(1000L);
        balancer.setRecoveryPeriod(50L);
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.start();
        int available = balancer.getAvailable();

        AtomicInteger success = new AtomicInteger(0);
        Runnable runnable = () -> {
            try {
                balancer.execute(service -> service.run());
                success.incrementAndGet();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        };

        int count = 100;
        Thread[] threads = createThreads(count, runnable, 10);

        long start = System.currentTimeMillis();
        runThreads(threads);

        long duration = System.currentTimeMillis() - start;
        Thread.sleep(2000L);
        System.out.println("threadSafe=true elapsed: " + duration + " success: " + success.get());
        assertTrue(balancer.getAvailable() == available);
        assertTrue(balancer.getProcessingCount() == 0);

        // run it with threadSafe set to false
        balancer.setThreadSafe(false);
        balancer.setTimeout(5000L);  // timeout needs to be longer for non-threadSafe services because we cannot reuse
                                     // a service that is in the middle of processing.
        success.set(0);
        threads = createThreads(count, runnable, 10);
        start = System.currentTimeMillis();
        runThreads(threads);

        duration = System.currentTimeMillis() - start;
        Thread.sleep(2000L);
        System.out.println("threadSafe=false elapsed: " + duration + " success: " + success.get());
        assertTrue(balancer.getAvailable() == available);
        assertTrue(balancer.getProcessingCount() == 0);
    }

    private Thread[] createThreads(int count, Runnable runnable, int iteration) {
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iteration; j++) {
                    runnable.run();
                }
            });
        }
        return threads;
    }

    private void runThreads(Thread[] threads) throws InterruptedException {
        int count = threads.length;
        for (int i = 0; i < count; i++) {
            threads[i].start();
        }

        for (int i = 0; i < count; i++) {
            threads[i].join();
        }
    }

    @Test
    void exception() throws Exception {
        MyBalancer balancer = new MyBalancer();
        balancer.setTimeout(100L);
        balancer.setRecoveryPeriod(100L);
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.addService(new ServiceImpl());
        balancer.start();
        int available = balancer.getAvailable();

        int count = 100;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            int k = i;
            threads[i] = new Thread(() -> {
                if (k % 5 == 0) {
                    assertThrows(Exception.class, () ->
                            balancer.getService().puke());
                } else {
                    for (int j = 0; j < 10; j++) {
                        try {
                            balancer.execute(service -> service.run());
                        } catch (Throwable th) {
                        }
                    }
                }
            });
        }
        runThreads(threads);

        Thread.sleep(1000L);
        assertEquals(available, balancer.getAvailable());
        assertTrue(balancer.getProcessingCount() == 0);
    }

    public static class MyBalancer extends Balancer<Service> {

        @Override
        protected void start(Service service) throws IOException {
            service.start();
        }

        @Override
        protected void stop(Service service) throws IOException {
            service.stop();
        }
    }

    public static interface Service {
        void start();
        void stop();
        int run() throws IOException;
        void puke();
    }

    public static class ServiceImpl implements Service {
        volatile int count = 0;

        public int run() throws IOException {
            count ++;
            if (count % 25 == 0) {
                throw new IOException();
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {

            }
            return count;
        }

        public void puke() {
            throw new SystemException("intentional");
        }

        public void start() {
            count = 0;
        }

        public void stop() {
            count = 0;
        }
    }
}
