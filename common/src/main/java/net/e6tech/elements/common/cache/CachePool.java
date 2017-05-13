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

import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.TouchedExpiryPolicy;
import javax.cache.spi.CachingProvider;
import java.util.concurrent.TimeUnit;

/**
 * Created by futeh.
 */
public class CachePool implements Initializable {

    public static final long defaultExpiry = 15 * 60 * 1000L;

    protected String provider = "org.ehcache.jsr107.EhcacheCachingProvider";
    protected CacheManager cacheManager;
    protected long expiry = defaultExpiry;
    protected boolean storeByValue = false;

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public boolean isStoreByValue() {
        return storeByValue;
    }

    public void setStoreByValue(boolean storeByValue) {
        this.storeByValue = storeByValue;
    }

    @Override
    public void initialize(Resources resources) {
        CachingProvider cachingProvider = Caching.getCachingProvider(provider);
        cacheManager = cachingProvider.getCacheManager();
    }

    public <K,V> Cache<K,V> getCache(String name, Class<K> keyClass, Class<V> valueClass) {
        if (cacheManager == null) {
            initialize(null);
        }

        Cache<K, V> cache = cacheManager.getCache(name, keyClass, valueClass);
        if (cache != null) return cache;
        MutableConfiguration<K, V> configuration = new MutableConfiguration<>();
        configuration.setTypes(keyClass, valueClass);
        configuration.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, expiry)));
        // configuration.setReadThrough(true);
        // configuration.setWriteThrough(true);
        configuration.setStoreByValue(storeByValue);
        try {
            return cacheManager.createCache(name, configuration);
        } catch (CacheException ex) {
            return cacheManager.getCache(name, keyClass, valueClass);
        }
    }

}
