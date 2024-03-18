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

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.inject.ModuleFactory;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.file.FileUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S134", "unchecked"})
public class PluginManager {

    private static final String DEFAULT_PLUGIN = "defaultPlugin";
    private static final Object NULL_OBJECT = new Object();

    private PluginClassLoader classLoader;
    private final ResourceManager resourceManager;
    private Resources resources;
    private Map<PluginPath<?>, PluginEntry> plugins = new HashMap<>();
    private Map<Class<?>, Object> defaultPlugins = new HashMap<>();

    public PluginManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        classLoader = new PluginClassLoader(resourceManager.getClass().getClassLoader());
    }

    protected PluginManager(PluginManager manager) {
        resourceManager = manager.resourceManager;
        resources = manager.resources;
        defaultPlugins = manager.defaultPlugins;
        classLoader = manager.classLoader;
        plugins = manager.plugins;
    }

    public PluginManager from(Resources resources) {
        PluginManager plugin = new PluginManager(this);
        plugin.resources = resources;
        return plugin;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public void loadPlugins(String[] directories) {
        for (String dir : directories) {
            String[] paths;
            try {
                paths = FileUtil.listFiles(dir, ".jar");
            } catch (IOException e) {
                throw new SystemException(e);
            }
            for (String p : paths) {
                String fullPath;
                try {
                    fullPath = new File(p).getCanonicalPath();
                } catch (IOException e) {
                    continue;
                }

                try {
                    classLoader.addURL(Paths.get(fullPath).toUri().toURL());
                } catch (IOException e) {
                    throw new SystemException(e);
                }
            }
        }
    }

    public URLClassLoader getPluginClassLoader() {
        return classLoader;
    }

    @SuppressWarnings({"squid:S3824", "squid:S3776"})
    protected Optional getDefaultPlugin(Class<?> type) {
        Object lookup = defaultPlugins.get(type);
        if (lookup == NULL_OBJECT)
            return Optional.empty();

        if (lookup == null) {
            Class t = type;
            while (t != null && !t.equals(Object.class)) {
                try {
                    Field field = t.getField(DEFAULT_PLUGIN);
                    lookup = field.get(null);
                    defaultPlugins.put(type, lookup);
                    break;
                } catch (NoSuchFieldException | IllegalAccessException e1) {
                    Logger.suppress(e1);
                }
                t = t.getSuperclass();
            }

            // no default static variable named defaultPlugin
            // see if type implements AutoPlugin.  If so, associate type with itself.
            if (lookup == null
                    && type != null
                    && AutoPlugin.class.isAssignableFrom(type)) {
                try {
                    PluginEntry.validateClass(type);
                    lookup = type;
                    defaultPlugins.put(type, lookup);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            }

            if (lookup == null)
                defaultPlugins.put(type, NULL_OBJECT);
        }

        return Optional.ofNullable(lookup);
    }

    public Map<PluginPath, PluginEntry> startsWith(PluginPath<?> path) {
        return startsWith(PluginPaths.of(path));
    }

    public Map<PluginPath, PluginEntry> startsWith(PluginPaths<?> paths) {
        Map<PluginPath, PluginEntry> map = new LinkedHashMap<>();

        for (PluginPath<?> path : paths.getPaths()) {
            for (Map.Entry<PluginPath<?>, PluginEntry> entry : plugins.entrySet()) {
                if (entry.getKey().startsWith(path) && !map.containsKey(entry.getKey()))
                    map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    public <T extends Plugin> Optional<PluginEntry<T>> getEntry(PluginPath<T> path) {
        return Optional.ofNullable(plugins.get(path));
    }

    public <T extends Plugin> Optional<PluginEntry<T>> getEntry(PluginPaths<T> paths) {
        PluginEntry<T> lookup = null;
        // look up from paths
        for (PluginPath path : paths.getPaths()) {
            lookup = plugins.get(path);
            if (lookup != null) {
                break;
            }
        }
        return Optional.ofNullable(lookup);
    }

    public <T extends Plugin> Optional<T> get(PluginPaths<T> paths, Object... args) {
        return lookup(paths, true, args);
    }

    public <T extends Plugin> Optional<T> lookup(PluginPaths<T> paths) {
        return lookup(paths, false);
    }

    private <T extends Plugin> Optional<T> lookup(PluginPaths<T> paths, boolean inject, Object... args) {
        PluginEntry<T> lookup = getEntry(paths).orElse(null);
        PluginPath<T> pluginPath;

        // if still null, look up from default plugin
        Object instance = null;
        if (lookup == null) {
            pluginPath = PluginPath.of(paths.getType(), DEFAULT_PLUGIN);
        } else {
            pluginPath = lookup.getPath();
            instance = lookup.getPlugin();
        }
        return Optional.ofNullable(createAndInject(pluginPath, instance, inject, args));
    }

    public <T extends Plugin> T createInstance(PluginPath<T> pluginPath, Object obj, Object... args) {
        return createAndInject(pluginPath, obj, true, args);
    }

    private <T extends Plugin> T createAndInject(PluginPath<T> pluginPath, Object obj, boolean strictInject, Object... args) {
        if (obj == null && pluginPath == null)
            return null;

        if (obj == null) {
            // get default plugin
            Optional defaultPlugin = getDefaultPlugin(pluginPath.getType());
            if (!defaultPlugin.isPresent())
                return null;
            obj = defaultPlugin.get();
            pluginPath = PluginPath.of(pluginPath.getType(), DEFAULT_PLUGIN);
        }

        T plugin;
        boolean singleton = false;
        if (obj instanceof Class) {
            plugin = createFromClass((Class) obj);
        } else if (obj instanceof PluginFactory) {
            plugin = createFromFactory((PluginFactory) obj);
        } else {
            singleton = isSingleton(obj);
            plugin = createFromInstance(obj);
        }

        if (!singleton) {
            inject(plugin, strictInject, args);
            plugin.initialize(pluginPath);
        }
        return plugin;
    }

    private <T extends Plugin> T createFromClass(Class cls) {
        try {
            return (T) cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private <T extends Plugin> T createFromFactory(PluginFactory factory) {
        return factory.create(this);
    }

    private <T extends Plugin> T createFromInstance(Object obj) {
        T plugin;
        T prototype = (T) obj;
        if (prototype instanceof Prototype) {
            plugin = (T) ((Prototype) prototype).newInstance();
        } else if (prototype.isPrototype()) {
            try {
                plugin = (T) prototype.getClass().getDeclaredConstructor().newInstance();
                Reflection.copyInstance(plugin, prototype);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        } else {
            // this is a singleton, no need to initialize and inject.
            plugin = prototype;
        }
        return plugin;
    }

    private <T extends Plugin> boolean isSingleton(Object obj) {
        T prototype = (T) obj;
        return !(prototype instanceof Prototype || prototype.isPrototype());
    }

    @SuppressWarnings("squid:S3776")
    public void inject(Object instance, boolean strict, Object... args) {
        if (instance != null && args != null && args.length > 0) {
            ModuleFactory factory = (resources != null) ? resources.getModule().getFactory() :
                    resourceManager.getModule().getFactory();
            Module module = factory.create();
            for (Object arg : args) {
                if (arg instanceof Binding) {
                    Binding binding = (Binding) arg;
                    if (binding.getName() != null) {
                        module.bindNamedInstance(binding.getBoundClass(), binding.getName(), binding.get());
                    } else {
                        module.bindInstance(binding.getBoundClass(), binding.get());
                    }
                } else {
                    module.bindInstance(arg.getClass(), arg);
                }
            }

            Injector injector = (resources != null) ?
                    module.build(resources.getModule(), resourceManager.getModule())
                    : module.build(resourceManager.getModule());
            InjectionListener injectionListener = null;
            ResourcePool resourcePool = (resources != null) ? resources : resourceManager;
            if (instance instanceof InjectionListener) {
                injectionListener = (InjectionListener) instance;
                injectionListener.preInject(resourcePool);
            }
            injector.inject(instance, strict);
            if (injectionListener != null)
                injectionListener.injected(resourcePool);
        } else if (instance != null && resources != null) {
            resources.inject(instance, strict);
        }
    }

    public <T extends Plugin> Optional<T> get(PluginPath<T> path, Object... args) {
        return get(PluginPaths.of(path), args);
    }

    public <T extends Plugin> Optional<T> lookup(PluginPath<T> path) {
        return lookup(PluginPaths.of(path));
    }

    public synchronized <T extends Plugin> PluginEntry<T> add(PluginPath<T> path, Class<T> cls) {
        PluginEntry<T> entry = new PluginEntry(path, cls);
        plugins.put(path, entry);
        return entry;
    }

    public synchronized <T extends Plugin> PluginEntry<T> add(PluginPath<T> path, T singleton) {
        PluginEntry<T> entry = new PluginEntry(path, singleton);
        plugins.put(path, entry);
        resourceManager.inject(singleton, !singleton.isPrototype());
        singleton.initialize(path);
        return entry;
    }

    public synchronized <T extends Plugin> Object remove(PluginPath<T> path) {
        PluginEntry entry = plugins.remove(path);
        return entry == null ? null : entry.getPlugin();
    }

    public synchronized <T extends Plugin, U extends T> void addDefault(Class<T> cls, U singleton) {
        defaultPlugins.put(cls, singleton);
        resourceManager.inject(singleton, !singleton.isPrototype());
        singleton.initialize(PluginPath.of(cls, DEFAULT_PLUGIN));
    }

    public synchronized <T extends Plugin, U extends T> void addDefault(Class<T> cls, Class<U> implClass) {
        defaultPlugins.put(cls, implClass);
    }

    public Object removeDefault(Class cls) {
        return defaultPlugins.remove(cls);
    }

    public static class PluginClassLoader extends URLClassLoader {

        private List<URL> pluginURLs = new ArrayList<>();

        public PluginClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
            pluginURLs.add(url);
        }

        public URL[] getPluginURLs() {
            return pluginURLs.toArray(new URL[0]);
        }
    }
}
