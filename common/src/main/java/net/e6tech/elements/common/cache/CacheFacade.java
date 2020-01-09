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

package net.e6tech.elements.common.cache;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import javax.cache.Cache;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * This class should be instantiated as an anonymous class.  For example,
 * {@code new CacheFacade&lt;K,V&gt;("name") {}}
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public abstract class CacheFacade<K, V> {
    protected CacheConfiguration configuration;
    protected String name;
    protected Class keyClass;
    protected Class valueClass;
    Cache<K, V> cache;

    public CacheFacade() {
        this(Reflection.getCallingClass(), null);
    }

    public CacheFacade(String name) {
        this(Reflection.getCallingClass(), name);
    }

    public CacheFacade(Class cls, String name) {
        Type genericSuper = getClass().getGenericSuperclass();
        if (genericSuper instanceof ParameterizedType) {
            ParameterizedType parametrizedType = (ParameterizedType) genericSuper;
            Type type = parametrizedType.getActualTypeArguments()[0];
            if (type instanceof Class) {
                keyClass = (Class) type;
            } else if (type instanceof ParameterizedType) {
                keyClass = (Class) ((ParameterizedType) type).getRawType();
            }

            type = parametrizedType.getActualTypeArguments()[1];
            if (type instanceof Class) {
                valueClass = (Class) type;
            } else if (type instanceof ParameterizedType) {
                valueClass = (Class) ((ParameterizedType) type).getRawType();
            }
        }

        String clsName = cls.getName();
        int last = clsName.lastIndexOf('$');
        if (last > 0) {
            clsName = clsName.substring(0, last);
        }

        if (name != null)
            this.name = clsName + "." + name;
        else
            this.name = clsName;
    }

    public CacheFacade<K,V> initPool(Consumer<CacheConfiguration> configurator) {
        if (configuration == null) {
            configuration = new CacheConfiguration();
            configurator.accept(configuration);
        }
        return this;
    }

    public CacheFacade<K,V> initPool() {
        return initPool(p -> p.setExpiry(CacheConfiguration.DEFAULT_EXPIRY));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CacheConfiguration getCacheConfiguration() {
        return configuration;
    }

    @Inject(optional = true)
    public void setCacheConfiguration(CacheConfiguration configuration) {
        this.configuration = configuration;
    }

    public V get(K key) {
        return getCache().get(key);
    }

    public V get(K key, Callable<V> callable) {
        Cache<K,V> c = getCache();
        V value = c.get(key);
        if (value == null) {
            try {
                value = callable.call();
                if (value != null)
                    c.put(key, value);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return value;
    }

    public boolean remove(K key) {
        Cache<K,V> c = getCache();
        return c.remove(key);
    }

    public void put(K key, V value) {
        getCache().put(key, value);
    }

    protected synchronized Cache<K,V> getCache() {
        if (cache != null)
            return cache;
        if (configuration == null) {
            initPool();
        }
        cache = configuration.getCache(name, keyClass, valueClass);
        return cache;
    }
}
