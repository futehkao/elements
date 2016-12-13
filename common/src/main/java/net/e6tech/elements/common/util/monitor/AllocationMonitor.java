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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by futeh.
 *
 * Used to monitor objects that should have a short life time.
 */
public class AllocationMonitor {

    private ReferenceQueue<Object> phantoms = new ReferenceQueue<>();
    private Thread gcThread = new Thread();
    Set<AllocationReference> allocated = Collections.synchronizedSet(new LinkedHashSet<>());
    private long checkInterval = 1 * 60000;
    private long expired = 1 * 60000;
    private boolean disabled = false;

    /**
     * The listener MUST not have any reference to obj.  Pay special attention especially when
     * the listener is in the form of lambda expression.
     *
     * @param timeout  timeout period
     * @param obj the object to be monitor
     * @param listener an AllocationListener
     */
    public void monitor(long timeout, Object obj, AllocationListener listener) {
        if (disabled) return;
        if (timeout <= 0) timeout = expired;
        allocated.add(new AllocationReference(timeout, obj, phantoms, listener));
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

    protected synchronized void checkGCThread() {
        if (gcThread.isAlive()) return;
        gcThread = new Thread(() -> {
            try {
                Thread.sleep(checkInterval);
                System.gc();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                try {
                    Reference ref = phantoms.poll();
                    while (ref != null) {
                        ref = phantoms.remove();
                        allocated.remove(ref);
                        ref = phantoms.poll();
                    }

                    synchronized (allocated) {
                        Iterator<AllocationReference> iterator = allocated.iterator();
                        while (iterator.hasNext()) {
                            AllocationReference alloc = iterator.next();
                            if (System.currentTimeMillis() > alloc.expiredTime) {
                                alloc.getListener().onPotentialLeak();
                                iterator.remove();
                            }
                        }
                    }
                    Thread.sleep(checkInterval);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        gcThread.setDaemon(true);
        gcThread.start();
    }

    static class AllocationReference extends PhantomReference {
        AllocationListener listener;
        long startTime;
        long expiredTime;

        public AllocationReference(long timeout, Object referent, ReferenceQueue q, AllocationListener listener) {
            super(referent, q);
            expiredTime = System.currentTimeMillis() + timeout;
            this.listener = listener;
        }

        public AllocationListener getListener() {
            return listener;
        }

        public long getStartTime() {
            return startTime;
        }

        public boolean equals(Object object) {
            if (!(object instanceof  AllocationReference)) return false;
            return System.identityHashCode(this) == System.identityHashCode(object);
        }

        public int hashCode() {
            return System.identityHashCode(this);
        }

    }
}
