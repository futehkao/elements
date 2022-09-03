/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.common.script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.util.HashMap;
import java.util.Map;

public class ThreadLocalShell extends GroovyShell {
    private static final ThreadLocal<Map<String, Object>> threadContext = new ThreadLocal<>();

    public ThreadLocalShell() {
        this(null, new Binding());
    }

    public ThreadLocalShell(Binding binding) {
        this(null, binding);
    }

    public ThreadLocalShell(ClassLoader parent, CompilerConfiguration config) {
        this(parent, new Binding(), config);
    }

    public ThreadLocalShell(CompilerConfiguration config) {
        this(new Binding(), config);
    }

    public ThreadLocalShell(Binding binding, CompilerConfiguration config) {
        this(null, binding, config);
    }

    public ThreadLocalShell(ClassLoader parent, Binding binding) {
        this(parent, binding, CompilerConfiguration.DEFAULT);
    }

    public ThreadLocalShell(ClassLoader parent) {
        this(parent, new Binding(), CompilerConfiguration.DEFAULT);
    }

    public ThreadLocalShell(ClassLoader parent, Binding binding, final CompilerConfiguration config) {
        super(parent, binding, config);
    }

    public Object getVariable(String name) {
        Map<String, Object> map = threadContext.get();
        if (map != null && map.containsKey(name)) {
            return map.get(name);
        }
        return super.getVariable(name);
    }

    public void setVariable(String name, Object value) {
        Map<String, Object> map = threadContext.get();
        if (map == null) {
            threadContext.set(map = new HashMap<>());
        }
        map.put(name, value);
        super.setVariable(name, value);
    }

    public Map<String, Object> getVariables() {
        Map<String, Object> map = getContext().getVariables();
        Map<String, Object> map2 = threadContext.get();
        if (map2 != null)
            map.putAll(map2);
        return map;
    }

    public Object remove(String key) {
        Object val = null;
        Map<String, Object> map = threadContext.get();
        if (map != null && map.containsKey(key)) {
            val = map.remove(key);
        }
        if (val == null)
            val = super.getContext().getVariables().remove(key);
        else
            super.getContext().getVariables().remove(key);
        return val;
    }

    public void clearThreadContext() {
        Map<String, Object> map = threadContext.get();
        if (map != null)
            map.clear();
    }
}
