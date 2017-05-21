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
        PluginManager plugin = new PluginManager(new ResourceManager());
        plugin.add(PluginPath.of(PluginTest.class, "1").and(PluginX.class), new DefaultPluginX("1"));

        PluginX x1 = plugin.get(PluginPath.of(PluginTest.class, "1").and(PluginX.class));
        assertTrue(x1.name().equals("1"));

        // using default
        PluginX x2 = plugin.get(PluginPath.of(PluginTest.class, "abc").and(PluginX.class)); // abc doesn't exist, so default is return.
        assertTrue(x2.name().equals("default"));
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