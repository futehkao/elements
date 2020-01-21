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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class PluginList<T> implements PluginFactory {

    private List list = new ArrayList<>();
    private PluginPath<T> pluginPath;
    private PluginManager pluginManager;

    @Override
    public PluginList<T> create(PluginManager pluginManager) {
        PluginList<T> copy = null;
        try {
            copy = getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
        copy.pluginManager = pluginManager;
        copy.list = list;
        return copy;
    }

    @Override
    public void initialize(PluginPath path) {
        pluginPath = path;
    }

    public void add(T singleton) {
        list.add(singleton);
    }

    public void add(Class<? extends T> cls) {
        list.add(cls);
    }

    public void remove(Predicate predicate) {
        Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            if (predicate.test(iterator.next())) {
                iterator.remove();
            }
        }
    }

    public List plugins() {
        return list;
    }

    @SuppressWarnings("squid:S3776")
    public List<T> list() {
        return pluginManager.getResources().configurator().computeIfAbsent(pluginPath.path(),
                key -> {
                    List<T> l = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof Class) {
                            l.add(pluginManager.createInstance(pluginPath, (Class<T>) obj));
                        } else {
                            if (obj instanceof Plugin && ((Plugin) obj).isPrototype()) {
                                try {
                                    Plugin plugin = (Plugin) obj.getClass().getDeclaredConstructor().newInstance();
                                    Reflection.copyInstance(plugin, obj);
                                    plugin.initialize(pluginPath);
                                    pluginManager.inject(plugin);
                                    l.add((T) plugin);
                                } catch (Exception e) {
                                    throw new SystemException(e);
                                }
                            } else {
                                l.add((T) obj);
                            }
                        }
                    }
                    return l;
                } );
    }
}
