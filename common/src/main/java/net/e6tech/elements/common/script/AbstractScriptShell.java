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

import groovy.lang.Closure;
import groovy.lang.GString;
import net.e6tech.elements.common.resources.Configuration;
import net.e6tech.elements.common.util.file.FileUtil;

import javax.script.ScriptException;
import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
abstract public class AbstractScriptShell {
    private String env;
    private Map<String, List<String>> knownEnvironments = new LinkedHashMap<>();
    private Scripting scripting;
    private Properties properties;
    List<Runnable> cleanup = new LinkedList<>();
    boolean loading = false;

    protected AbstractScriptShell() {
    }

    protected AbstractScriptShell(Properties properties) {
        initialize(null, properties);
    }

    protected void initialize(ClassLoader classLoader, Properties properties) {
        if (properties == null) properties = new Properties();
        this.properties = properties;
        env = properties.getProperty("env");

        // see if a script base is already defined.  If so, we need replace it
        // with the appropriate script base
        // after Scripting.newInstance, we need to put it back
        String originalScriptBase = properties.getProperty(Scripting.SCRIPT_BASE_CLASS);
        if (originalScriptBase == null) {
            String className = getClass().getName() + "Script";
            try {
                getClass().getClassLoader().loadClass(className);
                properties.put(Scripting.SCRIPT_BASE_CLASS, className);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        env = properties.getProperty("env");
        scripting = Scripting.newInstance(classLoader, properties);
        if (originalScriptBase == null) properties.remove(Scripting.SCRIPT_BASE_CLASS);
        else properties.put(Scripting.SCRIPT_BASE_CLASS, originalScriptBase);

        String simpleName = getClass().getSimpleName();
        if (simpleName.length() == 0) {
            simpleName = getClass().getName();
            int idx = simpleName.lastIndexOf('.');
            if (idx >=0 ) simpleName = simpleName.substring(idx + 1);
        }
        String shellName = Introspector.decapitalize(simpleName);
        scripting.put(shellName, this);
        scripting.put("shell", this);
    }

    public Scripting getScripting() {
        return scripting;
    }

    public boolean isLoading() {
        return loading;
    }

    public void load(String str) throws ScriptException {
        try {
            loading = true;
            scripting.load(str);
            onLoaded();
        } finally {
            loading = false;
        }
    }

    protected void onLoaded() {
        for (Runnable r : cleanup) {
            r.run();
        }
        cleanup.clear();
        System.gc();
    }

    public void addCleanup(Runnable r) {
        cleanup.add(r);
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        scripting.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) getScripting().get(key);
    }

    public  Map<String, List<String>> defineKnownEnvironments(String str) {
        knownEnvironments = Configuration.defineEnvironments(str);
        return knownEnvironments;
    }

    public Map<String, List<String>> getKnownEnvironments() {
        return knownEnvironments;
    }

    public void setKnownEnvironments(Map<String, List<String>> knownEnvironments) {
        this.knownEnvironments = knownEnvironments;
    }

    public void runAfter(Object callable) {
        scripting.runAfter(callable);
    }

    public void runAfter(Runnable callable) {
        scripting.runAfter(callable);
    }

    public void runAfterIfNotLoading() {
        if (!loading) scripting.runAfter();
    }

    public Object runNow(Object caller, Object callable) {
        return scripting.runNow(caller, callable);
    }

    public Object runNow(Object caller, Runnable callable) {
        return scripting.runNow(caller, callable);
    }

    public Object runNow(Object caller, Callable callable) {
        return scripting.runNow(caller, callable);
    }

    public void runLaunched(Runnable callable) {
        scripting.runLaunched(callable);
    }

    public Object exec(String path) {
        try {
            return getScripting().exec(path);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    public void exec(Object ... items) {
        Object value = null;
        for (Object item : items) {
            try {
                if (item instanceof String || item instanceof GString) {
                    value = getScripting().exec(item.toString());
                } else if (item instanceof Closure) {
                    final Closure clonedClosure = (Closure) ((Closure) item).clone();
                    try {
                        clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
                        clonedClosure.setDelegate(value);
                        Object ret = clonedClosure.call(value);
                        if (ret != null) value = ret;
                    } finally {
                        clonedClosure.setDelegate(null);
                    }
                } else if (item instanceof String[]) {
                    for (String str : (String[]) item) {
                        value = getScripting().exec(str);
                    }
                } else {
                    value = item;
                }
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Dir dir(String dir) {
        return new Dir(dir);
    }

    public static class Dir {
        private String dir;

        public Dir() {
            dir = "";
        }

        public Dir(String base) {
            dir = base;
            while (dir.endsWith(File.separator) || dir.endsWith("/")) {
                if ("classpath://".equals(dir) || "classpath:/".equals(dir)) break;
                dir = dir.substring(0, dir.length() - 1);
            }
        }

        public String[] expand(String ... items) {
            if (items == null) return new String[0];
            String[] paths = new String[items.length];
            for (int i = 0; i < paths.length; i++) {
                String item = items[i];
                while (item.startsWith(File.separator) || item.startsWith("/")) item = item.substring(1);
                if (!item.endsWith(".groovy")) {
                    item += ".groovy";
                }
                paths[i] = dir + "/" + item;
            }
            return paths;
        }
    }
}
