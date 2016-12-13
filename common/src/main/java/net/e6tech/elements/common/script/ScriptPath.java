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

package net.e6tech.elements.common.script;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by futeh.
 */
public class ScriptPath {
    private boolean classPath;
    private String fileName;

    public ScriptPath(String path) {
        this.fileName = path;
    }

    public String getParent() {
        String parent = Paths.get(fileName).getParent().toString();
        if (classPath) {
            return "classpath://" + parent;
        }
        return parent;
    }

    public boolean isClassPath() {
        return classPath;
    }

    public void setClassPath(boolean classPath) {
        this.classPath = classPath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Path getPath() {
        return Paths.get(fileName);
    }
}
