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
import net.e6tech.elements.common.util.SystemException;

import java.util.Optional;

public interface PluginModel {
    Resources getResources();

    String getName();

    default <P extends Plugin> void registerPlugin(Class<P> pluginBaseClass, P plugin) {
        getResources().getResourceManager().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pluginBaseClass), plugin);
    }

    default <P extends Plugin> void registerPlugin(Class<P> pluginBaseClass, Class<? extends P> pluginClass) {
        getResources().getResourceManager().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pluginBaseClass), (Class) pluginClass);
    }

    default <P extends Plugin> P unregisterPlugin(Class<P> pluginBaseClass) {
        return (P) getResources().getResourceManager().getPluginManager().remove(PluginPath.of(getClass(), getName()).and(pluginBaseClass));
    }

    default <P extends Plugin> void registerPlugin(Class pathClass, String pathAttribute, Class<P> pluginBaseClass, P plugin) {
        getResources().getResourceManager().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass), plugin);
    }

    default <P extends Plugin> void registerPlugin(Class pathClass, String pathAttribute, Class<P> pluginBaseClass, Class<? extends P> pluginClass) {
        getResources().getResourceManager().getPluginManager().add(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass), (Class) pluginClass);
    }

    default <P extends Plugin> P unregisterPlugin(Class pathClass, String pathAttribute, Class<P> pluginBaseClass) {
        return (P) getResources().getResourceManager().getPluginManager().remove(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass));
    }

    default <P extends Plugin> boolean isPluginRegistered(Class<P> pluginBaseClass) {
        return getResources().getPlugin(PluginPath.of(getClass(), getName()).and(pluginBaseClass)).isPresent();
    }

    default <P extends Plugin> boolean isPluginRegistered(Class pathClass, String pathAttribute, Class<P> pluginBaseClass) {
        return getResources().getPlugin(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(pluginBaseClass)).isPresent();
    }

    default <P extends Plugin> PluginPaths<P> getPluginPaths(Class<P> cls) {
        return PluginPaths.of(getClass(), getName(), cls);
    }

    default <P extends Plugin> PluginPaths<P> getPluginPaths(Class pathClass, String pathAttribute, Class<P> cls) {
        return PluginPaths.of(PluginPath.of(getClass(), getName()).and(pathClass, pathAttribute).and(cls));
    }

    default <P extends Plugin> Optional<P> getPlugin(Class<P> cls, Object ... args) {
        Optional<P> optional = getResources().getPlugin(getPluginPaths(cls), args);
        if (optional.isPresent())
            return optional;

        return DefaultPluginModel.from(getResources()).getPlugin(cls, args);
    }

    default <P extends Plugin> Optional<P> getLevel2Plugin(Class pathClass, String pathAttribute, Class<P> cls, Object ... args) {
        return getResources().getPlugin(getPluginPaths(pathClass, pathAttribute, cls), args);
    }

    default <P extends Plugin> P newPlugin(Class<P> cls, Object ... args) {
        try {
            return getPlugin(cls, args).orElse(cls.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new SystemException(e);
        }
    }

    default Optional<PluginModel> parent() {
        return Optional.empty();
    }
}
