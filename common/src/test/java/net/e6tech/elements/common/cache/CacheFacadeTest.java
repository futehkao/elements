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

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheRuntimeConfiguration;
import org.ehcache.config.ResourceType;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.Ehcache;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.Test;

import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.TouchedExpiryPolicy;
import javax.cache.spi.CachingProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ehcache and CacheFacade.
 * Created by futeh.
 */
public class CacheFacadeTest {
    @Test
    public void basic() {
        CacheManager cacheManager
                = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("preConfigured",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10))
                                .withExpiry(Expirations.timeToLiveExpiration(Duration.of(20, TimeUnit.SECONDS))))
                .build();

        cacheManager.init();

        Cache<Long, String> preConfigured =
                cacheManager.getCache("preConfigured", Long.class, String.class);

        Cache<Long, String> myCache = cacheManager.createCache("myCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)).build());

        myCache.put(1L, "da one!");
        String value = myCache.get(1L);

        cacheManager.removeCache("preConfigured");

        cacheManager.close();
    }

    @Test void expiry() throws Exception {
        CacheFacade<Long, String> facade = new CacheFacade<Long, String>() {}
        .initPool(pool -> {
            pool.setMaxEntries(20);
            pool.setExpiry(100L);
        });

        javax.cache.Cache<Long, String> cache = facade.getCache();

        for (int i = 0; i < 5; i++) {
            facade.put((long)i, Integer.toString(i));
        }

        synchronized (facade) {
            facade.wait(101L);
        }

        for (int i = 5; i < 15; i++) {
            facade.put((long)i, Integer.toString(i));
        }

        assertNull(facade.get((long) 0));
        assertNotNull(facade.get((long) 5));
    }

    @Test
    public void facade() {
        CacheFacade<Long, Map<String, String>> facade = new CacheFacade<Long, Map<String, String>>() {};
        System.out.println(facade.getClass());
        facade.pool = new CachePool();

        Map<String, String> value = facade.get(1L, ()-> new HashMap<String, String>());
        System.out.println(value);
    }

    @Test
    public void shareCache() {
        CacheFacade<String, String> cache1 = new CacheFacade<String, String>("cache") {}.initPool(pool -> pool.setExpiry(5 * 60 * 1000L));
        CacheFacade<String, String> cache2 = new CacheFacade<String, String>("cache") {}.initPool(pool -> pool.setExpiry(5 * 60 * 1000L));
        cache1.put("a", "b");
        String v = cache2.get("a");
        assertTrue(v.equals("b"));
        cache1.remove("a");
        v = cache2.get("a");
        assertTrue(v == null);
    }
}
