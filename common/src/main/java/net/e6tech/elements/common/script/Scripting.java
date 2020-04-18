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
import net.e6tech.elements.common.util.file.FileUtil;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;

import javax.script.ScriptException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
@SuppressWarnings({"unchecked", "squid:S00115", "squid:S134", "squid:S1192", "squid:S3400", "squid:S1075"})
public class Scripting {

    public static final String SCRIPT_BASE_CLASS = "scriptBaseClass";
    public static final String PATH = "script.path";
    public static final String __DIR = "__dir";
    public static final String __FILE = "__file";
    public static final String __LOAD_DIR = "__load_dir";
    public static final String __LOAD_FILE = "__load_file";
    public static final String __SCRIPT = "__script";
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

        script.engine = new GroovyEngine(classLoader, properties);

        return script;
    }

    public void shutdown() {
        engine.shutdown();
    }

    public boolean containsKey(String key) {
        return engine.containsKey(key);
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

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S2093", "squid:S3776", "squid:S2139"})
    // script is the full path name
    private Object internalExec(String script, boolean topLevel) throws ScriptException {
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
                reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
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
            // rethrow to let caller handle it, instead of logging error
            throw new ScriptException(e);
        } catch (ScriptException e) {
            logger.error("Error eval {}", script);
            throw e;
        } catch (Exception e) {
            logger.error("Error eval " + script, e);
            throw new SystemException(e.getMessage(), e);
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

    public Object eval(String script) {
        return engine.eval(script);
    }

    public Object eval(String script, boolean shouldCache) {
        return engine.eval(script, shouldCache);
    }

    public boolean isRunnable(Object obj) {
        return obj instanceof Runnable || obj instanceof Closure;
    }

    public Object runClosure(Object caller, Closure closure, Object ... args) {
        Object ret = null;
        try {
            final Closure clonedClosure = (Closure) closure.clone();
            try {
                clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
                clonedClosure.setDelegate(caller);
                ret = clonedClosure.call(args);
            } finally {
                clonedClosure.setDelegate(null);
            }
        } catch (Exception th) {
            logger.warn(th.getMessage(), th);
        }
        return ret;
    }

    public Object runNow(Object caller, Object callable) {
        Object ret = null;
        if (callable instanceof Closure) {
            runClosure(caller, (Closure) callable, (Object[]) null);
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

    /**
     * Run a script.  The script may trigger further calls to exec.  But, load should not be
     * be called again.
     *
     * @param loadDir use a different director as the load directory
     * @param path file path of the script to be load
     * @throws ScriptException throws exception if there are errors.
     */
    public void load(String loadDir, String path) throws ScriptException {
        String prevRootDir = (String) get(__LOAD_DIR);
        String prevRootFile = (String) get(__LOAD_FILE);
        try {
            String dir = (new File(loadDir)).getCanonicalPath();
            String file = (new File(path)).getCanonicalPath();
            privatePut(__LOAD_DIR, dir);
            privatePut(__FILE, file);
            exec(path, false);
            runAfter();
        } catch (IOException e) {
            throw new ScriptException(e);
        } finally {
            privatePut(__LOAD_DIR, prevRootDir);
            privatePut(__LOAD_FILE, prevRootFile);
        }
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
                if (owner instanceof Closure) {
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
            if ((paths == null || paths.length == 0) && !(path.endsWith("**") || path.endsWith("*")))
                throw new IOException("Script not found " + originalPath);
        } catch (IOException e) {
            throw new ScriptException(e);
        }

        Object ret = null;
        for (String p : paths) {
            if (!silent)
                logger.info("Executing script: {}", p);
            Object val = internalExec(p, topLevel);
            if (val != null)
                ret = val;
        }
        return ret;
    }

    public ClassLoader getScriptLoader() {
        return engine.getClassLoader();
    }

    // This class encapsulates the differences between GroovyShell and GroovyScriptEngineImpl.
    private static class GroovyEngine {
        GroovyShell shell;
        CompilerConfiguration compilerConfig;

        public GroovyEngine(ClassLoader classLoader, Properties properties) {
            ClassLoader ctxLoader = classLoader;
            if (ctxLoader == null)
                ctxLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader == null)
                ctxLoader = Scripting.class.getClassLoader();

            compilerConfig = new CompilerConfiguration();
            String scriptBaseClass = properties.getProperty(SCRIPT_BASE_CLASS);
            if (scriptBaseClass != null)
                compilerConfig.setScriptBaseClass(scriptBaseClass);
            GroovyClassLoader loader = new GroovyClassLoader(ctxLoader, compilerConfig);
            if (properties.getProperty(PATH) != null) {
                loader.addClasspath(properties.getProperty(PATH));
            }

            Binding binding = new Binding();
            for (Map.Entry entry : properties.entrySet()) {
                binding.setVariable(entry.getKey().toString(), entry.getValue());
            }
            shell = new GroovyShell(loader, binding, compilerConfig);

        }

        public void shutdown() {
            try {
                shell.getClassLoader().close();
            } catch (IOException e) {
                throw new SystemException(e);
            }
        }

        public boolean containsKey(String key) {
            return shell.getContext().getVariables().containsKey(key);
        }

        public void put(String key, Object val) {
            shell.setVariable(key, val);
        }

        public Map<String, Object> getVariables() {
            Map<String, Object> binding;
            binding = shell.getContext().getVariables();

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
            if ("binding".equals(key))
                return shell.getContext();
            return shell.getVariable(key);
        }

        public Object remove(String key) {
            return shell.getContext().getVariables().remove(key);
        }

        public Properties getProperties() {
            Map<Object, Object> binding = shell.getContext().getVariables();
            Properties properties = new Properties();
            for (Map.Entry key : binding.entrySet()) {
                Object value = binding.get(key);
                if (value != null)
                    properties.setProperty(key.toString(), value.toString());
            }
            return properties;

        }

        public Object eval(File file) throws ScriptException {
            Script previous = (Script) get(__SCRIPT);
            try {
                GroovyCodeSource codeSource = new GroovyCodeSource(file, compilerConfig.getSourceEncoding());
                Script script = shell.parse(codeSource);
                put(__SCRIPT, script);
                return script.run();
            } catch (IOException ex) {
                throw new ScriptException(ex);
            } finally {
                if (previous != null)
                    put(__SCRIPT, previous);
                else
                    remove(__SCRIPT);
            }
        }

        public Object eval(Reader reader, String fileName) {
            Script previous = (Script) get(__SCRIPT);
            Script script = null;
            try {
                script = shell.parse(reader, scriptName(fileName));
                put(__SCRIPT, script);
                return script.run();
            } finally {
                if (script != null) {
                    InvokerHelper.removeClass(script.getClass());
                }
                if (previous != null)
                    put(__SCRIPT, previous);
                else
                    remove(__SCRIPT);
            }
        }

        public Object eval(String scriptText) {
            Script previous = (Script) get(__SCRIPT);
            try {
                Script script = shell.parse(scriptText);
                put(__SCRIPT, script);
                return script.run();
            } finally {
                if (previous != null)
                    put(__SCRIPT, previous);
                else
                    remove(__SCRIPT);
            }
        }

        public Object eval(String scriptText, boolean shouldCache) {
            Script previous = (Script) get(__SCRIPT);
            try {
                GroovyCodeSource codeSource = new GroovyCodeSource(scriptText, "Scripting_Eval.groovy", GroovyShell.DEFAULT_CODE_BASE);
                Class cls = shell.getClassLoader().parseClass(codeSource, shouldCache);
                Script script = InvokerHelper.createScript(cls, shell.getContext());
                put(__SCRIPT, script);
                return script.run();
            } finally {
                if (previous != null)
                    put(__SCRIPT, previous);
                else
                    remove(__SCRIPT);
            }
        }

        public ClassLoader getClassLoader() {
            Script script = (Script) get(__SCRIPT);
            if (script != null) {
                return script.getMetaClass().getTheClass().getClassLoader();
            }
            return shell.getClassLoader();
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
