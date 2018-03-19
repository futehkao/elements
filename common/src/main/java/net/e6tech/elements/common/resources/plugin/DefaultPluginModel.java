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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Resources;

import java.util.Optional;

public class DefaultPluginModel implements PluginModel {

    private Resources resources;

    public static DefaultPluginModel from(Resources resources) {
        return resources.newInstance(DefaultPluginModel.class);
    }

    @Override
    public <P extends Plugin> Optional<P> getPlugin(Class<P> cls, Object ... args) {
        return getResources().getPlugin(getPluginPaths(cls), args);
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Inject
    public void setResources(Resources resources) {
        this.resources = resources;
    }

    @Override
    public String getName() {
        return null;
    }
}
