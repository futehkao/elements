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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.InitialContextFactory;
import net.e6tech.elements.common.util.file.FileUtil;
import org.osjava.sj.memory.MemoryContext;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Created by futeh.
 */
public class Plugin {

    private static final String DEFAULT_PLUGIN = "defaultPlugin";

    private PluginClassLoader classLoader;
    private Context context;
    private ResourceManager resourceManager;
    private Resources resources;
    private Map<Class, Object> defaultPlugins = new HashMap<>();

    public Plugin from(Resources resources) {
        Plugin plugin = new Plugin(resourceManager);
        plugin.resources = resources;
        plugin.context = context;
        plugin.defaultPlugins = defaultPlugins;
        plugin.classLoader = classLoader;
        return plugin;
    }

    public Plugin(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        classLoader = new PluginClassLoader(resourceManager.getClass().getClassLoader());
        context = (new InitialContextFactory()).createContext(new Hashtable());
    }

    public void loadPlugins(String[] directories) {
        for (String dir: directories) {
            java.nio.file.Path[] paths = new java.nio.file.Path[0];
            try {
                paths = FileUtil.listFiles(dir, "jar");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (java.nio.file.Path p : paths) {
                try {
                    classLoader.addURL(p.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public ClassLoader getPluginClassLoader() {
        return classLoader;
    }

    public <T extends Pluggable> T get(Class c1, String n1, Class c2, Object ... args) {
        return (T) get(Path.of(c1, n1).and(c2), args);
    }

    public <T extends Pluggable> T get(Class c1, String n1, Class c2, String n2, Class c3, Object ... args) {
        return (T) get(Path.of(c1, n1).and(c2, n2).and(c3), args);
    }

    public <T extends Pluggable> T get(Path<T> path, Object ... args) {
        String fullPath = path.path();
        Object lookup = null;
        try {
            lookup =  context.lookup(fullPath);
        } catch (NamingException e) {
            Class type = path.getType();
            Object plugin = defaultPlugins.get(type);
            if (lookup == null) {
                while (type != null && !type.equals(Object.class)) {
                    try {
                        Field field = type.getField(DEFAULT_PLUGIN);
                        lookup = field.get(null);
                        defaultPlugins.put(path.getType(), lookup);
                        break;
                    } catch (NoSuchFieldException e1) {
                    } catch (IllegalAccessException e1) {
                    }
                    type = type.getSuperclass();
                }
                if (lookup == null) throw new RuntimeException("Invalid plugin path: " + fullPath);
            }
        }

        Pluggable plugin;
        if (lookup instanceof Class) {
            try {
                plugin = (T) ((Class) lookup).newInstance();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            plugin = (T) lookup;
        }

        if (args != null && args.length > 0) {
            InjectionModule module = new InjectionModule();
            for (Object arg : args) {
                if (arg instanceof  Binding) {
                    Binding binding = (Binding) arg;
                    if (binding.getName() != null) {
                        module.bindNamedInstance(binding.getName(), binding.getBoundClass(), binding.get());
                    } else {
                        module.bindInstance(binding.getBoundClass(), binding.get());
                    }
                } else {
                    module.bindInstance(arg.getClass(), arg);
                }
            }

            List<Module> modules = new ArrayList<>();
            modules.add(module);
            if (resources != null) modules.add(resources.getModule());
            modules.add(resourceManager.getModule());
            Injector injector = Guice.createInjector(modules);
            if (plugin instanceof InjectionListener) {
                ((InjectionListener) plugin).preInject(resources);
            }
            injector.injectMembers(plugin);
            if (plugin instanceof InjectionListener) {
                ((InjectionListener) plugin).injected(resources);
            }
        }

        plugin.initialize();
        return (T) plugin;
    }

    public <T extends Pluggable> void add(Class c1, String n1, Class<T> c2, T instance) {
        add(Path.of(c1, n1).and(c2), instance);
    }

    public <T extends Pluggable> void add(Class c1, String n1, Class<T> c2, Class<T> implClass) {
        add(Path.of(c1, n1).and(c2), implClass);
    }

    public <T extends Pluggable> void add(Class c1, String n1, Class c2, String n2, Class<T> c3, T object) {
        add(Path.of(c1, n1).and(c2, n2).and(c3), object);
    }

    public <T extends Pluggable> void add(Class c1, String n1, Class c2, String n2, Class<T> c3, Class<T> implClass) {
        add(Path.of(c1, n1).and(c2, n2).and(c3), implClass);
    }

    public <T extends Pluggable> void add(Path<T> path, Class<T> cls) {
        try {
            context.rebind(path.path(), cls);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends Pluggable> void add(Path<T> path, T object) {
        try {
            context.rebind(path.path(), object);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends Pluggable> void addDefault(Class<T> cls, T object) {
        defaultPlugins.put(cls, object);
    }

    public <T extends Pluggable> void addDefault(Class<T> cls, Class<T> implClass) {
        defaultPlugins.put(cls, implClass);
    }

    public static class PluginClassLoader extends URLClassLoader {

        public PluginClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        public void addURL(URL url) {
            super.addURL(url);
        }

    }
}
