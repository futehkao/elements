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

import net.e6tech.elements.common.resources.InstanceNotFoundException;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public interface PluginModel {
    Resources getResources();

    String getName();

    default <P extends Plugin> PluginEntry<P> registerPlugin(Class<P> pluginBaseClass, P plugin) {
        return getResources().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pluginBaseClass), plugin);
    }

    default <P extends Plugin> PluginEntry<P> registerPlugin(Class<P> pluginBaseClass, Class<? extends P> pluginClass) {
        return getResources().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pluginBaseClass), (Class) pluginClass);
    }

    default <P extends Plugin> Object unregisterPlugin(Class<P> pluginBaseClass) {
        return getResources().getPluginManager().remove(PluginPath.of(getClass(), getName()).and(pluginBaseClass));
    }

    default <P extends Plugin> PluginEntry<P> registerPlugin(Class pathClass, String pathAttribute, Class<P> pluginBaseClass, P plugin) {
        return getResources().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass), plugin);
    }

    default <P extends Plugin> PluginEntry<P> registerPlugin(Class pathClass, String pathAttribute, Class<P> pluginBaseClass, Class<? extends P> pluginClass) {
        return getResources().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass), (Class) pluginClass);
    }

    default <P extends Plugin> Object unregisterPlugin(Class pathClass, String pathAttribute, Class<P> pluginBaseClass) {
        return getResources().getPluginManager().remove(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass));
    }

    default <P extends Plugin> PluginEntry<P> registerPlugin(Class pathClass, String pathAttribute, Class pathClass2, String pathAttribute2, Class<P> pluginBaseClass, P plugin) {
        return getResources().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pathClass2, pathAttribute2).and(pluginBaseClass), plugin);
    }

    default <P extends Plugin> PluginEntry<P> registerPlugin(Class pathClass, String pathAttribute,  Class pathClass2, String pathAttribute2, Class<P> pluginBaseClass, Class<? extends P> pluginClass) {
        return getResources().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pathClass2, pathAttribute2).and(pluginBaseClass), (Class) pluginClass);
    }

    default <P extends Plugin> Object unregisterPlugin(Class pathClass, String pathAttribute, Class pathClass2, String pathAttribute2, Class<P> pluginBaseClass) {
        return getResources().getPluginManager().remove(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pathClass2, pathAttribute2).and(pluginBaseClass));
    }

    default <P extends Plugin> boolean isPluginRegistered(Class<P> pluginBaseClass) {
        return getResources().getPlugin(PluginPath.of(getClass(), getName()).and(pluginBaseClass)).isPresent();
    }

    default <P extends Plugin> boolean isPluginRegistered(Class pathClass, String pathAttribute, Class<P> pluginBaseClass) {
        return getResources().getPlugin(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass)).isPresent();
    }

    default <P extends Plugin> boolean isPluginRegistered(Class pathClass, String pathAttribute, Class pathClass2, String pathAttribute2, Class<P> pluginBaseClass) {
        return getResources().getPlugin(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pathClass2, pathAttribute2).and(pluginBaseClass)).isPresent();
    }

    default <P extends Plugin> PluginPaths<P> getPluginPaths(Class<P> cls) {
        return PluginPaths.of(getClass(), getName(), cls);
    }

    default <P extends Plugin> PluginPaths<P> getPluginPaths(Class pathClass, String pathAttribute,
                                                             Class<P> cls) {
        return PluginPaths.of(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(cls));
    }

    default <P extends Plugin> PluginPaths<P> getPluginPaths(Class pathClass, String pathAttribute,
                                                            Class pathClass2, String pathAttribute2,
                                                            Class<P> cls) {
        return PluginPaths.of(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pathClass2, pathAttribute2).and(cls));
    }

    default <P extends Plugin> Optional<P> getPlugin(PluginPath<P> path, Object ... args) {
        Optional<P> optional = getResources().getPlugin(path, args);
        if (optional.isPresent())
            return optional;

        if (path.parent() == null)
            return DefaultPluginModel.from(getResources()).getPlugin(path.getType(), args);
        return optional;
    }

    default <P extends Plugin> Optional<PluginEntry<P>> getPluginEntry(PluginPath<P> path) {
        return getResources().getPluginManager().getEntry(path);
    }

    default <P extends Plugin> Optional<P> getPlugin(Class<P> cls, Object ... args) {
        Optional<P> optional = getResources().getPlugin(getPluginPaths(cls), args);
        if (optional.isPresent())
            return optional;

        return DefaultPluginModel.from(getResources()).getPlugin(cls, args);
    }

    default <P extends Plugin> Optional<PluginEntry<P>> getPluginEntry(Class<P> cls) {
        return getResources().getPluginManager().getEntry(getPluginPaths(cls));
    }

    default <P extends Plugin> Optional<P> getLevel2Plugin(Class pathClass, String pathAttribute,
                                                           Class<P> cls, Object ... args) {
        return getResources().getPlugin(getPluginPaths(pathClass, pathAttribute, cls), args);
    }

    default <P extends Plugin> Optional<PluginEntry<P>> getLevel2PluginEntry(Class pathClass, String pathAttribute,
                                                                       Class<P> cls) {
        return getResources().getPluginManager().getEntry(getPluginPaths(pathClass, pathAttribute, cls));
    }

    default <P extends Plugin> Optional<P> getLevel3Plugin(Class pathClass, String pathAttribute,
                                                           Class pathClass2, String pathAttribute2,
                                                           Class<P> cls, Object ... args) {
        return getResources().getPlugin(getPluginPaths(pathClass, pathAttribute, pathClass2, pathAttribute2, cls), args);
    }

    default <P extends Plugin> Optional<PluginEntry<P>> getLevel3PluginEntry(Class pathClass, String pathAttribute,
                                                                        Class pathClass2, String pathAttribute2,
                                                                        Class<P> cls)  {
        return getResources().getPluginManager().getEntry(getPluginPaths(pathClass, pathAttribute, pathClass2, pathAttribute2, cls));
    }

    default <P extends Plugin> P newPlugin(Class<P> cls, Object ... args) {
        try {
            return getPlugin(cls, args).orElse(cls.getDeclaredConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Returns a plugin and bind it with resources
     * @param cls Plugin class
     * @param args arguments
     * @param <P> Plugin
     * @return Plugin
     */
    default <P extends Plugin> P requirePlugin(Class<P> cls, Object ... args) {
        return getPlugin(cls, args).orElseThrow(InstanceNotFoundException::new);
    }

    /**
     * plugin specified by cls must exist.
     */
    default <P extends Plugin> P acceptPlugin(Class<P> cls, Consumer<P> consumer, Object ... args) {
        P plugin = requirePlugin(cls, args);
        consumer.accept(plugin);
        return plugin;
    }

    /**
     * plugin specified by cls must exist.
     */
    default <P extends Plugin, R> R applyPlugin(Class<P> cls, Function<P, R> function, Object ... args) {
        P plugin = requirePlugin(cls, args);
        return function.apply(plugin);
    }

    /**
     * If plugin exists, consumer.accept is called
     */
    default <P extends Plugin> Optional<P> ifPlugin(Class<P> cls, Consumer<P> consumer, Object ... args) {
        Optional<P> optional = getPlugin(cls, args);
        optional.ifPresent(consumer);
        return optional;
    }

    /**
     * If plugin exists, function.apply is called.  The result is returned.
     */
    default <P extends Plugin, R> Optional<R> mapPlugin(Class<P> cls, Function<P, R> function, Object ... args) {
        Optional<P> optional = getPlugin(cls, args);
        return optional.map(function);
    }

    default Optional<PluginModel> parent() {
        return Optional.empty();
    }
}
