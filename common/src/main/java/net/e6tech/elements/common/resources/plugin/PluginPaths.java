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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class PluginPaths<T> {

    private List<PluginPath> paths = new ArrayList<>();
    private Class<T> type;
    private String toString;
    private int hash = 0;

    public static <T> PluginPaths<T> of(PluginPath<T> path) {
        PluginPaths<T> paths = new PluginPaths<>();
        paths.add(path);
        return paths;
    }

    public static <T> PluginPaths<T> of(Class baseClass, Class<T> cls) {
        PluginPaths<T> paths = new PluginPaths<>();
        paths.add(PluginPath.of(baseClass).and(cls));
        return paths;
    }

    public static <T> PluginPaths<T> of(Class baseClass, String baseName, Class<T> cls) {
        PluginPaths<T> paths = new PluginPaths<>();
        paths.add(PluginPath.of(baseClass, baseName).and(cls));
        return paths;
    }

    public PluginPaths<T> add(Class baseClass, Class<T> cls) {
        return add(PluginPath.of(baseClass).and(cls));
    }

    public PluginPaths<T> add(Class baseClass, String baseName, Class<T> cls) {
        return add(PluginPath.of(baseClass, baseName).and(cls));
    }

    public PluginPaths<T> add(PluginPath<T> ... paths) {
        if (paths != null && paths.length > 0) {
            for (PluginPath<T> path : paths) {
                this.paths.add(path);
                type = path.getType();
            }
            toString = null;
            hash = 0;
        }
        return this;
    }

    public PluginPaths<T> add(List<PluginPath<T>> paths) {
        this.paths.addAll(paths);
        if (!paths.isEmpty()) {
            type = paths.get(paths.size() - 1).getType();
            toString = null;
            hash = 0;
        }
        return this;
    }

    public List<PluginPath> getPaths() {
        return paths;
    }

    public Class<T> getType() {
        return type;
    }

    public String toString() {
        if (toString != null)
            return toString;

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (PluginPath<T> path : paths) {
            if (first) {
                builder.append(path);
                first = false;
            } else {
                builder.append(":");
                builder.append(path);
            }
        }
        toString = paths.toString();
        return toString;
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = Objects.hash(paths.toArray());
        }
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof  PluginPaths))
            return false;
        PluginPaths pp = (PluginPaths) object;
        if (paths.size() == pp.getPaths().size()) {
            for (int i = 0; i < paths.size(); i++) {
                if (!Objects.equals(paths.get(i), pp.paths.get(i)))
                    return false;
            }
            return true;
        }
        return false;
    }
}
