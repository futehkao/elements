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

import java.util.*;

public class ThreadLocalMap<K, V> implements Map<K, V> {
    private final ThreadLocal<Map> threadLocal = new ThreadLocal<>();

    private final ThreadLocal<Object> lastUpdate = new ThreadLocal<>();

    private Map<K, V> map;

    private volatile Object dirty = new Object();

    public ThreadLocalMap() {
        map = new LinkedHashMap<>();
        threadLocal.set(map);
        lastUpdate.set(dirty);
    }

    public ThreadLocalMap(int initialCapacity) {
        map = new LinkedHashMap<>(initialCapacity);
        threadLocal.set(map);
        lastUpdate.set(dirty);
    }

    public ThreadLocalMap(Map<K, V> map) {
        this.map = map;
        threadLocal.set(map);
        lastUpdate.set(dirty);
    }

    public Object lastUpdate() {
        return lastUpdate.get();
    }

    Map<K, V> merged(Object dirt) {
        Map<K, V> local = threadLocal.get();
        if (local == map) {
            lastUpdate.set(dirt);
            return map;
        }

        if (dirt == lastUpdate.get() && local != null ) {
            return local;
        }

        Map<K, V> combined = new LinkedHashMap<>(2 * map.size() + 1);
        combined.putAll(map);
        if (local != null)
            combined.putAll(local);
        lastUpdate.set(dirt);
        threadLocal.set(combined);
        return combined;
    }

    public synchronized Map<K, V> mapThreadLocal() {
        if (threadLocal.get() == null)
            threadLocal.set(new LinkedHashMap<>());
        return threadLocal.get();
    }

    public boolean isDirty() {
        return dirty != lastUpdate.get();
    }

    @Override
    public synchronized int size() {
        return merged(dirty).size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return merged(dirty).isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        Map<K, V> m = threadLocal.get();
        if (m != null && m.containsKey(key)) {
            return true;
        } else {
            synchronized (this) {
                return map.containsKey(key);
            }
        }
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        return merged(dirty).containsValue(value);
    }

    @Override
    public V get(Object key) {
        Map<K, V> m = threadLocal.get();
        if (m != null && m.containsKey(key)) {
            return m.get(key);
        } else {
            synchronized (this) {
                return map.get(key);
            }
        }
    }

    @Override
    public V put(K key, V value) {
        V old;
        Map<K, V> m = threadLocal.get();
        if (m == map)
            return syncPut(key, value);

        if (m != null) {
            if (m.containsKey(key)) {
                old = m.put(key, value);
                syncPut(key, value);
            } else {
                m.put(key, value);
                old = syncPut(key, value);
            }
        } else {
            m = new LinkedHashMap<>();
            threadLocal.set(m);
            m.put(key, value);
            old = syncPut(key, value);
        }
        return old;
    }

    private synchronized V syncPut(K key, V value) {
        boolean merged = dirty == lastUpdate.get();
        dirty = new Object();
        if (merged) {
            lastUpdate.set(dirty);  // current thread is updated to up to date.  However, other threads are not
        }

        return map.put(key, value);
    }

    public V putThreadLocal(K key, V value) {
        V old;
        Map<K, V> m = threadLocal.get();
        if (m != null) {
            old = m.put(key, value);
        } else {
            m = new LinkedHashMap<>();
            threadLocal.set(m);
            old = m.put(key, value);
        }
        return old;
    }

    @Override
    public synchronized V remove(Object key) {
        dirty = new Object();
        Map<K, V> m = threadLocal.get();
        if (m == map)
            return m.remove(key);

        Map<K, V> local = threadLocal.get();
        synchronized (this) {
            if (local != null) {
                map.remove(key);
                return local.remove(key);
            } else {
                return map.remove(key);
            }
        }
    }

    public V removeThreadLocal(K key) {
        Map<K, V> m = threadLocal.get();
        if (m != null) {
            return m.remove(key);
        } else {
            return null;
        }
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> m) {
        dirty = new Object();
        Map<K, V> local = threadLocal.get();
        if (local == map) {
            local.putAll(m);
            return;
        }

        if (local != null)
            local.putAll(m);
        map.putAll(m);
    }

    @Override
    public synchronized void clear() {
        dirty = new Object();
        Map<K, V> local = threadLocal.get();
        if (local != map) {
            threadLocal.remove();
        }
        lastUpdate.set(dirty);
        map.clear();
    }

    @Override
    public synchronized Set<K> keySet() {
        return merged(dirty).keySet();
    }

    @Override
    public synchronized Collection<V> values() {
        return merged(dirty).values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Map<K, V> m = threadLocal.get();
        if (m == map) {
           return map.entrySet();
        }

        Map<K, TLEntry<K, V>> entries = new LinkedHashMap<>(2 * map.size() + 1);
        if (m != null) {
            for (Entry<K, V> e : m.entrySet())
                entries.put(e.getKey(), new TLEntry(e, null));
        }

        synchronized (this) {
            for (Entry<K, V> e : map.entrySet()) {
                TLEntry<K, V> entry = entries.get(e.getKey());
                if (entry != null)
                    entry.global = e;
                else
                    entries.put(e.getKey(), new TLEntry(null, e));
            }
        }
        return new HashSet<>(entries.values());
    }

    public synchronized void clearThreadLocal() {
        dirty = new Object();
        Map<K, V> local = threadLocal.get();
        if (local == map) {
            clear();
            return;
        }

        threadLocal.remove();
        lastUpdate.set(dirty);
    }

    class TLEntry<K, V> implements Entry<K, V> {
        Entry<K, V> local;
        Entry<K, V> global;

        TLEntry(Entry<K, V> local, Entry<K, V> global) {
            this.local = local;
            this.global = global;
        }

        @Override
        public K getKey() {
            if (local != null)
                return local.getKey();
            return global.getKey();
        }

        @Override
        public V getValue() {
            if (local != null)
                return local.getValue();
            return global.getValue();
        }

        @Override
        public V setValue(V value) {
            dirty = new Object();
            V old = null;
            if (local != null) {
                old = local.setValue(value);
                if (global != null)
                    global.setValue(value);
            } else if (global != null) {
                old = global.setValue(value);
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (local != null) {
                return local.equals(o);
            } else if (global != null) {
                return global.equals(o);
            }
            return false;
        }

        @Override
        public int hashCode() {
            if (local != null) {
                return local.hashCode();
            } else if (global != null) {
                return global.hashCode();
            }
            return 0;
        }
    }
}
