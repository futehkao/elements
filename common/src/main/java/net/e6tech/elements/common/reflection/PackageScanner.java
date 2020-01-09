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

package net.e6tech.elements.common.reflection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import net.e6tech.elements.common.util.SystemException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PackageScanner {
    private int concurrencyLevel = 32;
    private int maximumSize = 100;
    private int initialCapacity = 16;
    private Cache<ClassLoader, Cache<String, Class[]>> topLevelRecursive;
    private Cache<ClassLoader, Cache<String, Class[]>> topLevel;
    private Cache<ClassLoader, ClassPath> classPathCache;

    public PackageScanner() {
        reset();
    }

    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    public void setConcurrencyLevel(int concurrencyLevel) {
        this.concurrencyLevel = concurrencyLevel;
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    public int getInitialCapacity() {
        return initialCapacity;
    }

    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public void reset() {
        topLevelRecursive = CacheBuilder.newBuilder()
                .concurrencyLevel(concurrencyLevel) //32 concurrent accessors should be plenty
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .build();
        topLevel = CacheBuilder.newBuilder()
                .concurrencyLevel(concurrencyLevel) //32 concurrent accessors should be plenty
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .build();
        classPathCache = CacheBuilder.newBuilder()
                .concurrencyLevel(concurrencyLevel) //32 concurrent accessors should be plenty
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .build();
    }

    public Class[] getTopLevelClassesRecursive(final ClassLoader classLoader, String packageName) {
        ClassPath classPath;
        try {
            classPath = classPathCache.get(classLoader, () -> ClassPath.from(classLoader));
            Cache<String, Class[]> cache= topLevelRecursive.get(classLoader, () ->
                    CacheBuilder.newBuilder()
                            .concurrencyLevel(concurrencyLevel) //32 concurrent accessors should be plenty
                            .initialCapacity(initialCapacity)
                            .maximumSize(maximumSize)
                            .build());
            return cache.get(packageName, () -> toClasses(classPath.getTopLevelClassesRecursive(packageName)));
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    public Class[] getTopLevelClasses(final ClassLoader classLoader, String packageName) {
        ClassPath classPath;
        try {
            classPath = classPathCache.get(classLoader, () -> ClassPath.from(classLoader));
            Cache<String, Class[]> cache= topLevel.get(classLoader, () ->
                    CacheBuilder.newBuilder()
                            .concurrencyLevel(concurrencyLevel) //32 concurrent accessors should be plenty
                            .initialCapacity(initialCapacity)
                            .maximumSize(maximumSize)
                            .build());
            return cache.get(packageName, () -> toClasses(classPath.getTopLevelClasses(packageName)));
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private static Class[] toClasses(ImmutableSet<ClassPath.ClassInfo> set) {
        List<Class> classes = new ArrayList();
        for (ClassPath.ClassInfo info : set) {
            classes.add(info.load());
        }
        return classes.toArray(new Class[0]);
    }
}

