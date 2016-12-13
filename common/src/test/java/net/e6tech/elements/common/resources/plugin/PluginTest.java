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
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by futeh.
 */
public class PluginTest {

    @Test
    public void defaultPlugin() {
        Plugin plugin = new Plugin(new ResourceManager());
        plugin.add(PluginTest.class, "1", PluginX.class, new DefaultPluginX("1"));

        DefaultPluginX x1 = plugin.get(PluginTest.class, "1", PluginX.class);
        assertTrue(x1.name.equals("1"));

        // using default
        DefaultPluginX x2 = plugin.get(PluginTest.class, "abc", PluginX.class);
        assertTrue(x2.name.equals("default"));
    }

    interface  PluginX extends Pluggable {
        static Class defaultPlugin = DefaultPluginX.class;
    }

    private static class DefaultPluginX implements PluginX {
        String name;

        DefaultPluginX() {
            name = "default";
        }

        DefaultPluginX(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
        }
    }

}