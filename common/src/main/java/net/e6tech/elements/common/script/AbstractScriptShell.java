/*
Copyright 2015-2019 Futeh Kao

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
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Configuration;

import javax.script.ScriptException;
import java.beans.Introspector;
import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
public abstract class AbstractScriptShell {
    private static Map<String, Object> constants = new HashMap<>();
    private static Logger logger = Logger.getLogger();
    private Map<String, List<String>> knownEnvironments = new LinkedHashMap<>();
    private Scripting scripting;
    private Properties properties;
    List<Runnable> cleanup = new LinkedList<>();
    boolean loading = false;

    static {
        constants.put("SECOND", 1000L);
        constants.put("MINUTE", 60 * 1000L);
        constants.put("HOUR", 60 * 60 * 1000L);
        constants.put("DAY", 24 * 60 * 60 * 1000L);
    }

    protected AbstractScriptShell() {
    }

    protected AbstractScriptShell(Properties properties) {
        initialize(null, properties);
    }

    protected void initialize(ClassLoader classLoader, Properties props) {
        this.properties = (props == null) ? new Properties() : props;

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
                Logger.suppress(e);
            }
        }

        scripting = Scripting.newInstance(classLoader, properties);
        if (originalScriptBase == null)
            properties.remove(Scripting.SCRIPT_BASE_CLASS);
        else properties.put(Scripting.SCRIPT_BASE_CLASS, originalScriptBase);

        String simpleName = getClass().getSimpleName();
        if (simpleName.length() == 0) {
            simpleName = getClass().getName();
            int idx = simpleName.lastIndexOf('.');
            if (idx >=0 )
                simpleName = simpleName.substring(idx + 1);
        }
        String shellName = Introspector.decapitalize(simpleName);
        scripting.put(shellName, this);
        scripting.put("shell", this);
        for (Map.Entry<String, Object> entry : constants.entrySet()) {
            scripting.put(entry.getKey(), entry.getValue());
        }
    }

    public Scripting getScripting() {
        return scripting;
    }

    public Object eval(String expression) {
        return getScripting().eval(expression);
    }

    public boolean isLoading() {
        return loading;
    }

    public synchronized void load(String str) throws ScriptException {
        try {
            loading = true;
            scripting.load(str);
            onLoaded();
        } finally {
            loading = false;
        }
    }

    // Same as the load, except specifying a different load directory
    public synchronized void load(String loadDir, String str) throws ScriptException {
        try {
            loading = true;
            scripting.load(loadDir, str);
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
    public <T> T nullableVar(String key) {
        return (T) getScripting().get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getVariable(String key) {
        return Optional.ofNullable(nullableVar(key));
    }

    public Map<String, List<String>> defineKnownEnvironments(String str) {
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
        if (!loading)
            scripting.runAfter();
    }

    public Object runClosure(Object caller, Closure closure, Object ... args) {
        return scripting.runClosure(caller, closure, args);
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
            throw logger.systemException(e);
        }
    }

    public Object parallel(String path) {
        try {
            return getScripting().parallel(path);
        } catch (ScriptException e) {
            throw logger.systemException(e);
        }
    }

    public void exec(Object ... items) {
        Object value = null;
        for (Object item : items) {
            value = execItem(item, value);
        }
    }

    private Object execItem(Object item, Object val) {
        Object value = val;
        try {
            if (item instanceof String || item instanceof GString) {
                value = getScripting().exec(item.toString());
            } else if (item instanceof Closure) {
                final Closure clonedClosure = (Closure) ((Closure) item).clone();
                value = execClosure(clonedClosure, value);
            } else if (item instanceof String[]) {
                for (String str : (String[]) item) {
                    value = getScripting().exec(str);
                }
            } else {
                value = item;
            }
        } catch (ScriptException e) {
            throw logger.systemException(e);
        }
        return value;
    }

    private Object execClosure(Closure closure, Object value) {
        try {
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(value);
            Object ret = closure.call(value);
            if (ret != null)
                return ret;
            else
                return value;
        } finally {
            closure.setDelegate(null);
        }
    }

    public Dir dir(String dir) {
        return new Dir(dir);
    }

    public static class Dir {
        private String directory;

        public Dir() {
            directory = "";
        }

        public Dir(String base) {
            directory = base;
            while (directory.endsWith(File.separator) || directory.endsWith("/")) {
                if ("classpath://".equals(directory) || "classpath:/".equals(directory))
                    break;
                directory = directory.substring(0, directory.length() - 1);
            }
        }

        public String[] expand(String ... items) {
            if (items == null)
                return new String[0];
            String[] paths = new String[items.length];
            for (int i = 0; i < paths.length; i++) {
                String item = items[i];
                while (item.startsWith(File.separator) || item.startsWith("/"))
                    item = item.substring(1);
                if (!item.endsWith(".groovy")) {
                    item += ".groovy";
                }
                paths[i] = directory + "/" + item;
            }
            return paths;
        }
    }
}
