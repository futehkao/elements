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

import com.google.inject.TypeLiteral;
import net.e6tech.elements.common.cache.CacheFacade;
import net.e6tech.elements.common.cache.CachePool;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.Test;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.TouchedExpiryPolicy;
import javax.cache.spi.CachingProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void jsr107() {
        CachingProvider provider = Caching.getCachingProvider(EhcacheCachingProvider.class.getName());
        javax.cache.CacheManager cacheManager = provider.getCacheManager();
        MutableConfiguration<Long, String> configuration = new MutableConfiguration<Long, String>();
        configuration.setTypes(Long.class, String.class);
        configuration.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new javax.cache.expiry.Duration(TimeUnit.SECONDS, 10)));
        javax.cache.Cache<Long, String> cache = cacheManager.createCache("someCache", configuration);
        cache.put(1L, "item");
        String value = cache.get(1L);
        System.out.println(value);
        cache.close();
        cacheManager.close();
    }

    @Test
    public void facade() {
        CacheFacade<Long, Map<String, String>> facade = new CacheFacade<Long, Map<String, String>>() {};
        System.out.println(facade.getClass());
        facade.pool = new CachePool();
        facade.pool.initialize(null);

        Map<String, String> value = facade.get(1L, ()-> new HashMap<String, String>());
        System.out.println(value);

        TypeLiteral typeLiteral = TypeLiteral.get(facade.getClass().getGenericSuperclass());
        TypeLiteral typeLiteral2 = new TypeLiteral<CacheFacade<Long, Map<String, String>>>(){};
        System.out.println(typeLiteral);
        System.out.println(typeLiteral2);
    }
}
