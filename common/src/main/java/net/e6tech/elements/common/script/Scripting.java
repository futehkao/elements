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
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.file.FileUtil;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S00115", "squid:S134", "squid:S1192", "squid:S3400", "squid:S1075"})
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

    private GroovyEngine engine;
    private List runAfterList = new LinkedList<>();
    private List launchedList = new LinkedList<>();
    private ScriptPath scriptPath;
    private boolean silent = false;

    protected Scripting() {
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public static Scripting newInstance(ClassLoader classLoader, Properties properties) {
        Scripting script = new Scripting();

        script.engine = new GroovyEngine(classLoader, properties, true);

        return script;
    }

    public void put(String key, Object val) {
        if (reservedKeyWords.contains(key))
            throw new SystemException(key + " is a reserved keyword");
        privatePut(key, val);
    }

    public void privatePut(String key, Object val) {
        engine.put(key, val);
    }

    public Object get(String key) {
        return engine.get(key);
    }

    public <T> T get(String key, T defaultValue) {
        T t = (T) engine.get(key);
        return (t== null) ? defaultValue : t;
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

    @SuppressWarnings("squid:S1067")
    private String normalizePath(String originalPath) {
        String dir = (String) get(Scripting.__DIR);
        boolean relativePath = false;
        String path = originalPath;
        if (dir != null
                && !path.startsWith(dir)
                && !path.startsWith(File.separator)
                && !path.startsWith("/")
                && !path.startsWith("classpath:")) {
            // detect windows
            int indexOfColon = path.indexOf(':');
            if (indexOfColon > 0) {
                String prefix = path.substring(0, indexOfColon);
                if (prefix.contains(File.separator)
                        || prefix.contains("/")) {
                    relativePath = true;
                } else {
                    relativePath = false;
                }
            } else {
                relativePath = true;
            }
        }
        if (relativePath)
            path = dir + "/" + path;
        return path;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S2093", "squid:S3776"})
    // script is the full path name
    private Object eval(String script, boolean topLevel) throws ScriptException {
        String prevRootDir = null;
        String prevRootFile = null;
        if (topLevel) {
            prevRootDir = (String) get(__LOAD_DIR);
            prevRootFile = (String) get(__LOAD_FILE);
        }

        ScriptPath prev = scriptPath;
        scriptPath = new ScriptPath(normalizePath(script));
        Reader reader = null;
        try {
            String dir = scriptPath.getParent();
            String file;

            if (scriptPath.isClassPath()) {
                InputStream stream = getClass().getClassLoader().getResourceAsStream(scriptPath.getFileName());
                if (stream == null)
                    throw new IOException("File not found: " + scriptPath.getClassPath());
                reader = new InputStreamReader(stream, "UTF-8");
                file = scriptPath.getFileName();
            } else {
                File f = new File(scriptPath.getFileName());
                if (!f.exists())
                    throw new IOException("File not found: " + scriptPath.getFileName());
                dir = (new File(dir)).getCanonicalPath();
                file = f.getCanonicalPath();
            }

            privatePut(__DIR, dir);
            privatePut(__FILE, file);
            if (topLevel) {
                privatePut(__LOAD_DIR, dir);
                privatePut(__LOAD_FILE, file);
            }

            // reader is not null for classpath
            if (reader != null) {
                return engine.eval(reader, scriptPath.getFileName());
            } else {
                return engine.eval(scriptPath.getPath().toFile());
            }
        } catch (IOException e) {
            // rethrow to let caller handle it, instead of logging erro
            throw new ScriptException(e);
        } catch (ScriptException e) {
            logger.error("Error eval " + script);
            throw e;
        } catch (Exception e) {
            logger.error("Error eval " + script, e);
            throw new ScriptException(e.getMessage());
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            scriptPath = prev;
            if (prev != null) {
                privatePut(__DIR, prev.getParent());
                privatePut(__FILE, prev.getFileName());
            } else {
                privatePut(__DIR, null);
                privatePut(__FILE, null);
            }

            if (topLevel) {
                privatePut(__LOAD_DIR, prevRootDir);
                privatePut(__LOAD_FILE, prevRootFile);
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
        if (callable instanceof Closure) {
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
            } catch (Exception th) {
                logger.warn(th.getMessage(), th);
            }
        } else if (callable instanceof Runnable) {
            ((Runnable) callable).run();
        } else if (callable instanceof Callable) {
            try {
                ret = ((Callable) callable).call();
            } catch (Exception e) {
                throw new SystemException(e);
            }
        } else {
            throw new SystemException("runNow " + callable + " cannot be run");
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
     * @param path file path of the script to be load
     * @throws ScriptException throws exception if there are errors.
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
                throw new SystemException("Expecting Closure or Runnable but got " + clsName);
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

    private Object exec(String originalPath, boolean topLevel) throws ScriptException {
        String[] paths;
        try {
            String path = normalizePath(originalPath);
            paths = FileUtil.listFiles(path, getExtension());
            if (paths == null || paths.length == 0)
                throw new IOException("Script not found " + originalPath);
        } catch (IOException e) {
            throw new ScriptException(e);
        }

        Object ret = null;
        for (String p : paths) {
            if (!silent)
                logger.info("Executing script: {}", p);
            Object val = eval(p, topLevel);
            if (val != null)
                ret = val;
        }
        return ret;
    }

    // This class encapsulates the differences between GroovyShell and GroovyScriptEngineImpl.
    private static class GroovyEngine {
        GroovyShell shell;
        GroovyScriptEngineImpl scriptEngine;
        ScriptContext scriptContext;

        public GroovyEngine(ClassLoader classLoader, Properties properties, boolean useGroovyShell) {
            ClassLoader ctxLoader = classLoader;
            if (ctxLoader == null)
                ctxLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader == null)
                ctxLoader = Scripting.class.getClassLoader();

            CompilerConfiguration compilerConfig = new CompilerConfiguration();
            String scriptBaseClass = properties.getProperty(SCRIPT_BASE_CLASS);
            if (scriptBaseClass != null)
                compilerConfig.setScriptBaseClass(scriptBaseClass);
            GroovyClassLoader loader = new GroovyClassLoader(ctxLoader, compilerConfig);
            if (properties.getProperty(PATH) != null) {
                loader.addClasspath(properties.getProperty(PATH));
            }

            if (useGroovyShell) {
                Binding binding = new Binding();
                for (Map.Entry entry : properties.entrySet()) {
                    binding.setVariable(entry.getKey().toString(), entry.getValue());
                }
                shell = new GroovyShell(loader, binding, compilerConfig);
            } else {
                scriptEngine = new GroovyScriptEngineImpl(loader);
                scriptContext = new SimpleScriptContext();
                for (Map.Entry entry : properties.entrySet()) {
                    scriptContext.getBindings(ScriptContext.ENGINE_SCOPE).put(entry.getKey().toString(), entry.getValue());
                }
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
                if ("binding".equals(key))
                    return shell.getContext();
                return shell.getVariable(key);
            } else {
                if ("binding".equals(key))
                    return scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
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
                Map<Object, Object> binding = shell.getContext().getVariables();
                Properties properties = new Properties();
                for (Map.Entry key : binding.entrySet()) {
                    Object value = binding.get(key);
                    if (value != null)
                        properties.setProperty(key.toString(), value.toString());
                }
                return properties;
            } else {
                Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
                Properties properties = new Properties();
                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                    if (entry.getValue() != null)
                        properties.setProperty(entry.getKey(), entry.getValue().toString());
                }
                return properties;
            }
        }

        public Object eval(File file) throws ScriptException {
            try {
                if (shell != null) {
                    return shell.evaluate(file);
                } else {
                    try (Reader reader = new BufferedReader(new FileReader(file))) {
                        return scriptEngine.eval(reader, scriptContext);
                    }
                }
            } catch (IOException ex) {
                throw new ScriptException(ex);
            }
        }

        public Object eval(Reader reader, String fileName) throws ScriptException {
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

        private static String scriptName(String fileName) {
            Path path = Paths.get(fileName);
            Path file = path.getFileName();
            if (file.toString().endsWith(".groovy")) {
                int idx = file.toString().lastIndexOf('.');
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
}
