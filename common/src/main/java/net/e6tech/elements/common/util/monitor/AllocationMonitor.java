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
package net.e6tech.elements.common.util.monitor;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.*;

/**
 * Created by futeh.
 *
 * Used to monitor objects gc state.
 */
public class AllocationMonitor {

    private ReferenceQueue<Object> phantoms = new ReferenceQueue<>();
    private Thread deallocThread = new Thread();
    private final SortedSet<AllocationReference> allocated;
    private long checkInterval = 1 * 60000L;
    private long expired = 1 * 60000L;
    private boolean disabled = false;

    public AllocationMonitor() {
        Comparator<AllocationReference> comparator = Comparator.comparingLong(l -> l.expiredTime);
        allocated = Collections.synchronizedSortedSet(new TreeSet<>(comparator));
    }

    /**
     * The listener MUST not have any reference to obj.  Pay special attention especially when
     * the listener is in the form of lambda expression.
     *
     * @param timeout  timeout period
     * @param obj the object to be monitor
     * @param listener an AllocationListener
     */
    public void monitorLeak(long timeout, Object obj, LeakListener listener) {
        if (disabled)
            return;
        long realTimeout = timeout;
        if (realTimeout <= 0)
            realTimeout = expired;
        allocated.add(new AllocationReference(realTimeout, obj, phantoms, listener));
        checkGCThread();
    }

    /**
     * The listener MUST not have any reference to obj.  Pay special attention especially when
     * the listener is in the form of lambda expression.
     *
     * @param obj the object to be monitor
     * @param listener an AllocationListener
     */
    public void monitorDealloc(Object obj, DeallocationListener listener) {
        if (disabled)
            return;
        allocated.add(new AllocationReference(0, obj, phantoms, listener));
        checkGCThread();
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    public long getExpired() {
        return expired;
    }

    public void setExpired(long expired) {
        this.expired = expired;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public synchronized void shutdown() {
        if (deallocThread.isAlive()) {
            deallocThread.interrupt();
        }
        allocated.clear();
        phantoms = new ReferenceQueue<>();
    }

    @SuppressWarnings({"squid:S2276", "squid:S3776", "squid:S1188", "squid:S134"}) // we really want the thread to sleep, not wait
    protected synchronized void checkGCThread() {
        if (deallocThread.isAlive())
            return;
        Thread leakThread = new Thread(() -> {
            Object wait = new Object();
            while (true) {
                try {
                    synchronized (allocated) {
                        Iterator<AllocationReference> iterator = allocated.iterator();
                        while (iterator.hasNext()) {
                            AllocationReference alloc = iterator.next();
                            if (System.currentTimeMillis() > alloc.expiredTime) {
                                alloc.getListener().onPotentialLeak();
                                iterator.remove();
                            } else {
                                break;
                            }
                        }
                    }
                    synchronized (wait) {
                        wait.wait(checkInterval);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        deallocThread = new Thread(() -> {
            try {
                while (true) {
                    Reference<?> ref = phantoms.remove();
                    allocated.remove(ref);
                    if (ref instanceof AllocationReference) {
                        AllocationReference alloc = (AllocationReference) ref;
                        alloc.listener.onDeallocated();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                leakThread.interrupt();
            }
        });
        leakThread.setDaemon(true);
        leakThread.start();

        deallocThread.setDaemon(true);
        deallocThread.start();
    }

    static class AllocationReference extends PhantomReference<Object> {
        AllocationListener listener;
        long startTime;
        long expiredTime;

        @SuppressWarnings("unchecked")
        public AllocationReference(long timeout, Object referent, ReferenceQueue q, AllocationListener listener) {
            super(referent, q);
            if (timeout == 0)
                expiredTime = Long.MAX_VALUE;
            else
                expiredTime = System.currentTimeMillis() + timeout;
            this.listener = listener;
        }

        public AllocationListener getListener() {
            return listener;
        }

        public long getStartTime() {
            return startTime;
        }

        @Override
        public boolean equals(Object object) {
            return this == object;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

    }
}
