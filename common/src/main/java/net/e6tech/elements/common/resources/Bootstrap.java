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
import groovy.lang.GroovyObjectSupport;
import groovy.util.Expando;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.script.Scripting;
import net.e6tech.elements.common.util.InitialContextFactory;
import net.e6tech.elements.common.util.SystemException;
import org.apache.logging.log4j.ThreadContext;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Bootstrap extends GroovyObjectSupport {
    private static final String PRE_BOOT = "preBoot";
    private static final String POST_BOOT = "postBoot";
    private static final String BOOT_ENV = "bootEnv";
    private static final String PLUGIN_DIRECTORIES = "pluginDirectories";
    private static final String PROVISION_CLASS = "provisionClass";
    private static final String HOST_ENVIRONMENT_FILE = "hostEnvironmentFile";
    private static final String HOST_SYSTME_PROPERTIES_FILE = "hostSystemPropertiesFile";
    private static final String ENVIRONMENT = "environment";
    private static final String SYSTEM_PROPERTIES = "systemProperties";
    private static Logger logger = Logger.getLogger();
    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private String  bootstrapDir = ".";
    private String defaultEnvironmentFile;
    private String defaultSystemProperties;
    private List initBoot = new ArrayList();
    private List preBoot = new ArrayList();
    private Map main = new LinkedHashMap();
    private List postBoot = new ArrayList();
    private Map after = new LinkedHashMap();
    private ResourceManager resourceManager;
    private Expando expando = new Expando();
    private boolean envInitialized = false;

    public Bootstrap(ResourceManager rm) {
        this.resourceManager = rm;
    }

    public Map getMain() {
        return main;
    }

    public void setMain(Map main) {
        this.main = main;
        main.forEach(this::setComponent);
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

    public Map getAfter() {
        return after;
    }

    public void setAfter(Map after) {
        this.after = after;
        after.forEach(this::setComponent);
    }

    private void setComponent(Object key, Object value) {
        if (key instanceof Closure) {
            Closure closure = (Closure) key;
            closure.setDelegate(expando);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        } else {
            String propertyName = key.toString();
            expando.setProperty(propertyName, false);
        }
    }

    public void boot(String ... components) {
        // boot env
        logger.info("***********************************************************");
        logger.info("Loading environment");
        logger.info("***********************************************************");
        bootEnv();
        logger.info("Done loading environment **********************************\n");

        bootProvision();

        bootInitialContext();

        if (components != null) {
            for (String component : components) {
                if (after.get(component) == null && main.get(component) == null)
                    main.put(component, bootstrapDir + File.separator + component);
                expando.setProperty(component, true);
            }
        }

        // boot initialization
        logger.info("***********************************************************");
        logger.info("Boot initialization");
        logger.info("***********************************************************");
        initBoot();
        logger.info("Done pre-booting ******************************************\n");

        // preBoot
        logger.info("***********************************************************");
        logger.info("Pre-booting");
        logger.info("***********************************************************");
        preBoot();
        logger.info("Done pre-booting ******************************************\n");

        // boot Components
        logger.info("***********************************************************");
        logger.info("Booting main");
        logger.info("***********************************************************");
        bootMain();
        logger.info("Done booting components **********************************\n");

        // postBoot
        logger.info("***********************************************************");
        logger.info("Post-booting");
        logger.info("***********************************************************");
        postBoot();
        logger.info("Done post-booting ******************************************\n");

        // boot initialization
        logger.info("***********************************************************");
        logger.info("Boot after");
        logger.info("***********************************************************");
        bootAfter();
        logger.info("Booting completed ****************************************");
    }

    private void bootEnv() {

        if (envInitialized)
            return;

        // default environment
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
        Object bootEnv = getVar(BOOT_ENV);
        if (bootEnv instanceof Map) {
            Map<String, Object> map = (Map) bootEnv;
            map.forEach((key, val) -> {
                resourceManager.getScripting().put(key, val);
            });
        }

        // host environment file
        if (getVar(HOST_ENVIRONMENT_FILE) != null) {
            envFile = getVar(HOST_ENVIRONMENT_FILE).toString();
            tryExec(envFile);
        }

        // startup system properties
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

        // host system properties
        if (getVar(HOST_SYSTME_PROPERTIES_FILE) != null) {
            sysFile = getVar(HOST_SYSTME_PROPERTIES_FILE).toString();
            tryExec(sysFile);
        }

        // log4j
        ThreadContext.put(ResourceManager.LOG_DIR_ABBREV, System.getProperty(Logger.logDir));
        logger.info("-> Log4J log4j.configurationFile=" + System.getProperty("log4j.configurationFile"));

        if (getVar(PRE_BOOT) != null) {
            Object p = getVar(PRE_BOOT);
            setupBootList(p, preBoot);
        }

        if (getVar(POST_BOOT) != null) {
            Object p = getVar(POST_BOOT);
            setupBootList(p, postBoot);
        }

        envInitialized = true;
    }

    private void setupBootList(Object p, List bootList) {
        if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  {
                if (main.get(l) != null || after.get(l) != null) {  // see if item is a name to main or after
                    expando.setProperty(l.toString(), true);
                } else {
                    bootList.add(l);
                }
            });
        } else if (p != null) {
            if (main.get(p) != null || after.get(p) != null){
                expando.setProperty(p.toString(), true);
            } else {
                bootList.add(p);
            }
        }
    }

    private void bootProvision() {
        //
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
    }

    private void bootInitialContext() {
        InitialContextFactory.setDefault();
    }

    private void tryExec(String path) {
        try {
            resourceManager.getScripting().exec(path);
        } catch (ScriptException e) {
            if (e.getCause() instanceof IOException) {
                logger.info("-> Script " + path + " not processed: " + e.getCause().getMessage());
            } else {
                logger.warn("!! Script not processed due to error.", e);
            }
        }
    }

    private void initBoot() {
        initBoot.forEach(script -> exec(script));
    }

    private void bootMain() {
        main.forEach(this::runComponent);
    }

    private void preBoot() {
        for (Object s : preBoot) {
            exec(s);
        }
    }

    private void postBoot() {
        for (Object s : postBoot) {
            exec(s);
        }
    }

    private void bootAfter() {
        after.forEach(this::runComponent);
    }

    private void runComponent(Object key, Object value) {
        if (key instanceof Closure) {
            Closure closure = (Closure) key;
            logger.info("-----------------------------------------------------------");
            logger.info("Running closure " + closure.toString() );
            logger.info("-----------------------------------------------------------");
            if (closure.isCase(EMPTY_OBJECT_ARRAY)) {
                exec(value);
            } else {
                logger.info("-> Closure returns false, skipped running " + value.toString());
            }
        } else {
            Object on = expando.getProperty(key.toString());
            if (Boolean.TRUE.equals(on)) {
                logger.info("-----------------------------------------------------------");
                logger.info("Booting " + key);
                logger.info("-----------------------------------------------------------");
                exec(value);
            }
        }
        logger.info("-----------------------------------------------------------\n");
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

    private void runObject(Object obj) {
        if (obj == null)
            return;
        try {
            if (obj instanceof Closure) {
                Closure closure = (Closure) obj;
                closure.setDelegate(expando);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                closure.call(this);
            } else {
                resourceManager.getScripting().exec(obj.toString());
            }
        } catch (ScriptException ex) {
            throw new SystemException(ex);
        }
    }

    private Object getVar(String var) {
        return resourceManager.getScripting().get(var);
    }
}
