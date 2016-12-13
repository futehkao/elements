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

import groovy.lang.*;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.file.FileUtil;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
public class Scripting {
    public static final String SCRIPT_BASE_CLASS = "scriptBaseClass";
    public static final String PATH = "script.path";
    public static final String __DIR = "__dir";
    public static final String __FILE = "__file";
    public static final String __LOAD_DIR = "__load_dir";
    public static final String __LOAD_FILE = "__load_file";
    private static Logger logger = Logger.getLogger();
    private static final Set<String> reservedKeyWords = new HashSet<>();
    static {
        reservedKeyWords.add(__DIR);
        reservedKeyWords.add(__FILE);
    }

    GroovyEngine engine;
    List runAfterList = new LinkedList<>();
    List launchedList = new LinkedList<>();
    ScriptPath scriptPath;

    public static Scripting newInstance(ClassLoader classLoader, Properties properties) {
        Scripting script = new Scripting();

        script.engine = new GroovyEngine(classLoader, properties, true);

        return script;
    }

    protected Scripting() {
    }

    public void put(String key, Object val) {
        if (reservedKeyWords.contains(key)) throw new RuntimeException(key + " is a reserved keyword");
        _put(key, val);
    }

    public void _put(String key, Object val) {
        engine.put(key, val);
    }

    public Object get(String key) {
        return engine.get(key);
    }

    public Map<String, Object> getVariables() {
        return engine.getVariables();
    }

    public Object remove(String key) {
        return engine.remove(key);
    }

    public Properties getProperties() {
        return engine.getProperties();
    }

    private String normalizePath(String path) {
        String dir = (String) get(Scripting.__DIR);
        boolean relativePath = false;
        if (dir != null) {
            if (!path.startsWith(dir) && !path.startsWith(File.separator) && !path.startsWith("/")
                    && !path.startsWith("classpath:")) {
                // detect windows
                int indexOfColon = path.indexOf(":");
                if (indexOfColon > 0) {
                    String prefix = path.substring(0, indexOfColon);
                    if (prefix.contains(File.separator)
                        || prefix.contains("/")) {
                        relativePath = false;
                    } else {
                        relativePath = true;
                    }
                } else {
                    relativePath = true;
                }
            }
        }
        if (relativePath) path = dir + "/" + path;
        return path;
    }

    public Object eval(Path script) throws ScriptException {
        return eval(script, false);
    }

    private Object eval(Path script, boolean topLevel) throws  ScriptException {
        String prevRootDir = null;
        String prevRootFile = null;
        if (topLevel) {
            prevRootDir = (String)get(__LOAD_DIR);
            prevRootFile = (String) get(__LOAD_FILE);
        }

        ScriptPath prev = scriptPath;
        scriptPath = new ScriptPath(normalizePath(script.toString()));
        try {
            Reader reader = null;
            if (Files.exists(scriptPath.getPath())) {
                 // reader = Files.newBufferedReader(script);
            } else {
                String fileName = scriptPath.getFileName();
                boolean loadFromClassPath = false;
                if (fileName.startsWith("classpath://")) {
                    fileName = fileName.substring("classpath://".length());
                    loadFromClassPath = true;
                } else if (fileName.startsWith("classpath:/")) {
                    fileName = fileName.substring("classpath:/".length());
                    loadFromClassPath = true;
                } else if (fileName.startsWith("classpath:")) {
                    fileName = fileName.substring("classpath:".length());
                    loadFromClassPath = true;
                }
                scriptPath.setFileName(fileName);
                scriptPath.setClassPath(true);
                if (loadFromClassPath) {
                    InputStream stream = getClass().getClassLoader().getResourceAsStream(fileName);
                    if (stream == null) throw new IOException("File not found: " + fileName);
                    reader = new InputStreamReader(stream, "UTF-8");
                } else {
                    throw new IOException("Script not found: " + script);
                }
            }

            String dir = scriptPath.getParent();
            String file = scriptPath.getFileName();
            if (!scriptPath.isClassPath()) {
                dir = (new File(dir)).getCanonicalPath();
                file = (new File(dir)).getCanonicalPath();
            }
            _put(__DIR, dir);
            _put(__FILE, file);
            if (topLevel) {
                _put(__LOAD_DIR, dir);
                _put(__LOAD_FILE, file);
            }

            if (Files.exists(scriptPath.getPath())) {
                return engine.eval(scriptPath.getPath().toFile());
            } else {
                return engine.eval(reader, scriptPath.getFileName());
            }
        } catch (IOException e) {
            throw new ScriptException(e);
        } catch (ScriptException e) {
            logger.error("Error eval " + script.toString());
            throw e;
        } catch (RuntimeException e) {
            logger.error("Error eval " + script.toString());
            throw e;
        } catch (Throwable e) {
            logger.error("Error eval " + script.toString(), e);
            throw new ScriptException(e.getMessage());
        } finally {
            scriptPath = prev;
            if (prev != null) {
                _put(__DIR, prev.getParent());
                _put(__FILE, prev.getFileName());
            } else {
                _put(__DIR, null);
                _put(__FILE, null);
            }

            if (topLevel) {
                _put(__LOAD_DIR, prevRootDir);
                _put(__LOAD_FILE, prevRootFile);
            }
        }
    }

    public Object eval(String script) throws ScriptException {
        return engine.eval(script);
    }

    public boolean isRunnable(Object obj) {
        return obj instanceof Runnable || obj instanceof Closure;
    }

    public Object runNow(Object caller, Object callable) {
        Object ret = null;
        if(callable instanceof Closure) {
            Closure closure = (Closure) callable;
            try {
                final Closure clonedClosure = (Closure) closure.clone();
                try {
                    clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
                    clonedClosure.setDelegate(caller);
                    ret = clonedClosure.call(caller);
                } finally {
                    clonedClosure.setDelegate(null);
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        } else if (callable instanceof  Runnable) {
            ((Runnable) callable).run();
        } else if (callable instanceof Callable) {
            try {
                ret = ((Callable) callable).call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("runNow " + callable + " cannot be run");
        }
        return ret;
    }

    public void runAfter(Runnable callable) {
        runAfterList.add(callable);
    }

    public void runAfter(Object callable) {
        runAfterList.add(callable);
    }

    public void runLaunched(Object callable) {
        launchedList.add(callable);
    }
    // onStartup is called when all ResourceManagers are initialized and ready to go.
    public void onLaunched() {
        executeList(launchedList);
        launchedList.clear();
    }

    protected String getExtension() {
        return ".groovy";
    }

    /**
     * Run a script.  The script may trigger further calls to exec.  But, load should not be
     * be called again.
     *
     * @param path
     * @throws ScriptException
     */
    public void load(String path) throws ScriptException {
        exec(path, true);
        runAfter();
    }

    // runAfter is called after scripts are executed.
    protected void runAfter() {
        executeList(runAfterList);
        runAfterList.clear();
    }

    private void executeList(List list) {
        // to prevent concurrent modification (because calling the closure or runnable can modify the list),
        // we copy list to items.
        Object[] items = list.toArray(new Object[list.size()]);
        for (Object obj : items) {
            if (obj instanceof Closure) {
                ((Closure) obj).call();
            } else if (obj instanceof Runnable) {
                ((Runnable) obj).run();
            } else {
                String clsName = (obj == null) ? "null" : obj.getClass().getName();
                throw new RuntimeException("Expecting Closure or Runnable but got " + clsName);
            }
        }

        for (Object obj : items) {
            if (obj instanceof Closure) {
                Closure closure = (Closure) obj;
                closure.setDelegate(null);
                Object owner = closure.getOwner();
                if (owner != null && owner instanceof Closure) {
                    ((Closure) owner).setDelegate(null);
                }
            }
        }
    }

    public Object exec(String path) throws ScriptException {
        return exec(path, false);
    }

    private Object exec(String path, boolean topLevel) throws ScriptException {
        Path[] paths;
        try {
            path = normalizePath(path);
            paths = listFiles(path);
        } catch (IOException e) {
            throw new ScriptException(e);
        }

        Object ret = null;
        for (Path p : paths) {
            Object val = eval(p, topLevel);
            if (val != null) ret = val;
        }
        return ret;
    }

    private Path[] listFiles(String path) throws IOException {
        return FileUtil.listFiles(path, getExtension());
    }

    // This class encapsulates the differences between GroovyShell and GroovyScriptEngineImpl.
    private static class GroovyEngine {
        GroovyShell shell;
        GroovyScriptEngineImpl scriptEngine;
        ScriptContext scriptContext;

        public GroovyEngine(ClassLoader classLoader, Properties properties, boolean useGroovyShell) {
            ClassLoader ctxLoader = classLoader;
            if (ctxLoader == null) classLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader == null) ctxLoader = Scripting.class.getClassLoader();

            CompilerConfiguration compilerConfig = new CompilerConfiguration();
            String scriptBaseClass = properties.getProperty(SCRIPT_BASE_CLASS);
            if (scriptBaseClass != null) compilerConfig.setScriptBaseClass(scriptBaseClass);
            GroovyClassLoader loader = new GroovyClassLoader(ctxLoader, compilerConfig);
            if (properties.getProperty(PATH) != null) {
                loader.addClasspath(properties.getProperty(PATH));
            }

            if (useGroovyShell) {
                Binding binding = new Binding();
                for (Object key : properties.keySet()) {
                    binding.setVariable(key.toString(), properties.get(key));
                }
                // binding.setVariable("vars", properties);
                shell = new GroovyShell(loader, binding, compilerConfig);
            } else {
                scriptEngine = new GroovyScriptEngineImpl(loader);
                scriptContext = new SimpleScriptContext();
                for (Object key : properties.keySet()) {
                    scriptContext.getBindings(ScriptContext.ENGINE_SCOPE).put(key.toString(), properties.get(key));
                }
                // scriptContext.getBindings(ScriptContext.ENGINE_SCOPE).put("vars", properties);
            }
        }

        public void put(String key, Object val) {
            if (shell != null) {
                shell.setVariable(key, val);
            } else {
                scriptContext.getBindings(ScriptContext.ENGINE_SCOPE).put(key, val);
            }
        }

        public Map<String, Object> getVariables() {
            Map<String, Object> binding;
            if (shell != null) {
                binding = shell.getContext().getVariables();
            } else {
                binding = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : binding.entrySet()) {
                if (entry.getValue() instanceof GString) {
                    variables.put(entry.getKey(), entry.getValue().toString());
                } else {
                    variables.put(entry.getKey(), entry.getValue());
                }
            }
            return variables;
        }

        public Object get(String key) {
            if (shell != null) {
                if ("binding".equals(key)) return shell.getContext();
                return shell.getVariable(key);
            } else {
                if ("binding".equals(key)) return scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
                return scriptContext.getBindings(ScriptContext.ENGINE_SCOPE).get(key);
            }
        }

        public Object remove(String key) {
            if (shell != null) {
                return shell.getContext().getVariables().remove(key);
            } else {
                return scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).remove(key);
            }
        }

        public Properties getProperties() {
            if (shell != null) {
                Map binding = shell.getContext().getVariables();
                Properties properties = new Properties();
                for (Object key : binding.keySet()) {
                    Object value = binding.get(key);
                    if (value != null) properties.setProperty(key.toString(), value.toString());
                }
                return properties;
            } else {
                Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
                Properties properties = new Properties();
                for (String key : bindings.keySet()) {
                    Object value = bindings.get(key);
                    if (value != null) properties.setProperty(key, value.toString());
                }
                return properties;
            }
        }

        public Object eval(File file) throws IOException, ScriptException {
            if (shell != null) {
                return shell.evaluate(file);
            } else {
                Reader reader = new BufferedReader(new FileReader(file));
                return scriptEngine.eval(reader, scriptContext);
            }
        }

        public Object eval(Reader reader, String fileName) throws IOException, ScriptException {
            if (shell != null) {
                return shell.evaluate(reader, scriptName(fileName));
            } else {
                return scriptEngine.eval(reader, scriptContext);
            }
        }


        public Object eval(String script) throws ScriptException {
            if (shell != null) {
                return shell.evaluate(script);
            } else {
                try {
                    return scriptEngine.eval(script, scriptContext);
                } catch (ScriptException e) {
                    logger.error("Error eval " + script);
                    throw e;
                }
            }
        }
    }

    private static String scriptName(String fileName) {
        Path path = Paths.get(fileName);
        Path file = path.getFileName();
        if (file.toString().endsWith(".groovy")) {
            int idx = file.toString().lastIndexOf(".");
            String name = file.toString().substring(0, idx);
            name += "$script.groovy"; // change the name so that in the script a variable can have the same name as the script.
            file = Paths.get(name);
        } else {
            file = Paths.get(file.toString() + "$script");
        }
        Path parent = path.getParent();
        path = Paths.get(parent.toString(), file.toString());
        return path.toString();
    }
}
