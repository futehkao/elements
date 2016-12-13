/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.network.shell;

import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.ServiceLoaderDiscovery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by futeh.
 */
public class PluginDiscovery extends ServiceLoaderDiscovery {

    protected List<CRaSHPlugin> plugins = new ArrayList<>();
    protected boolean loaded = false;
    protected ClassLoader classLoader;

    public PluginDiscovery(ClassLoader classLoader)
            throws NullPointerException {
        super((classLoader == null) ? ClassLoader.getSystemClassLoader() : classLoader);
        this.classLoader = (classLoader == null) ? ClassLoader.getSystemClassLoader() : classLoader;
    }

    public CRaSHPlugin addPlugin(String plugin) {
        try {
            CRaSHPlugin p = (CRaSHPlugin) Class.forName(plugin, false, classLoader).newInstance();
            return addPlugin(p);
        } catch (Throwable x) {
           throw new RuntimeException(x);
        }
    }

    public CRaSHPlugin addPlugin(CRaSHPlugin plugin) {
        getPlugins();
        plugins.add(plugin);
        return plugin;
    }

    public void removePlugin(String clsName) throws ClassNotFoundException {
        removePlugin((Class<CRaSHPlugin>) Class.forName(clsName, false, classLoader));
    }

    public void removePlugin(Class<CRaSHPlugin> cls) {
        Iterator<CRaSHPlugin<?>> iterator = getPlugins().iterator();
        while (iterator.hasNext()) {
            CRaSHPlugin<?> plugin = iterator.next();
            if (plugin.getClass().equals(cls)) iterator.remove();
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Iterable<CRaSHPlugin<?>> getPlugins() {
        if (loaded) return (Iterable) plugins;
        for (CRaSHPlugin<?> cRaSHPlugin : super.getPlugins()) {
            plugins.add(cRaSHPlugin);
        }
        loaded = true;
        return (Iterable) plugins;
    }
}
