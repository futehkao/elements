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

package net.e6tech.elements.common.resources.plugin;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by futeh.
 */
public class PluginPath<T> {
    PluginPath parent;
    private Class<T> type;
    private String name;

    protected PluginPath(Class<T> cls, String name) {
        this.type = cls;
        this.name = name;
    }

    public static <T> PluginPath<T> of(Class<T> cls, String name) {
        return new PluginPath<>(cls, name);
    }

    public Class<T> getType() {
        return type;
    }

    public void setType(Class<T> type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public <R> PluginPath<R> and(Class<R> cls, String name) {
        PluginPath<R> child = new PluginPath<>(cls, name);
        child.parent = this;
        return child;
    }

    public <R> PluginPath<R> and(Class<R> cls) {
        PluginPath<R> child = new PluginPath<>(cls, null);
        child.parent = this;
        return child;
    }

    public List<PluginPath> list() {
        LinkedList list = new LinkedList();
        PluginPath path = this;
        while (path != null) {
            list.addFirst(path);
            path = path.parent;
        }
        return list;
    }

    public String path() {
        StringBuilder builder = new StringBuilder();
        List<PluginPath> list = list();
        boolean first = true;
        for (PluginPath p : list) {
            if (first) {
                first  = false;
            } else {
                builder.append("/");
            }
            builder.append(p.getType().getName());
            if (p.getName() != null) {
                builder.append("/").append(p.getName());
            }
        }
        return builder.toString();
    }

}
