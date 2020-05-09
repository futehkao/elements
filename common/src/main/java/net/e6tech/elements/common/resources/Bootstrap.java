/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.resources;

import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyRuntimeException;
import groovy.util.Expando;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.script.Scripting;
import net.e6tech.elements.common.util.InitialContextFactory;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.Terminal;
import net.e6tech.elements.common.util.concurrent.ThreadPool;
import org.apache.logging.log4j.ThreadContext;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

@SuppressWarnings({"unchecked", "squid:S3776"})
public class Bootstrap extends GroovyObjectSupport {
    private static final String LINE_SEPARATOR = "***********************************************************";
    private static Logger logger = Logger.getLogger();
    private static final String PRE_BOOT = "preBoot";
    private static final String POST_BOOT = "postBoot";
    private static final String BOOT_ENV = "bootEnv";
    private static final String BOOT_AFTER = "bootAfter";
    private static final String BOOT_DISABLE_LIST = "bootDisableList";
    private static final String BOOT_ENABLE_LIST = "bootEnableList";
    private static final String PLUGIN_DIRECTORIES = "pluginDirectories";
    private static final String PROVISION_CLASS = "provisionClass";
    private static final String HOST_ENVIRONMENT_FILE = "hostEnvironmentFile";
    private static final String HOST_SYSTEM_PROPERTIES_FILE = "hostSystemPropertiesFile";
    private static final String ENVIRONMENT = "environment";
    private static final String SYSTEM_PROPERTIES = "systemProperties";
    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private String  bootstrapDir = ".";
    private String defaultEnvironmentFile;
    private String defaultSystemProperties;
    private int bootIndex = 0;
    private List initBoot = new ArrayList();
    private Map preBoot = new LinkedHashMap();
    private Map main = new LinkedHashMap();
    private Map postBoot = new LinkedHashMap();
    private Map after = new LinkedHashMap();
    private ResourceManager resourceManager;
    private MyExpando expando = new MyExpando();
    private Set<String> disableList = new LinkedHashSet<>();
    private Set<String> enableList = new LinkedHashSet<>();
    private Set bootComponents = new HashSet();
    private boolean bootEnv = false;
    private boolean bootProvision = false;
    private boolean bootInit = false;
    private List<BootstrapListener> listeners = new ArrayList<>();

    public Bootstrap(ResourceManager rm) {
        this.resourceManager = rm;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public Map getMain() {
        return main;
    }

    public void setMain(Map main) {
        this.main = main;
        main.keySet().forEach(key -> setComponent(key, false));
    }

    public String getDir() {
        return bootstrapDir;
    }

    public void setDir(String dir) {
        bootstrapDir = dir;
    }

    public String getDefaultEnvironmentFile() {
        return defaultEnvironmentFile;
    }

    public void setDefaultEnvironmentFile(String defaultEnvironmentFile) {
        this.defaultEnvironmentFile = defaultEnvironmentFile;
    }

    public String getDefaultSystemProperties() {
        return defaultSystemProperties;
    }

    public void setDefaultSystemProperties(String defaultSystemProperties) {
        this.defaultSystemProperties = defaultSystemProperties;
    }

    public List<String> getInitBoot() {
        return initBoot;
    }

    public void setInitBoot(List initBoot) {
        this.initBoot = initBoot;
    }

    public Bootstrap initBoot(List initBoot) {
        if (this.initBoot == null) {
            this.initBoot = initBoot;
        } else {
            this.initBoot.addAll(initBoot);
        }
        return this;
    }

    public List<String> getInit() {
        return getInitBoot();
    }

    public void setInit(List initBoot) {
        setInitBoot(initBoot);
    }

    public Map getAfter() {
        return after;
    }

    public void setAfter(Map after) {
        this.after = after;
        after.keySet().forEach(key -> setComponent(key, false));
    }

    public List<ComponentInfo> componentInfos() {
        List<ComponentInfo> list = new ArrayList<>();
        preBoot.forEach((key, value) -> list.add(this.componentInfo(PRE_BOOT, key, value)));
        main.forEach((key, value) -> list.add(this.componentInfo("main", key, value)));
        postBoot.forEach((key, value) -> list.add(this.componentInfo(POST_BOOT, key, value)));
        after.forEach((key, value) -> list.add(this.componentInfo(BOOT_AFTER, key, value)));
        return list;
    }

    private ComponentInfo componentInfo(String stage, Object key, Object value) {
        ComponentInfo info = new ComponentInfo();
        info.stage = stage;
        if (key instanceof Closure) {
            info.name = value.toString();
            Closure closure = (Closure) key;
            if (closure.isCase(EMPTY_OBJECT_ARRAY)) {
                info.enabled = true;
            } else {
                info.enabled = false;
            }
        } else {
            info.name = key.toString();
            Object on = expando.getProperty(key.toString());
            if (Boolean.TRUE.equals(on)) {
                info.enabled = true;
            } else {
                info.enabled = false;
            }
        }
        return info;
    }

    public void addBootstrapListener(BootstrapListener listener) {
        listeners.add(listener);
    }

    public boolean removeBootstrapListener(BootstrapListener listener) {
        return listeners.remove(listener);
    }

    public void addBootstrapBeginEnv(BootstrapBeginEnv listener) {
        listeners.add(listener);
    }

    public void addBootstrapEndEnv(BootstrapEndEnv listener) {
        listeners.add(listener);
    }

    public void addBootstrapSystemPropertiesListener(BootstrapSystemPropertiesListener listener) {
        listeners.add(listener);
    }

    public Map getBootEnv() {
        Object bootEnvOverride = getVar(BOOT_ENV);
        if (bootEnvOverride instanceof Map) {
            return (Map) bootEnvOverride;
        }
        return null;
    }

    public void setBootEnv(Map map) {
        if (map == null)
            return;
        Map override = getBootEnv();
        if (override != null) {
            override.putAll(map);
        } else {
            override = map;
            resourceManager.getScripting().put(BOOT_ENV, override);
        }
    }

    private void setComponent(Object key, boolean on) {
        if (key instanceof Closure) {
            Closure closure = (Closure) key;
            closure.setDelegate(expando);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        } else {
            String propertyName = key.toString();
            expando.setProperty(propertyName, on);
        }
    }

    private void bootMessage(String message) {
        String line = LINE_SEPARATOR;
        if (logger.isInfoEnabled()) {
            logger.info(line);
            logger.info(message);
            logger.info(line);
        }
    }

    public Bootstrap enable(String ... components) {
        if (components != null) {
            for (String component : components) {
                enableList.add(component);
                disableList.remove(component);
                expando.setProperty(component, true);
            }
        }
        return this;
    }

    public Bootstrap disable(String ... components) {
        if (components != null) {
            for (String component : components) {
                disableList.add(component);
                enableList.remove(component);
                expando.setProperty(component, false);
            }
        }
        return this;
    }

    public void setPreBoot(Object pre) {
        setupBootList(pre, preBoot);
    }

    public Bootstrap preBoot(Object pre) {
        setupBootList(pre, preBoot);
        return this;
    }

    public void setPostBoot(Object post) {
        setupBootList(post, postBoot);
    }

    public Bootstrap postBoot(Object post) {
        setupBootList(post, postBoot);
        return this;
    }

    /** The boot order is
        exec boot script
        boot env
        boot provision
        boot init
        pre boot
        boot main
        post boot
        boot after
     **/
    public Bootstrap boot(Object bootScript, Object ... components) {
        if (bootScript != null)
            exec(bootScript);

        // boot env
        if (main.isEmpty() && after.isEmpty()) {
            logger.warn("Components not configured.  Use main or after to configure components.");
        }

        bootEnvironment();
        bootProvision();
        bootInitialContext();

        // configure boot after if arg in components is a Map
        if (components != null) {
            for (Object component : components) {
                if (component instanceof Map) {
                    Map map = (Map) component;
                    map.forEach((key, value) -> {
                        after.put(key, value);
                        setComponent(key, true); // default to on unless they are turn off by disable list during bootEnv()
                    });
                }
            }
        }

        // configure boot main
        if (components != null) {
            for (Object component : components) {
                if (component instanceof Map)
                    continue;

                if (after.get(component) == null && main.get(component) == null) {
                    main.put(component, bootstrapDir + File.separator + component);
                }
                expando.setProperty(component.toString(), true);
            }
        }

        if (disableList != null) {
            for (String component : disableList) {
                expando.setProperty(component, false);
            }
        }

        if (enableList != null) {
            for (String component : enableList) {
                expando.setProperty(component, true);
            }
        }

        listeners.forEach( l -> l.beginBoot(this));

        // boot initialization
        if (initBoot != null && !initBoot.isEmpty()) {
            initBoot();  // set by bootstrap script
        }

        // preBoot
        if (preBoot != null && !preBoot.isEmpty()) {
            preBoot();  // set by launch script
        }

        // boot Components
        if (main != null && !main.isEmpty()) {
            bootMain();  // set by bootstrap script
        }

        // postBoot
        if (postBoot != null && !postBoot.isEmpty()) {
            postBoot();  // set by launch script
        }

        // boot after
        if (after != null && !after.isEmpty()) {
            bootAfter(); // set by bootstrap script
        }

        listeners.forEach( l -> l.endBoot(this));

        bootMessage("Booting completed");

        // After this point, additional scripts are run by the launch script via exec ResourceManagerScript

        return this;
    }

    private void bootEnvironment() {
        if (bootEnv)
            return;

        bootMessage("Loading environment");
        listeners.forEach( l -> l.beginEnv(this));

        // default environment, either explicit set as defaultEnvironmentFile or an accompanied file when boot is called
        if (defaultEnvironmentFile != null) {
            exec(defaultEnvironmentFile);
        } else {
            String script = getVar(Scripting.__DIR) + "/environment.groovy";
            File file = new File(script);
            if (file.exists()) {
                exec(script);
            } else {
                logger.warn("!! No default environment script.");
            }
        }

        // startup script's environment
        String envFile = getVar(Scripting.__LOAD_DIR) + "/environment.groovy";
        if (getVar(ENVIRONMENT) != null) {
            envFile = getVar(ENVIRONMENT).toString();
        }
        tryExec(envFile);

        // bootEnv override
        Object bootEnvOverride = getVar(BOOT_ENV);
        if (bootEnvOverride instanceof Map) {
            Map<String, Object> map = (Map) bootEnvOverride;
            map.forEach((key, val) -> resourceManager.getScripting().put(key, val));
        }

        // host environment file, e.g. /usr/local/e6tech/environment.groovy
        if (getVar(HOST_ENVIRONMENT_FILE) != null) {
            envFile = getVar(HOST_ENVIRONMENT_FILE).toString();
            tryExec(envFile);
        }

        listeners.forEach( l -> l.beginSystemProperties(this));

        // default system properties, either explicit set as defaultSystemProperties or an accompanied file when boot is called
        if (defaultSystemProperties != null)
            exec(defaultSystemProperties);
        else {
            String script = getVar(Scripting.__DIR) + "/system_properties.groovy";
            tryExec(script);
        }

        // startup script's system properties
        String sysFile = getVar(Scripting.__LOAD_DIR) + "/system_properties.groovy";
        if (getVar(SYSTEM_PROPERTIES) != null) {
            sysFile = getVar(SYSTEM_PROPERTIES).toString();
        }
        tryExec(sysFile);

        // host system properties, e.g. /usr/local/e6tech/system_properties.groovy
        if (getVar(HOST_SYSTEM_PROPERTIES_FILE) != null) {
            sysFile = getVar(HOST_SYSTEM_PROPERTIES_FILE).toString();
            tryExec(sysFile);
        }

        listeners.forEach( l -> l.endSystemProperties(this));

        // log4j
        ThreadContext.put(ResourceManager.LOG_DIR_ABBREV, System.getProperty(Logger.logDir));
        logger.info("-> Log4J log4j.configurationFile={}", System.getProperty("log4j.configurationFile"));

        if (getVar(PRE_BOOT) != null) {
            Object p = getVar(PRE_BOOT);
            setupBootList(p, preBoot);
        }

        if (getVar(POST_BOOT) != null) {
            Object p = getVar(POST_BOOT);
            setupBootList(p, postBoot);
        }

        if (getVar(BOOT_DISABLE_LIST) != null) {
            Object p = getVar(BOOT_DISABLE_LIST);
            setupDisableList(p);
        }

        if (getVar(BOOT_DISABLE_LIST) != null) {
            Object p = getVar(BOOT_ENABLE_LIST);
            setupEnableList(p);
        }

        if (getVar(BOOT_AFTER) != null) {
            if (!(getVar(BOOT_AFTER) instanceof Map)) {
                throw new SystemException("Expecting variable " + BOOT_AFTER + " to be a Map instead of " + getVar(BOOT_AFTER).getClass());
            }
            Map p = (Map) getVar(BOOT_AFTER);
            p.forEach((key, value) -> {
                after.put(key, value);
                setComponent(key, true);
            });
        }

        listeners.forEach( l -> l.endEnv(this));

        bootEnv = true;
        logger.info("Done loading environment **********************************\n");
    }

    private void setupDisableList(Object p) {
        if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  disable(l.toString()));
        } else if (p != null) {
            disable(p.toString());
        }
    }

    private void setupEnableList(Object p) {
        if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  enable(l.toString()));
        } else if (p != null) {
            enable(p.toString());
        }
    }

    private void setupBootList(Object p, Map bootList) {
        if (p instanceof Map) {
            Map map = (Map) p;
            map.forEach((key, value) -> {
                if (main.get(key) != null || after.get(key) != null) {  // see if item is a name to main or after
                    boolean on = true;
                    Object ret = runObject(value);
                    if (ret == null)
                        on = false;
                    else if (ret instanceof String || ret instanceof GString) {
                        on = "true".equalsIgnoreCase(ret.toString()) || "t".equalsIgnoreCase(ret.toString());
                    } else if (ret instanceof Boolean) {
                        on = (Boolean) ret;
                    }
                    expando.setProperty(key.toString(), on);
                } else {
                    bootList.put(key, value);
                    setComponent(key, true);
                }
            });
        } else if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  {
                expando.setProperty(l.toString(), true);
                updateBootList(l, bootList);
            });
        } else if (p != null) {
            updateBootList(p, bootList);
        }
    }

    private void updateBootList(Object p, Map bootList) {
        if (main.get(p) == null && after.get(p) == null) {  // see if item is a name to main or after
            String key = "anonymous-" + ++bootIndex;
            expando.setProperty(key, true);
            bootList.put(key, p);
        } else {
            expando.setProperty(p.toString(), true);
        }
    }

    private void bootProvision() {
        if (bootProvision)
            return;

        Class provisionClass = Provision.class;
        if (getVar(PROVISION_CLASS) != null) {
            provisionClass = (Class) getVar(PROVISION_CLASS);
        }
        resourceManager.loadProvision(provisionClass);
        if (getVar(PLUGIN_DIRECTORIES) != null) {
            Object dir = getVar(PLUGIN_DIRECTORIES);
            String[] pluginDirectories = new String[0];
            if (dir instanceof Collection) {
                Collection collection = (Collection) dir;
                pluginDirectories = new String[collection.size()];
                Iterator iterator = collection.iterator();
                int idx = 0;
                while (iterator.hasNext()) { // doing this to account for GString in groovy
                    pluginDirectories[idx] = iterator.next().toString();
                    idx ++;
                }
            } else if (dir instanceof Object[]) { // doing this to account for GString in groovy
                Object[] array = (Object[]) dir;
                pluginDirectories = new String[array.length];
                for (int i = 0; i < pluginDirectories.length; i++) {
                    pluginDirectories[i] = array[i].toString();
                }
            }
            resourceManager.getPluginManager().loadPlugins(pluginDirectories);
        }
        bootProvision = true;
    }

    private void bootInitialContext() {
        InitialContextFactory.setDefault();
    }

    private void tryExec(String path) {
        try {
            resourceManager.getScripting().exec(path);
        } catch (ScriptException e) {
            if (e.getCause() instanceof IOException) {
                logger.info("-> Script {} not processed: {}", path, e.getCause().getMessage());
            } else {
                logger.warn("!! Script not processed due to error.", e);
            }
        }
    }

    private void initBoot() {
        if (bootInit)
            return;
        bootMessage("Boot initialization");
        initBoot.forEach(this::exec);
        logger.info("Done pre-booting ******************************************\n");
        bootInit = true;
    }

    private void bootMain() {
        bootMessage("Booting main");
        main.forEach(this::runComponent);
        logger.info("Done booting components **********************************\n");
    }

    private void preBoot() {
        bootMessage("Pre-booting");
        preBoot.forEach(this::runComponent);
        logger.info("Done pre-booting ******************************************\n");
    }

    private void postBoot() {
        bootMessage("Post-booting");
        postBoot.forEach(this::runComponent);
        logger.info("Done post-booting ******************************************\n");
    }

    private void bootAfter() {
        bootMessage("Boot after");
        after.forEach(this::runComponent);
        logger.info("Done boot after ********************************************\n");
    }

    public Bootstrap after(Map map) {
        map.forEach((key, value) -> {
            after.put(key, value);
            setComponent(key, true); // default to on unless they are turn off by disable list during bootEnv()
        });
        if (disableList != null) {
            for (String component : disableList) {
                expando.setProperty(component, false);
            }
        }
        bootAfter();
        return this;
    }

    private void runComponentMessage(String message) {
        final String line = "    =======================================================";
        if (logger.isInfoEnabled()) {
            logger.info(line);
            logger.info(message);
        }
    }

    private void runComponent(Object key, Object value) {
        if (key == null || bootComponents.contains(key.toString()))
            return;
        if (key instanceof Closure) {
            Closure closure = (Closure) key;
            runComponentMessage("    Running closure " + closure.toString());
            if (closure.isCase(EMPTY_OBJECT_ARRAY)) {
                exec(value);
            } else {
                if (logger.isInfoEnabled())
                    logger.info("    !! Closure returns false, skipped running {}", value);
            }
            if (logger.isInfoEnabled()) {
                logger.info("    Done running {}", closure);
            }
        } else {
            Object on = expando.getProperty(key.toString());
            if (Boolean.TRUE.equals(on)) {
                runComponentMessage("    Booting *" + key + "*");
                if (value instanceof Map) {
                    ((Map) value).forEach(this::runComponent);
                } else {
                    exec(value);
                }
            } else {
                logger.info("    !! {} is disabled", key);
            }
            if (logger.isInfoEnabled()) {
                logger.info("    Done booting *{}*", key);
            }
        }
        logger.info("    -------------------------------------------------------\n");
        bootComponents.add(key.toString());
    }

    private void exec(Object obj) {
        if (obj instanceof Collection) {
            Collection collection = (Collection) obj;
            Iterator iterator = collection.iterator();
            while (iterator.hasNext()) {
                Object script = iterator.next();
                runObject(script);
            }
        } else {
            runObject(obj);
        }
    }

    private Object runObject(Object obj) {
        if (obj == null)
            return null;
        try {
            if (obj instanceof Closure) {
                Closure closure = (Closure) obj;
                closure.setDelegate(expando);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                return closure.call(this);
            } else if (obj instanceof String || obj instanceof GString ){
                return resourceManager.getScripting().exec(obj.toString());
            } else {
                return obj;
            }
        } catch (ScriptException ex) {
            throw new SystemException(ex);
        }
    }

    private Object getVar(String var) {
        return resourceManager.getScripting().get(var);
    }

    // convenient method for initBoot
    public void setupThreadPool(String threadPoolName) {
        ThreadPool threadPool = ThreadPool.cachedThreadPool(threadPoolName);

        // resourceManager is a special case
        // that you cannot use component to inject instances.
        logger.info(LINE_SEPARATOR);
        logger.info("Setting up thread pool {}", threadPoolName);
        logger.info(LINE_SEPARATOR);
        resourceManager.registerBean(threadPoolName, threadPool);
        resourceManager.bind(ThreadPool.class, resourceManager.getBean(threadPoolName));
    }

    // send a shutdown message to a server
    public void shutdown(int shutdownPort) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), shutdownPort)) {
            PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            output.println("shutdown");
            output.flush();
        } catch (Exception ex) {
            logger.warn("No local server found at port={}", shutdownPort);
        }
        System.exit(0);
    }

    // starting a shutdown listener
    public void startShutdownListener(int shutdownPort) {
        logger.info(LINE_SEPARATOR);
        logger.info("Starting server shutdown listening thread");
        logger.info(LINE_SEPARATOR);
        Thread thread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(shutdownPort, 0, InetAddress.getLoopbackAddress())) {
                while (true) {
                    Terminal terminal = new Terminal(serverSocket);
                    if (terminal.readLine("").equals("shutdown")) {
                        logger.info("Received shutdown request.  Shutting down ... ");
                        resourceManager.shutdown();
                        System.exit(0);
                    }
                }
            } catch (IOException ex) {
                logger.warn("Unable to start shutdown listener", ex);
            }
        });
        thread.start();
    }

    private class MyExpando extends Expando {

        @Override
        public Object invokeMethod(String name, Object args) {
            try {
                return getMetaClass().invokeMethod(this, name, args);
            } catch (GroovyRuntimeException e) {
                // br should get a "native" property match first. getProperty includes such fall-back logic
                Object value = super.getProperty(name);
                if (value instanceof Closure) {
                    Closure closure = (Closure) value;
                    closure = (Closure) closure.clone();
                    closure.setDelegate(this);
                    return closure.call((Object[]) args);
                } else {
                    throw e;
                }
            }
        }

        @Override
        public Object getProperty(String property) {
            // always use the expando properties first
            Object result = getProperties().get(property);
            if (result != null) return result;
            return getMetaClass().getProperty(this, property);
        }

        public void enable(String ... components) {
            if (components != null)
                for (String component : components)
                    setProperty(component, true);
        }

        public void disable(String ... components) {
            if (components != null)
                for (String component : components)
                    setProperty(component, false);
        }
    }

    public static class ComponentInfo {
        private String stage;
        private String name;
        private boolean enabled;

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
