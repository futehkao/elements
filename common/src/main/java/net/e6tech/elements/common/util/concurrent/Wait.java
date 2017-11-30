/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.common.util.concurrent;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S2276","squid:S134", "squid:S1188", "squid:S1066", "squid:S2864", "squid:S1149"})
public class Wait<K, V> {
    Hashtable<K, Entry<V>> table = new Hashtable<>();
    Thread thread;

    public void offer(K key, V value) {
        Entry<V> entry = table.get(key);
        if (entry != null) {
            entry.queue.offer(value);
        }
    }

    public void newEntry(K key, long expired) {
        newEntry(key, null, expired);
    }

    public void extendExpiration(K key, long time) {
        Entry<V> entry = table.get(key);
        if (entry != null) {
            entry.expired += time;
        }
    }

    @SuppressWarnings("squid:S3776")
    public void newEntry(K key, Object userData, long expired) {
        Entry<V> entry = new Entry<>();
        entry.userData = userData;
        entry.expired = expired;
        synchronized (table) {
            table.put(key, entry);
            table.notifyAll();
        }

        synchronized (table) {
            if (thread == null) {
                // start cleanup thread
                thread = new Thread(()->{
                    List<K> list = new LinkedList<>();
                    while (true) {
                        long waitTime = 0;
                        list.clear();
                        synchronized (table) {
                            for (K k : table.keySet()) {
                                Entry<V> e = table.get(k);
                                if (e != null) { // can be checkout by a different thread
                                    if (e.start + e.expired < System.currentTimeMillis()) {
                                        list.add(k);
                                    } else {
                                        long exp = e.start + e.expired - System.currentTimeMillis();
                                        if (exp > 0) {
                                            if (waitTime == 0 || waitTime > exp) waitTime = exp;
                                        }
                                    }
                                }
                            }
                        }

                        for (K k : list) {
                            Entry<V> e = table.get(k);
                            // we check for e != null because some other thread can remove it.
                            if (e != null && e.start + e.expired < System.currentTimeMillis()) {
                                table.remove(k);
                            }
                        }

                        if (waitTime > 0) {
                            try {
                                Thread.sleep(waitTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        synchronized (table) {
                            while (table.size() == 0) {
                                try {
                                    table.wait();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                });
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    public V remove(K key) {
        Entry<V> entry = table.get(key);
        if (entry == null)
            return null;
        table.remove(key);
        return entry.queue.peek();
    }

    public V poll(K key, long timeout) {
        Entry<V> entry = table.get(key);
        if (entry == null)
            return null;
        try {
            return  entry.queue.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            remove(key);
        }
    }

    public V peek(K key) {
        Entry<V> entry = table.get(key);
        if (entry == null)
            return null;
        return entry.queue.peek();
    }

    public <T> T peekUserData(K key) {
        Entry<V> entry = table.get(key);
        if (entry == null)
            return null;
        return (T) entry.userData;
    }

    private static class Entry<V> {
        long start = System.currentTimeMillis();
        long expired;
        BlockingQueue<V> queue = new LinkedBlockingQueue<>(1);
        Object userData;
    }


}
