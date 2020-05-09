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

import groovy.lang.*;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.function.SupplierWithException;

import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;


/**
 * Created by futeh.
 */
public abstract class AbstractScriptBase<T extends AbstractScriptShell> extends Script {

    public static final String CALLER = "__caller";

    private static final Logger logger = Logger.getLogger();

    T scriptShell;

    private Binding privateBinding = new Binding();

    @Override
    public void setBinding(Binding binding) {
        if (binding != null) {
            if (binding.hasVariable(Scripting.__DIR))
                privateBinding.setVariable(Scripting.__DIR, binding.getProperty(Scripting.__DIR));
            if (binding.hasVariable(Scripting.__FILE))
                privateBinding.setVariable(Scripting.__FILE, binding.getProperty(Scripting.__FILE));
        }
        super.setBinding(binding);
    }

    @Override
    public Object getProperty(String property) {
        try {
            return privateBinding.getVariable(property);
        } catch (MissingPropertyException e) {
            return super.getProperty(property);
        }
    }

    public T getShell() {
        if (scriptShell == null) {
            scriptShell = getVariable("shell");
        }
        return scriptShell;
    }

    protected boolean hasVariable(String var) {
        return getBinding().hasVariable(var);
    }

    @SuppressWarnings("unchecked")
    protected <V> V getVariable(String var) {
        return (V) getBinding().getVariable(var);
    }

    protected void setVariable(String var, Object val) {
        getBinding().setVariable(var, val);
    }

    protected <T, E extends Exception> T call(SupplierWithException<T, E> supplier) throws E {
        Object prev = getShell().getScripting().get(CALLER);
        try {
            getShell().getScripting().put(CALLER, this);
            return supplier.get();
        } finally {
            getShell().getScripting().put(CALLER, prev);
        }
    }

    /**
     * execute the path relative to __dir
     */
    public Object execDir(String path) throws ScriptException {
        String dir = (String) getProperty(Scripting.__DIR);
        Path p = Paths.get(dir, path);
        return call(() -> getShell().getScripting().exec(p.toString()));
    }

    public Object exec(String path) throws ScriptException {
        return call(() -> getShell().getScripting().exec(path));
    }

    public void exec(Object ... items) {
        call(() -> {
            getShell().exec(items);
            return null;
        });
    }

    public AbstractScriptShell.Dir dir(String dir) {
        return getShell().dir(dir);
    }

    public Object tryExec(String path) {
        try {
            return call(() -> getShell().getScripting().exec(path));
        } catch (ScriptException e) {
            if (e.getCause() instanceof IOException) {
                logger.info("Script {} not processed: {}", path, e.getCause().getMessage());
            } else {
                logger.warn("Script not processed due to error.", e);
            }
            return null;
        }
    }

    public void after(Object callable) {
        scriptShell.runAfter(callable);
    }

    public void setup(Object caller, Object callable) {
        scriptShell.runNow(caller, callable);
    }

    @SuppressWarnings({"squid:S3776", "squid:S1188"})
    public void systemProperties(Closure closure) {
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(new GroovyObjectSupport() {
            @Override
            @SuppressWarnings("squid:MethodCyclomaticComplexity")
            public Object invokeMethod(String name, Object args) {
                Properties props = System.getProperties();
                Object[] arr = (Object[]) args;
                if ("set".equals(name)) {
                    if (arr.length != 2)
                        return super.invokeMethod(name, args);
                    if ((arr[0] instanceof String || arr[0] instanceof GString)
                            && (arr[1] instanceof String || arr[1] instanceof GString)) {
                        String key = (arr[0] == null) ? null : arr[0].toString();
                        String value = (arr[1] == null) ? null : arr[1].toString();
                        return props.setProperty(key, value);
                    } else {
                        return super.invokeMethod(name, args);
                    }
                } else {
                    String val = (arr[0] == null) ? null : arr[0].toString();
                    if (val != null && !props.containsKey(name)) {
                        return props.put(name, val);
                    } else {
                        return props.get(name);
                    }
                }
            }
        });
        closure.call();
    }

    public void env(String name, Closure closure) {
        if (getShell().getScripting().get("env").equals(name)) {
            closure.call();
        }
    }

    public String getenv(String envName, String defaultVal) {
        String value = System.getenv(envName);
        return (value != null) ? value : defaultVal;
    }

    public Class loadClass(String className) {
        return loadClass(getShell().getClass().getClassLoader(), className);
    }

    public Class loadClass(ClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new SystemException(e);
        }
    }
}
