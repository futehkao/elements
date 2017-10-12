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
package net.e6tech.elements.common.cache;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by futeh.
 */
public class CacheConfiguration {

    public static final long DEFAULT_EXPIRY = 15 * 60 * 1000L;

    private static final String DEFAULT_PROVIDER = "net.e6tech.elements.common.cache.ehcache.EhcacheProvider";
    private static Map<String, CacheManager> managers = Collections.synchronizedMap(new HashMap<>());

    private CacheProvider provider;
    private CacheManager cacheManager;
    private String name;
    private long expiry = DEFAULT_EXPIRY;
    private long maxEntries = 1024L;
    private boolean storeByValue = false;

    public CacheConfiguration() {
    }

    public CacheProvider getProvider() {
        return provider;
    }

    public void setProvider(CacheProvider provider) {
        this.provider = provider;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        if (expiry <= 0)
            throw new IllegalArgumentException();
        this.expiry = expiry;
    }

    public long getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(long maxEntries) {
        this.maxEntries = maxEntries;
    }

    public boolean isStoreByValue() {
        return storeByValue;
    }

    public void setStoreByValue(boolean storeByValue) {
        this.storeByValue = storeByValue;
    }

    public synchronized CacheManager getCacheManager() {
        if (cacheManager != null)
            return cacheManager;

        if (provider == null) {
            try {
                provider = (CacheProvider) getClass().getClassLoader().loadClass(DEFAULT_PROVIDER).newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        cacheManager = managers.computeIfAbsent(provider.getProviderClassName(), key ->  Caching.getCachingProvider(key).getCacheManager());
        return cacheManager;
    }

    public <K, V> Cache<K, V> getCache(String name, Class<K> keyClass, Class<V> valueClass) {
        CacheManager cacheManager = getCacheManager();
        Cache<K, V> cache = cacheManager.getCache(name, keyClass, valueClass);
        if (cache != null)
            return cache;

        try {
            return provider.createCache(this, name, keyClass, valueClass);
        } catch (CacheException ex) {
            cache = cacheManager.getCache(name, keyClass, valueClass);
            if (cache != null)
                return cache;
            else
                throw ex;
        }
    }
}
