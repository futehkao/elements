/*
 * Copyright 2015 Futeh Kao
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
package net.e6tech.elements.common.util.monitor;

import net.e6tech.elements.common.inject.Inject;

import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S134", "squid:S135", "squid:S3776"})
public class TimeoutMonitor {

    long timeout = -1; // means disable, 0 means use default

    @Inject(optional = true)
    ExecutorService threadPool;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void monitor(TimeoutListener listener) {
        if (listener.getTimeout() < 0)
            return;

        long initialTimeout = (listener.getTimeout() == 0) ? timeout : listener.getTimeout();
        if (initialTimeout > 0) {
            Monitor monitor = new Monitor(listener);
            if (threadPool != null)
                threadPool.execute(monitor);
            else {
                Thread thread = new Thread(monitor);
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    protected boolean rollback(TimeoutListener listener) {
        if (listener.isOpen()) {
            listener.onTimeout();
            return true;
        }
        return false;
    }

    class Monitor implements Runnable {
        TimeoutListener listener;

        Monitor(TimeoutListener listener) {
            this.listener = listener;
        }

        public void run() {
            long start = System.currentTimeMillis();
            long sleep = 100;
            while (sleep >= 0) {
                long t = (listener.getTimeout() == 0) ? timeout : listener.getTimeout();
                if (!listener.isOpen())
                    break;
                if (System.currentTimeMillis() - start > t) {
                    if (rollback(listener))
                        break;
                } else {
                    if (t - (System.currentTimeMillis() - start) < 100)
                        sleep = t - (System.currentTimeMillis() - start);
                    if (sleep < 100) {
                        sleep = t - (System.currentTimeMillis() - start) - 10;
                        if (sleep < 10) {
                            sleep = t - (System.currentTimeMillis() - start) - 1;
                        }
                    }
                    try {
                        if (sleep >= 0)
                            Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            rollback(listener);
        }
    }
}
