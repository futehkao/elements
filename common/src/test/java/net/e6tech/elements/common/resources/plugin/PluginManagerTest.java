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

import net.e6tech.elements.common.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginManagerTest {

    @Test
    void basic() {
        PluginPath<PluginX> path = PluginPath.of(String.class, "A").and(Long.class, "1").and(PluginX.class);
        ResourceManager rm = new ResourceManager();
        rm.getPluginManager().add(path,  new PluginX());
        path = PluginPath.of(String.class, "A").and(Long.class, "2").and(PluginX.class);
        rm.getPluginManager().add(path,  PluginX.class);
        assertEquals(1, rm.getPluginManager().startsWith(PluginPath.of(String.class, "A").and(Long.class, "1")).size());
        assertEquals(2, rm.getPluginManager().startsWith(PluginPath.of(String.class, "A")).size());
        assertEquals(2, rm.getPluginManager().startsWith(PluginPath.of(String.class)).size());

        Map<PluginPath, PluginEntry> map = rm.getPluginManager().startsWith(PluginPath.of(String.class));
        assertNotNull(map.get(path));
    }

    public static class PluginX implements Plugin {

    }
}


