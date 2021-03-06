/*
 * Copyright 2015-2020 Futeh Kao
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Usages: <br>
 * <p>
 * new ObjectPool&lt;&gt;().type(type).build(); <br>
 * new ObjectPool&lt;&gt;().factory(factory).build(); <br>
 * or new ObjectPool&lt;type&gt;(){}.build();
 * </p>
 */
public class ObjectPool<T> {
    private ConcurrentLinkedDeque<Entry<T>> deque = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<Entry<T>> entries = new ConcurrentLinkedDeque<>();
    private ObjectFactory<T> factory;
    private Class<?> type;
    private int limit = 50;
    private int idleTimeout = 20000;
    private long lastCleanup = 0L;

    public ObjectPool<T> build() {
        if (type == null && factory == null) {
            Type superClass = getClass().getGenericSuperclass();
            if (superClass instanceof Class<?>) { // sanity check, should never happen
                throw new IllegalArgumentException("Internal error: ObjectPool type information.  " +
                        "Try new ObjectPool<>().type(type).build(), " +
                        "new ObjectPool<>().factory(factory).build() " +
                        "or new ObjectPool<type>().build()");
            }
            if (superClass instanceof ParameterizedType) { // sanity check, should never happen
                Type[] types = ((ParameterizedType) superClass).getActualTypeArguments();
                if (types[0] instanceof Class) {
                    type = (Class) types[0];
                } else if (types[0] instanceof ParameterizedType && ((ParameterizedType) types[0]).getRawType() instanceof Class) {
                    type = (Class) ((ParameterizedType) types[0]).getRawType();
                }
            }
        }
        return this;
    }

    public ObjectFactory<T> getFactory() {
        return factory;
    }

    public void setFactory(ObjectFactory<T> factory) {
        this.factory = factory;
    }

    public ObjectPool<T> factory(ObjectFactory<T> factory) {
        setFactory(factory);
        return this;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    public ObjectPool<T> type(Class<?> type) {
        setType(type);
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public ObjectPool<T> limit(int limit) {
        setLimit(limit);
        return this;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public ObjectPool<T> idleTimeout(int idleTimeout) {
        setIdleTimeout(idleTimeout);
        return this;
    }

    public int size() {
        return deque.size();
    }

    public ObjectPool<T> clear() {
        deque.clear();
        entries.clear();
        return this;
    }

    @SuppressWarnings("unchecked")
    public T create() {
        if (factory != null)
            return factory.create();
        if (type != null) {
            try {
                return (T) type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        throw new IllegalArgumentException("Internal error: ObjectPool constructed without actual type information");
    }

    public void accept(Consumer<T> consumer) {
        T t = null;
        try {
            t = checkOut();
            consumer.accept(t);
        } finally {
            if (t != null)
                checkIn(t);
        }
    }

    public <R> R apply(Function<T, R> function) {
        T t = null;
        try {
            t = checkOut();
            return function.apply(t);
        } finally {
            if (t != null)
                checkIn(t);
        }
    }

    public T checkOut() {
        if (deque.isEmpty()) {
            lastCleanup = System.currentTimeMillis();
            return create();
        }
        try {
            Entry<T> entry = deque.removeLast();
            T value = entry.value;
            entry.value = null;
            if (entries.size() < limit)
                entries.offer(entry);
            return value;
        } catch (NoSuchElementException e) {
            return create();
        }
    }

    public void checkIn(T t) {
        if (t == null)
            return;

        if (deque.size() < limit) {
            Entry<T> entry;
            try {
                entry = entries.remove();
            } catch (NoSuchElementException e) {
                entry = new Entry<>();
            }
            entry.value = t;
            entry.lastAccess = System.currentTimeMillis();
            deque.offerLast(entry);
        }

        cleanup();
    }

    public void cleanup() {
        long time = System.currentTimeMillis();
        if (time - lastCleanup > idleTimeout) {
            lastCleanup = time;
            final Iterator<Entry<T>> each = deque.iterator();
            while (each.hasNext()) {
                Entry<T> entry = each.next();
                if (time - entry.lastAccess > idleTimeout) {
                    each.remove();
                } else {
                    break;
                }
            }
        }
    }

    @FunctionalInterface
    public interface ObjectFactory<T> {
        T create();
    }

    private static class Entry<T> {
        long lastAccess;
        T value;
    }

}
