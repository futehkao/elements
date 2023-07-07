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
import net.e6tech.elements.common.util.concurrent.ThreadLocalMap;
import net.e6tech.elements.common.util.file.FileUtil;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;

import javax.script.ScriptException;
import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private static final Logger logger = Logger.getLogger();
    private static final Set<String> reservedKeyWords = new HashSet<>();

    private static final ThreadLocal<ScriptPath> scriptPath = new InheritableThreadLocal<>();

    static {
        reservedKeyWords.add(__DIR);
        reservedKeyWords.add(__FILE);
    }

    private GroovyEngine engine;
    private List runAfterList = new LinkedList<>();
    private List launchedList = new LinkedList<>();
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
        putPrivate(key, val);
    }

    public void putPrivate(String key, Object val) {
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

        ScriptPath prev = scriptPath.get();
        ScriptPath current = new ScriptPath(normalizePath(script));
        scriptPath.set(current);
        Reader reader = null;
        try {
            String dir = current.getParent();
            String file;

            if (current.isClassPath()) {
                InputStream stream = engine.shell.getClassLoader().getResourceAsStream(current.getFileName());
                if (stream == null)
                    throw new IOException("File not found: " + current.getClassPath());
                reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                file = current.getFileName();
            } else {
                File f = new File(current.getFileName());
                if (!f.exists())
                    throw new IOException("File not found: " + current.getFileName());
                dir = (new File(dir)).getCanonicalPath();
                file = f.getCanonicalPath();
            }

            engine.putThreadLocal(__DIR, dir);
            engine.putThreadLocal(__FILE, file);
            if (topLevel) {
                putPrivate(__LOAD_DIR, dir);
                putPrivate(__LOAD_FILE, file);
            }

            // reader is not null for classpath
            if (reader != null) {
                return engine.eval(reader, current.getFileName());
            } else {
                return engine.eval(current.getPath().toFile());
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

            if (prev != null) {
                scriptPath.set(prev);
                engine.putThreadLocal(__DIR, prev.getParent());
                engine.putThreadLocal(__FILE, prev.getFileName());
            } else {
                scriptPath.remove();
                engine.putThreadLocal(__DIR, null);
                engine.putThreadLocal(__FILE, null);
            }

            if (topLevel) {
                putPrivate(__LOAD_DIR, prevRootDir);
                putPrivate(__LOAD_FILE, prevRootFile);
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
        exec(path, true, false);
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
            putPrivate(__LOAD_DIR, dir);
            putPrivate(__LOAD_FILE, file);
            exec(path, false, false);
            runAfter();
        } catch (IOException e) {
            throw new ScriptException(e);
        } finally {
            putPrivate(__LOAD_DIR, prevRootDir);
            putPrivate(__LOAD_FILE, prevRootFile);
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

    public Scripting addClasspath(String path) {
        ((GroovyClassLoader) getScriptLoader()).addClasspath(path);
        return this;
    }

    public Scripting parseClass(String path) throws ScriptException {
        return parseClass(path, false);
    }

    public Scripting parseClass(String path, boolean cache) throws ScriptException {
        String[] paths;
        try {
            String normalized = normalizePath(path);
            paths = FileUtil.listFiles(engine.shell.getClassLoader(), normalized, getExtension());
            if ((paths == null || paths.length == 0) && !(path.endsWith("**") || path.endsWith("*")))
                throw new IOException("Script not found " + path);
        } catch (IOException e) {
            throw new ScriptException(e);
        }

        for (String p : paths) {
            try {
                GroovyCodeSource codeSource = new GroovyCodeSource(new File(p), engine.compilerConfig.getSourceEncoding());
                engine.shell.getClassLoader().parseClass(codeSource, cache);
            } catch (IOException ex) {
                throw new ScriptException(ex);
            }
        }
        return this;
    }

    public Object exec(String path) throws ScriptException {
        return exec(path, false, false);
    }

    public Object parallel(String path) throws ScriptException {
        return exec(path, false, true);
    }

    private Object exec(String originalPath, boolean topLevel, boolean parallel) throws ScriptException {
        String[] paths;
        try {
            String path = normalizePath(originalPath);
            paths = FileUtil.listFiles(engine.shell.getClassLoader(), path, getExtension());
            if ((paths == null || paths.length == 0) && !(path.endsWith("**") || path.endsWith("*")))
                throw new IOException("Script not found " + originalPath);
        } catch (IOException e) {
            throw new ScriptException(e);
        }

        if (parallel && paths.length > 1) {
            List<Object> rets = new ArrayList<>(paths.length);
            List<Thread> threads = new ArrayList<>();
            AtomicReference<ScriptException> exception = new AtomicReference<>(null);
            for (String p : paths) {
                threads.add(new Thread(() -> {
                    if (!silent)
                        logger.info("Executing script: {}", p);
                    try {
                        Object val = internalExec(p, topLevel);
                        rets.add(val);
                    } catch (ScriptException e) {
                        if (exception.get() == null)
                            exception.set(e);
                    } finally {
                        engine.clearThreadContext();
                    }
                }));
            }
            threads.forEach(Thread::start);
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (exception.get() != null)
                throw exception.get();
            return rets;
        } else {
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
    }

    public ClassLoader getScriptLoader() {
        return engine.getClassLoader();
    }

    private static class SoftVariables extends SoftReference<Map<String, Object>> {
        Object lastUpdate;

        public SoftVariables(Map<String, Object> referent, Object lastUpdate) {
            super(referent);
            this.lastUpdate = lastUpdate;
        }
    }

    // This class encapsulates the differences between GroovyShell and GroovyScriptEngineImpl.
    private static class GroovyEngine {
        GroovyShell shell;
        CompilerConfiguration compilerConfig;
        private final ThreadLocal<SoftVariables> localVars = new ThreadLocal<>();

        public GroovyEngine(ClassLoader classLoader, Properties properties) {
            ClassLoader ctxLoader = classLoader;
            if (ctxLoader == null)
                ctxLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader == null)
                ctxLoader = Scripting.class.getClassLoader();

            compilerConfig = new CompilerConfiguration();
            setCompilerConfig(properties, "groovy.source.encoding", compilerConfig::setSourceEncoding, "UTF-8");
            setCompilerConfig(properties, "groovy.parameters", value -> compilerConfig.setParameters(Boolean.parseBoolean(value)));
            setCompilerConfig(properties, "groovy.preview.features", value -> compilerConfig.setPreviewFeatures(Boolean.parseBoolean(value)));
            setCompilerConfig(properties, "groovy.target.directory", value -> compilerConfig.setTargetDirectory(new File(value)));
            setCompilerConfig(properties, "groovy.target.bytecode", compilerConfig::setTargetBytecode);
            setCompilerConfig(properties, "groovy.default.scriptExtension", compilerConfig::setDefaultScriptExtension);

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
            binding = new Binding(new ThreadLocalMap(binding.getVariables()));
            shell = new GroovyShell(loader, binding, compilerConfig);
        }

        private void setCompilerConfig(Properties properties, String key, Consumer<String> consumer) {
            if (properties.containsKey(key))
                consumer.accept(properties.getProperty(key));
        }

        private void setCompilerConfig(Properties properties, String key, Consumer<String> consumer, String defaultValue) {
            if (properties.containsKey(key))
                consumer.accept(properties.getProperty(key));
            else if (System.getProperty(key) == null && defaultValue != null)
                consumer.accept(defaultValue);
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

        public void putThreadLocal(String key, Object val) {
            if (getBinding().getVariables() instanceof ThreadLocalMap) {
                ((ThreadLocalMap)getBinding().getVariables()).putThreadLocal(key, val);
            } else {
                put(key, val);
            }
        }

        // getVariables is different from a direct get in that GString values, not keys, are
        // converted to String values.
        public Map<String, Object> getVariables() {
            Map<String, Object> binding = shell.getContext().getVariables();
            if (binding instanceof ThreadLocalMap) {
                ThreadLocalMap<String, Object> map = (ThreadLocalMap<String, Object>) binding;
                SoftVariables ref = localVars.get();
                Map<String, Object> variables = ref != null ? ref.get() : null;
                if (variables == null || ref.lastUpdate != map.lastUpdate() || map.isDirty()) {
                    map.size(); // force a merge.
                    ref = new SoftVariables(normalizeLocalVars(), map.lastUpdate());
                    variables = ref.get();
                    localVars.set(ref);
                }
                return variables;
            }

            return normalizeLocalVars();
        }

        private Map<String, Object> normalizeLocalVars() {
            Map<String, Object> variables = new LinkedHashMap<>();
            Map<String, Object> binding = shell.getContext().getVariables();
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

        public void removeThreadLocal(String key) {
            if (getBinding().getVariables() instanceof ThreadLocalMap) {
                ((ThreadLocalMap)getBinding().getVariables()).removeThreadLocal(key);
            } else {
                remove(key);
            }
        }

        public void clearThreadContext() {
            if (shell.getContext().getVariables() instanceof ThreadLocalMap) {
                ((ThreadLocalMap) shell.getContext().getVariables()).clearThreadLocal();
            }
        }

        public Binding getBinding() {
            return shell.getContext();
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
                putThreadLocal(__SCRIPT, script);
                return script.run();
            } catch (IOException ex) {
                throw new ScriptException(ex);
            } finally {
                if (previous != null)
                    putThreadLocal(__SCRIPT, previous);
                else
                    removeThreadLocal(__SCRIPT);
            }
        }

        public Object eval(Reader reader, String fileName) {
            Script previous = (Script) get(__SCRIPT);
            Script script = null;
            try {
                script = shell.parse(reader, scriptName(fileName));
                putThreadLocal(__SCRIPT, script);
                return script.run();
            } finally {
                if (script != null) {
                    InvokerHelper.removeClass(script.getClass());
                }
                if (previous != null)
                    putThreadLocal(__SCRIPT, previous);
                else
                    removeThreadLocal(__SCRIPT);
            }
        }

        public Object eval(String scriptText) {
            Script previous = (Script) get(__SCRIPT);
            try {
                Script script = shell.parse(scriptText);
                putThreadLocal(__SCRIPT, script);
                return script.run();
            } finally {
                if (previous != null)
                    putThreadLocal(__SCRIPT, previous);
                else
                    removeThreadLocal(__SCRIPT);
            }
        }

        public Object eval(String scriptText, boolean shouldCache) {
            Script previous = (Script) get(__SCRIPT);
            try {
                GroovyCodeSource codeSource = new GroovyCodeSource(scriptText, "Scripting_Eval.groovy", GroovyShell.DEFAULT_CODE_BASE);
                Class cls = shell.getClassLoader().parseClass(codeSource, shouldCache);
                Script script = InvokerHelper.createScript(cls, shell.getContext());
                putThreadLocal(__SCRIPT, script);
                return script.run();
            } finally {
                if (previous != null)
                    putThreadLocal(__SCRIPT, previous);
                else
                    removeThreadLocal(__SCRIPT);
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
