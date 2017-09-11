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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by futeh.
 */
public class CachePool<K, V>{

    public static final long DEFAULT_EXPIRY = 15 * 60 * 1000L;

    private static final String DEFAULT_PROVIDER = "net.e6tech.elements.common.cache.ehcache.EhcacheProvider";
    private static Map<String, CacheManager> managers = new HashMap<>();

    private CacheProvider provider;
    private String name;
    private Class keyClass;
    private Class valueClass;
    private long expiry = DEFAULT_EXPIRY;
    private long maxEntries = 1024L;
    private boolean storeByValue = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class getKeyClass() {
        return keyClass;
    }

    public void setKeyClass(Class keyClass) {
        this.keyClass = keyClass;
    }

    public Class getValueClass() {
        return valueClass;
    }

    public void setValueClass(Class valueClass) {
        this.valueClass = valueClass;
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
        if (provider == null) {
            try {
                provider = (CacheProvider) getClass().getClassLoader().loadClass(DEFAULT_PROVIDER).newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return managers.computeIfAbsent(provider.getProviderClassName(), key -> Caching.getCachingProvider(key).getCacheManager());
    }

    public Cache<K,V> getCache() {
        CacheManager cacheManager = getCacheManager();
        Cache<K, V> cache = cacheManager.getCache(name, keyClass, valueClass);
        if (cache != null)
            return cache;
        return provider.createCache(this);
    }

    private <K,V> Cache<K,V> buildJsr107Cache() {
        CacheManager cacheManager = getCacheManager();
        MutableConfiguration<K, V> configuration = new MutableConfiguration<>();
        configuration.setTypes(keyClass, valueClass);
        configuration.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, expiry)));
        configuration.setStoreByValue(storeByValue);
        try {
            return cacheManager.createCache(name, configuration);
        } catch (CacheException ex) {
            Logger.suppress(ex);
            return cacheManager.getCache(name, keyClass, valueClass);
        }
    }


}
