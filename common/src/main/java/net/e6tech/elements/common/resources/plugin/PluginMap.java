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

package net.e6tech.elements.common.resources.plugin;

import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class PluginMap<K, V> implements PluginFactory {

    private Map<K, Object> map = new LinkedHashMap<>();
    private PluginPath pluginPath;
    private PluginManager pluginManager;

    @Override
    public PluginMap<K, V> create(PluginManager pluginManager) {
        PluginMap<K, V> copy = new PluginMap<>();
        copy.pluginManager = pluginManager;
        copy.map = map;
        return copy;
    }

    @Override
    public void initialize(PluginPath path) {
        pluginPath = path;
    }

    public void put(K key, V singleton) {
        map.put(key, singleton);
    }

    public void put(K key, Class<? extends V> cls) {
        map.put(key, cls);
    }

    public Object remove(K key) {
        return map.remove(key);
    }

    @SuppressWarnings("squid:S3776")
    public Map<K, V> map() {
        return pluginManager.getResources().configurator().computeIfAbsent(pluginPath.path(),
                key -> {
                    Map<K, V> m = new LinkedHashMap<>();
                    for (Map.Entry<K, Object> entry : map.entrySet()) {
                        Object obj = entry.getValue();
                        V value;
                        if (obj instanceof Class) {
                            value = (V) pluginManager.createInstance(pluginPath, (Class) obj);
                        } else {
                            if (obj instanceof Plugin && ((Plugin) obj).isPrototype()) {
                                try {
                                    Plugin plugin = (Plugin) obj.getClass().getDeclaredConstructor().newInstance();
                                    Reflection.copyInstance(plugin, obj);
                                    plugin.initialize(pluginPath);
                                    pluginManager.inject(plugin);
                                    value = (V) plugin;
                                } catch (Exception e) {
                                    throw new SystemException(e);
                                }
                            } else {
                                value = (V) obj;
                            }
                        }
                        m.put(entry.getKey(), value);
                    }
                    return m;
                } );
    }
}
