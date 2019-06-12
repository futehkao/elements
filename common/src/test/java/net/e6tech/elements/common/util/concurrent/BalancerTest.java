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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
public class BalancerTest {

    volatile int success = 0;

    @Test
    void basic() throws Exception {
        MyBalancer balancer = new MyBalancer();
        balancer.setThreadSafe(true);
        balancer.setTimeout(100L);
        balancer.setRecoveryPeriod(50L);
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.addService(new Service());
        balancer.start();
        int available = balancer.getAvailable();

        int count = 100;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    try {
                        balancer.execute(service -> service.run());
                        success ++;
                    } catch (Throwable th) {
                    }
                }
            });
        }

       long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            threads[i].start();
        }

        for (int i = 0; i < count; i++) {
            threads[i].join();
        }

        System.out.println("elapsed: " + (System.currentTimeMillis() - start) + " success: " + success);

        Thread.sleep(1000L);
        assertTrue(balancer.getAvailable() == available);
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

    public static class Service {
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

        public void start() {
            count = 0;
        }

        public void stop() {
            count = 0;
        }
    }
}
