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

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {

    @Test
    void basic() {
        PluginPath<PluginX> path = PluginPath.of(String.class, "A").and(Long.class, "1").and(PluginX.class);
        ResourceManager rm = new ResourceManager();
        PluginManager manager = rm.getPluginManager();
        manager.add(path,  new PluginX());
        path = PluginPath.of(String.class, "A").and(Long.class, "2").and(PluginX.class);
        manager.add(path,  PluginX.class);

        assertEquals(1, manager.startsWith(PluginPath.of(String.class, "A").and(Long.class, "1")).size());
        assertEquals(2, manager.startsWith(PluginPath.of(String.class, "A")).size());
        assertEquals(2, manager.startsWith(PluginPath.of(String.class)).size());

        Map<PluginPath, PluginEntry> map = rm.getPluginManager().startsWith(PluginPath.of(String.class));
        assertNotNull(map.get(path));

        manager.add(path,  PluginX.class);

        Map<PluginPath, PluginEntry> m = manager.startsWith(PluginPath.of(String.class, "A").and(Long.class, "2"));
        assertEquals(1, m.size());

        Map<PluginPath, PluginEntry> map2 = rm.getPluginManager().startsWith(PluginPath.of(String.class));
        assertEquals(map.size(), map2.size());

    }

    @Test
    void pluginModel() {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);
        rm.getInstance(Provision.class).open().accept(Resources.class, resources -> {
            DefaultPluginModel model = resources.newInstance(DefaultPluginModel.class);
            model.registerPlugin(PluginX.class, new PluginX());
            PluginEntry<PluginX> entry = model.getPluginEntry(PluginX.class).orElse(null);
            assertNotNull(entry);
            assertTrue(entry.getPlugin() instanceof PluginX);

            // level 2
            model.registerPlugin(Void.class, "A", PluginX.class, new PluginX());
            entry = model.getLevel2PluginEntry(Void.class, "A", PluginX.class).orElse(null);
            assertNotNull(entry);
            assertTrue(entry.getPlugin() instanceof PluginX);
        });
    }

    public static class PluginX implements Plugin {

    }
}


