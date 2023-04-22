/*
 * Copyright 2015-2019 Futeh Kao
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
import java.util.Objects;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class PluginPath<T> {
    PluginPath parent;
    private Class<T> type;
    private String name;
    private String toString;
    private int hash = 0;
    private LinkedList<PluginPath> path;

    protected PluginPath(Class<T> cls, String name) {
        this.type = cls;
        this.name = name;
    }

    public static <T> PluginPath<T> of(Class<T> cls) {
        return new PluginPath<>(cls, null);
    }

    public static <T> PluginPath<T> of(Class<T> cls, String name) {
        return new PluginPath<>(cls, name);
    }

    public static PluginPath from(ClassLoader loader, String str) throws ClassNotFoundException {
        String[] strings = str.split("/");
        PluginPath<?> path = null;
        for (int i = 0; i < strings.length; i += 2) {
            String name = null;
            if (i + 1 < strings.length)
                name = strings[i + 1];
            if (path == null)
                path = PluginPath.of(loader.loadClass(strings[i]), name);
            else
                path = path.and(loader.loadClass(strings[i]), name);
        }
        return path;
    }

    public static PluginPath from(String str) throws ClassNotFoundException {
        return from(PluginPath.class.getClassLoader(), str);
    }

    public PluginPath parent() {
        return parent;
    }

    public Class<T> getType() {
        return type;
    }

    public void setType(Class<T> type) {
        this.type = type;
        toString = null;
        hash = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        toString = null;
        hash = 0;
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

    public <R> PluginPath<R> concat(PluginPath<R> rear) {
        List<PluginPath> list = rear.list();
        PluginPath<?> node = copy();
        for (PluginPath<?> p : list)
            node = node.and(p.getType(), p.getName());

        return (PluginPath<R>) node;
    }

    public PluginPath<T> copy() {
        List<PluginPath> list = list();
        PluginPath<?> node = null;

        for (PluginPath<?> p : list) {
            if (node == null)
                node = new PluginPath(p.getType(), p.getName());
            else
                node = node.and(p.getType(), p.getName());
        }
        return (PluginPath<T>) node;
    }

    public synchronized List<PluginPath> list() {
        if (path != null)
            return path;
        path = new LinkedList<>();
        PluginPath<T> p = this;
        while (p != null) {
            path.addFirst(p);
            p = p.parent;
        }
        return path;
    }

    public <P extends Plugin> PluginPath<P> changeRoot(Class rootClass, String rootName) {
        List<PluginPath> list = list();
        PluginPath newPath = PluginPath.of(rootClass, rootName);
        for (int i = 1; i < list.size(); i++) {
            PluginPath p = list.get(i);
            newPath = newPath.and(p.getType(), p.getName());
        }
        return newPath;
    }

    public <R> PluginPath<R> trimRoot() {
        List<PluginPath> list = list();
        PluginPath<?> sub = null;
        for (int i = 1; i < list.size(); i++) {
            PluginPath<?> p = list.get(i);
            if (sub == null)
                sub = PluginPath.of(p.getType(), p.getName());
            else
                sub = sub.and(p.getType(), p.getName());
        }
        return (PluginPath<R>) sub;
    }

    public String path() {
        if (toString != null)
            return toString;

        StringBuilder builder = new StringBuilder();
        List<PluginPath> list = list();
        boolean first = true;
        for (PluginPath<?> p : list) {
            if (first) {
                first = false;
            } else {
                builder.append("/");
            }
            builder.append(p.getType().getName());
            if (p.getName() != null) {
                builder.append("/").append(p.getName());
            }
        }
        toString = builder.toString();
        return toString;
    }

    public String toString() {
        return path();
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            List<PluginPath> list = list();
            int result = 1;
            for (PluginPath<?> p : list)
                result = 31 * result + (p == null ? 0 : Objects.hash(p.type, name));
            hash = result;
        }
        return hash;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof PluginPath))
            return false;
        PluginPath<?> p = (PluginPath) object;
        List<PluginPath> l1 = list();
        List<PluginPath> l2 = p.list();
        if (l1.size() == l2.size()) {
            for (int i = 0; i < l1.size(); i++) {
                PluginPath<?> p1 = l1.get(i);
                PluginPath<?> p2 = l2.get(i);
                if (p1 != null && p2 != null) {
                    if (!(Objects.equals(p1.name, p2.name) && Objects.equals(p1.type, p2.type)))
                        return false;
                } else if (p1 != p2) // if only one of them is null
                    return false;
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("squid:S135")
    public boolean startsWith(PluginPath<?> p) {
        List<PluginPath> l1 = list();
        List<PluginPath> l2 = p.list();

        if (l1.size() < l2.size())
            return false;

        for (int i = 0; i < l2.size(); i++) {
            PluginPath<?> p1 = l1.get(i);
            PluginPath<?> p2 = l2.get(i);
            if (p1 != null && p2 != null) {
                if (p2.type == null && p2.name != null && Objects.equals(p1.name, p2.name)) {
                    // ok
                } else if (p2.type != null && p2.name == null && Objects.equals(p1.type, p2.type)) {
                    // ok
                } else if (!(Objects.equals(p1.name, p2.name) && Objects.equals(p1.type, p2.type)))
                    return false;
            } else if (p1 != p2) { // if only one of them is null
                return false;
            }
        }

        return true;
    }

}
