/*
 * Copyright 2015-2020 Futeh Kao
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

import net.e6tech.elements.common.logging.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class PluginEntry<T> {
    private PluginPath<T> path;
    private Object plugin;
    private String description;

    public PluginEntry() {
    }

    @SuppressWarnings("unchecked")
    public PluginEntry(PluginPath<T> path, Object plugin) {
        this.path = path;
        this.plugin = plugin;
        if (plugin instanceof Class) {
            Class<?> cls = (Class<?>) plugin;
            validateClass((Class<?>) plugin);
            deriveDescription((Class<Plugin>) cls);
        } else if (plugin instanceof PluginFactory)
            description = ((PluginFactory) plugin).description();
        else if (plugin instanceof Plugin)
            description = ((Plugin) plugin).description();
        else
            throw new IllegalArgumentException("Instance of type " + plugin.getClass() + " does not implement Plugin");
    }

    public PluginEntry(PluginPath<T> path, Object plugin, String description) {
        this.path = path;
        this.plugin = plugin;
        this.description = description;
        if (plugin instanceof Class)
            validateClass((Class<?>) plugin);
    }

    public Object getPlugin() {
        return plugin;
    }

    public void setPlugin(Object plugin) {
        this.plugin = plugin;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PluginEntry<T> description(String description) {
        setDescription(description);
        return this;
    }

    public PluginPath<T> getPath() {
        return path;
    }

    protected void setPath(PluginPath<T> path) {
        this.path = path;
    }

    public static void validateClass(Class<?> type) {
        final String CLASS = "Class ";
        if (!Plugin.class.isAssignableFrom(type))
            throw new IllegalArgumentException(CLASS + type + " does not implement Plugin.");
        if (type.isInterface())
            throw new IllegalArgumentException(CLASS + type + " is an interface; cannot be instantiated later.");
        if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
            throw new IllegalArgumentException(CLASS + type + " is an non-static inner class; cannot be instantiated later.");
        if (Modifier.isAbstract(type.getModifiers()))
            throw new IllegalArgumentException(CLASS + type + " is an abstract class; cannot be instantiated later.");
        if (!Modifier.isPublic(type.getModifiers()))
            throw new IllegalArgumentException(CLASS + type + " is not public; cannot be instantiated later.");
        try {
            type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Fail to instantiate class " + type + ": " + e.getMessage(), e);
        }
    }

    private void deriveDescription(Class<Plugin> type) {
        Class<?> t = type;
        while (t != null && !t.equals(Object.class)) {
            try {
                Field field = t.getField(Plugin.PLUGIN_DESCRIPTION);
                Object desc = field.get(null);
                if (desc != null) {
                    description = desc.toString();
                    break;
                }
            } catch (NoSuchFieldException | IllegalAccessException e1) {
                Logger.suppress(e1);
            }
            t = t.getSuperclass();
        }
        if (type != null && description == null) {
            try {
                Plugin p = type.getDeclaredConstructor().newInstance();
                description = p.description();
            } catch (Exception e) {
                // ok
            }
        }
    }
}
