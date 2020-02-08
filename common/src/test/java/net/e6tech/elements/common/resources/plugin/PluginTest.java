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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class PluginTest {

    @Test
    void loadJars() throws Exception {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.loadPlugins(new String[] { "./src/test/test-plugins/plugin-test.jar" } );
        Plugin plugin = (Plugin) manager.getPluginClassLoader().loadClass("net.e6tech.elements.common.resources.plugin.SimplePlugin").newInstance();
        plugin.initialize(PluginPath.of(Plugin.class, "Test"));
    }

    @Test
    void loadJars2() throws Exception {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.loadPlugins(new String[] { "./src/test/test-plugins/*" } );
        Plugin plugin = (Plugin) manager.getPluginClassLoader().loadClass("net.e6tech.elements.common.resources.plugin.SimplePlugin").newInstance();
        plugin.initialize(PluginPath.of(Plugin.class, "Test"));
    }

    @Test
    void defaultPlugin() {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.add(PluginPath.of(PluginTest.class, "1").and(PluginX.class), new DefaultPluginX("1"));

        PluginX x1 = manager.get(PluginPath.of(PluginTest.class, "1").and(PluginX.class)).get();
        assertTrue(x1.name().equals("1"));

        // using default
        PluginX x2 = manager.get(PluginPath.of(PluginTest.class, "abc").and(PluginX.class)).get(); // abc doesn't exist, so default is return.
        assertTrue(x2.name().equals("default"));
    }

    @Test
    void paths() {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.add(PluginPath.of(PluginTest.class, "1").and(PluginX.class), new DefaultPluginX("1"));

        PluginPaths<PluginX> paths = PluginPaths.of(PluginPath.of(PluginTest.class, "2").and(PluginX.class));
        assertTrue(manager.get(paths).get().name().equals("default"));

        // first time would make plugin manager cache the paths
        paths = PluginPaths.of(PluginPath.of(PluginTest.class, "2").and(PluginX.class))
            .add(PluginPath.of(PluginTest.class, "1").and(PluginX.class));
        assertTrue(manager.get(paths).get().name().equals("1"));

        // see if we can find it agina using a new copy of PluginPaths
        paths = PluginPaths.of(PluginPath.of(PluginTest.class, "2").and(PluginX.class))
                .add(PluginPath.of(PluginTest.class, "1").and(PluginX.class));
        assertTrue(manager.get(paths).get().name().equals("1"));
    }

    @Test
    void startsWith() {
        PluginManager manager = new PluginManager(new ResourceManager());
        PluginPath<PluginX> p1 = PluginPath.of(PluginTest.class, "1").and(String.class, "2").and(PluginX.class);
        PluginPath<String> p2 = PluginPath.of(PluginTest.class, "1").and(String.class, "2");
        PluginPath<AnotherPlugin> p3 = PluginPath.of(PluginTest.class, "1").and(String.class, "2").and(AnotherPlugin.class);
        assertTrue(p1.startsWith(p2));

        p2 = PluginPath.of(PluginTest.class, "1").and(String.class, "3");
        assertTrue(!p1.startsWith(p2));

        manager.add(p1, new DefaultPluginX("1"));
        manager.add(p3, new AnotherPlugin());

        p2 = PluginPath.of(PluginTest.class, "1").and(String.class, "2");
        assertEquals(manager.startsWith(PluginPaths.of(p2)).size(), 2);
    }

    public interface PluginX extends Plugin {
        Class defaultPlugin = DefaultPluginX.class;

        String name();
    }

    public static class DefaultPluginX implements PluginX {
        String name;

        public DefaultPluginX() {
            name = "default";
        }

        public DefaultPluginX(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
    }

    public static class AnotherPlugin implements Plugin {
    }

    @Test
    public void pluginList() throws Exception {
        ResourceManager resourcesManager = new ResourceManager();
        PluginManager manager = new PluginManager(resourcesManager);
        PluginList<PluginX> list = new PluginList<>();
        manager.add(PluginPath.of(PluginTest.class, "1").and(PluginList.class), list);
        list.add(DefaultPluginX.class);
        list.add(new DefaultPluginX("not default"));

        PluginList<PluginX> list2 = manager.from(resourcesManager.newResources()).get(PluginPath.of(PluginTest.class, "1").and(PluginList.class)).get();

        assertTrue(list != list2);

        List<PluginX> l = list2.list();
        l.size();
    }

    @Test
    public void pluginMap() throws Exception {
        ResourceManager resourcesManager = new ResourceManager();
        PluginManager manager = new PluginManager(resourcesManager);
        PluginMap<String, PluginX> map = new PluginMap<>();
        manager.add(PluginPath.of(PluginTest.class, "1").and(PluginMap.class), map);
        map.put("a", DefaultPluginX.class);
        map.put("b", new DefaultPluginX("not default"));

        PluginMap<String, PluginX> map2 = manager.from(resourcesManager.newResources()).get(PluginPath.of(PluginTest.class, "1").and(PluginMap.class)).get();

        assertTrue(map != map2);

        Map<String, PluginX> l = map2.map();
        l.size();
    }

}