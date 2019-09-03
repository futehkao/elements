/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.cache.ehcache;

import net.e6tech.elements.common.cache.CacheConfiguration;
import net.e6tech.elements.common.cache.CacheProvider;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;

import javax.cache.Cache;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by futeh.
 */
public class EhcacheProvider implements CacheProvider {

    private static ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "EhcacheProviderCleaner"));

    @Override
    public String getProviderClassName() {
        return EhcacheCachingProvider.class.getName();
    }

    public <K,V> Cache<K,V> createCache(CacheConfiguration cachePool, String poolName, Class<K> keyClass, Class<V> valueClass) {

        ResourcePoolsBuilder builder = (cachePool.getMaxEntries() > 0)
                ? ResourcePoolsBuilder.heap(cachePool.getMaxEntries())
                : ResourcePoolsBuilder.newResourcePoolsBuilder();

        ExpiryPolicy policy = ExpiryPolicyBuilder.timeToLiveExpiration(java.time.Duration.ofMillis(cachePool.getExpiry()));
        org.ehcache.config.CacheConfiguration cacheConfiguration = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(keyClass, valueClass, builder)
                .withExpiry(policy)
                .build();

        Cache<K, V> cache = cachePool.getCacheManager().createCache(poolName, Eh107Configuration.fromEhcacheCacheConfiguration(cacheConfiguration));
        return (Cache<K, V>) Proxy.newProxyInstance(Cache.class.getClassLoader(), new Class[] { Cache.class}, new CacheInvocationHandler<K,V>(cache, cachePool));
    }

    private class CacheInvocationHandler<K, V> implements InvocationHandler {
        Cache<K, V> cache;
        long lastPut;
        long expiry;

        CacheInvocationHandler(Cache<K, V> cache, CacheConfiguration cachePool) {
            this.cache = cache;
            this.expiry = cachePool.getExpiry();
            this.lastPut = System.currentTimeMillis();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().startsWith("put")
                    && System.currentTimeMillis() - lastPut > expiry) {
                executorService.submit(() ->
                    cache.iterator().forEachRemaining(entry -> {
                        // expire entries by accessing
                    })
                );
                lastPut = System.currentTimeMillis();
            }
            return method.invoke(cache, args);
        }
    }
}
