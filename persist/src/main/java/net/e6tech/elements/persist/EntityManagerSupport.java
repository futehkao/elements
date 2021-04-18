/*
 * Copyright 2015-2021 Futeh Kao
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

package net.e6tech.elements.persist;

import net.e6tech.elements.common.resources.Resources;

import java.util.Map;

public interface EntityManagerSupport {
    Resources getResources();

    String getAlias();

    EntityManagerProvider getProvider();

    EntityManagerConfig getConfig();

    Map<String, Object> getContext();

    Object get(String key);

    EntityManagerExtension put(String key, Object value);

    EntityManagerExtension remove(String key);

    EntityManagerExtension lockTimeout(long millis);

    long lockTimeout();

    Object runExtension(String extension, Object ... args);
}
