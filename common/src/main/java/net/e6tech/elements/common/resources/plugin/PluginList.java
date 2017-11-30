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

import net.e6tech.elements.common.resources.Resources;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by futeh.
 */
public class PluginList<T> implements PluginFactory {

    private List list = new ArrayList<>();
    private PluginPath<T> pluginPath;
    private Resources resources;

    @Override
    public PluginList<T> create(Resources resources) {
        PluginList<T> copy = new PluginList<>();
        copy.resources = resources;
        copy.list = list;
        return copy;
    }

    @Override
    public void initialize(PluginPath path) {
        pluginPath = path;
    }

    protected void add(T singleton) {
        list.add(singleton);
    }

    protected void add(Class<? extends T> cls) {
        list.add(cls);
    }

    public List<T> list() {
        return resources.configurator().computeIfAbsent(pluginPath.path(),
                key -> {
                    List<T> l = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof Class) {
                            l.add((T) resources.newInstance((Class) obj));
                        } else {
                            l.add((T) obj);
                        }
                    }
                    return l;
                } );
    }
}
