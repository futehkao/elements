/*
 * Copyright 2016 Futeh Kao
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class PluginTest {

    @Test
    public void defaultPlugin() {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.add(PluginPath.of(PluginTest.class, "1").and(PluginX.class), new DefaultPluginX("1"));

        PluginX x1 = manager.get(PluginPath.of(PluginTest.class, "1").and(PluginX.class)).get();
        assertTrue(x1.name().equals("1"));

        // using default
        PluginX x2 = manager.get(PluginPath.of(PluginTest.class, "abc").and(PluginX.class)).get(); // abc doesn't exist, so default is return.
        assertTrue(x2.name().equals("default"));
    }

    @Test
    public void paths() {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.add(PluginPath.of(PluginTest.class, "1").and(PluginX.class), new DefaultPluginX("1"));

        PluginPaths<PluginX> paths = PluginPaths.of(PluginPath.of(PluginTest.class, "2").and(PluginX.class));
        assertTrue(manager.get(paths).get().name().equals("default"));

    }

    interface  PluginX extends Plugin {
        static Class defaultPlugin = DefaultPluginX.class;

        String name();
    }

    private static class DefaultPluginX implements PluginX {
        String name;

        DefaultPluginX() {
            name = "default";
        }

        DefaultPluginX(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public void initialize() {
        }
    }

}