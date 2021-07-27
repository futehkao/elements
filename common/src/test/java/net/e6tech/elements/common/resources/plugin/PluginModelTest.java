/*
 * Copyright 2015-2021 Futeh Kao
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
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;
import org.junit.jupiter.api.Test;

public class PluginModelTest {

    @Test
    void basic() {
        ResourceManager rm = new ResourceManager();
        Provision provision = rm.getInstance(Provision.class);
        provision.open().accept(Resources.class, res -> {
            PluginModelX x = res.newInstance(PluginModelX.class);
            x.registerPlugin(PluginPath.of(PluginX.class, "1"), PluginX.class);
        });
    }

    public static class PluginModelX implements PluginModel {
        @Inject
        private Resources resources;

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public String getName() {
            return null;
        }
    }

    public static class PluginX implements Plugin {
        String name;

        public String name() {
            return name;
        }

        public void onRegistered(PluginModel model) {
            System.currentTimeMillis();
        }
    }
}
