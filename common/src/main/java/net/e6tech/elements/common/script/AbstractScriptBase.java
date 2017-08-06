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
import groovy.lang.GroovyObjectSupport;
import groovy.lang.Script;
import net.e6tech.elements.common.logging.Logger;

import javax.script.ScriptException;
import java.util.Properties;


/**
 * Created by futeh.
 */
public abstract class AbstractScriptBase<T extends AbstractScriptShell> extends Script {

    T scriptShell;

    public T getShell() {
        if (scriptShell == null) {
            scriptShell = getVariable("shell");
        }
        return scriptShell;
    }

    protected <V> V getVariable(String var) {
        return (V) getBinding().getVariable(var);
    }

    public Object exec(String path) throws ScriptException {
        return getShell().getScripting().exec(path);
    }

    public void exec(Object ... items) {
        getShell().exec(items);
    }

    public AbstractScriptShell.Dir dir(String dir) {
        return getShell().dir(dir);
    }

    public Object tryExec(String path) {
        try {
            return getShell().getScripting().exec(path);
        } catch (Exception e) {
            Logger.suppress(e);
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
}
