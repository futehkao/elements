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

package net.e6tech.elements.common.util.file;

import net.e6tech.elements.common.Tags;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.plugin.Plugin;
import net.e6tech.elements.common.resources.plugin.PluginManager;
import net.e6tech.elements.common.resources.plugin.PluginPath;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags.Common
public class FileUtilTest {

    @Test
    void basic() throws Exception {
        File file = new File("./");
        String dir = file.getCanonicalPath();
        String[] paths = FileUtil.listFiles(dir + "/*", null);
        System.out.println(paths);

        paths = FileUtil.listFiles(dir + "/**", null);
        System.out.println(paths);

        URL url = Paths.get(paths[0]).toUri().toURL();
        System.out.println(url.toExternalForm());
    }

    @Test
    void classpath() throws Exception {
        String[] paths = FileUtil.listFiles("classpath:/net/e6tech/elements/common" + "/**", null);
        assertTrue(paths.length > 0);
        paths = FileUtil.listFiles("classpath:/net/e6tech/elements/common/actor" + "/**", null);
        assertTrue(paths.length > 0);
        paths = FileUtil.listFiles("classpath:/net/e6tech/elements/common/resources" + "/*", null);
        assertTrue(paths.length > 0);
    }

    @Test
    void loadJars() throws Exception {
        PluginManager manager = new PluginManager(new ResourceManager());
        manager.loadPlugins(new String[] { "./src/test/test-plugins/plugin-test.jar" } );
        String[] paths = FileUtil.listFiles(manager.getPluginClassLoader(), "classpath:net/e6tech/elements/common/resources/**", null);
        assertTrue(paths.length > 0);
        paths = FileUtil.listFiles(manager.getPluginClassLoader(), "classpath:net/e6tech/elements/common/resources/plugin/*", null);
        assertTrue(paths.length > 0);
    }
}
