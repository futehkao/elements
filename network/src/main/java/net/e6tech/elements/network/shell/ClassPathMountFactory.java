/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.network.shell;

import org.crsh.vfs.Path;
import org.crsh.vfs.spi.FSMountFactory;
import org.crsh.vfs.spi.Mount;
import org.crsh.vfs.spi.url.Node;
import org.crsh.vfs.spi.url.URLDriver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by futeh.
 */
public class ClassPathMountFactory implements FSMountFactory<Node> {

    private List<String> commandPaths = new ArrayList<>();
    private final ClassLoader loader;

    public ClassPathMountFactory(ClassLoader loader) {
        this.loader = loader;
    }

    public void addCommandPath(String path) {
        commandPaths.add(path);
    }

    @Override
    public Mount<Node> create(Path path) throws IOException {
        if (path == null) {
            throw new NullPointerException();
        }
        URLDriver driver = new URLDriver();
        if (commandPaths.size() > 0) {
            for (String p : commandPaths) {
                while (p.startsWith("/")) p = p.substring(1);
                merge(driver, p);
            }
        } else {
            merge(driver, path.getValue().substring(1));
        }
        return new Mount<>(driver, "classpath:" + path.absolute().getValue());
    }

    protected void merge(URLDriver driver, String path) throws IOException {
        Enumeration<URL> en = loader.getResources(path);
        while (en.hasMoreElements()) {
            URL url = en.nextElement();
            try {
                driver.merge(url);
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
    }
}
