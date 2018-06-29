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

/**
 * It is for boostrap groovy system.  The configuration in a groovy file should look like
 * def map = [ a: ["$__dir/script", ...],   // list of script
 *             b: "$__dir/script",          // a single script
 *             { !x && !y }: list of scripts]  // closure can be the key
 */
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
    private String defaultEnvironmentScript;
    private String defaultSystemProperties;
    private List<String> bootInitScripts = new ArrayList<>();
    private Map components = new LinkedHashMap();
    private List preBoot = new ArrayList<>();
    private List postBoot = new ArrayList<>();
    private ResourceManager resourceManager;
    private Expando expando = new Expando();

    public Bootstrap(ResourceManager rm) {
        this.resourceManager = rm;
    }

    public Map getComponents() {
        return components;
    }

    public void setComponents(Map components) {
        this.components = components;
        components.forEach((key, value) -> {
            if (key instanceof Closure) {
                Closure closure = (Closure) key;
                closure.setDelegate(expando);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            } else {
                String propertyName = key.toString();
                expando.setProperty(propertyName, false);
            }
        });
    }

    public String getDir() {
        return bootstrapDir;
    }

    public void setDir(String dir) {
        bootstrapDir = dir;
    }

    public String getDefaultEnvironmentScript() {
        return defaultEnvironmentScript;
    }

    public void setDefaultEnvironmentScript(String defaultEnvironmentScript) {
        this.defaultEnvironmentScript = defaultEnvironmentScript;
    }

    public String getDefaultSystemProperties() {
        return defaultSystemProperties;
    }

    public void setDefaultSystemProperties(String defaultSystemProperties) {
        this.defaultSystemProperties = defaultSystemProperties;
    }

    public void boot(String ... components) {
        // boot env
        logger.info("Loading environment ***************************************");
        bootEnv();
        logger.info("Done loading environment **********************************\n");

        bootProvision();

        bootInitialContext();

        if (components != null) {
            for (String component : components) {
                getComponents().computeIfAbsent(component, key -> bootstrapDir + File.separator + component);
                expando.setProperty(component, true);
            }
        }

        // boot initialization
        bootInitScripts();

        // preBoot
        logger.info("Prebooting ***********************************************");
        preBoot();
        logger.info("Done prebooting ******************************************\n");

        // boot Components
        logger.info("Booting components ***************************************");
        bootComponents();
        logger.info("Done booting components **********************************\n");

        // postBoot
        logger.info("Postbooting **********************************************");
        postBoot();
        logger.info("Booting completed ****************************************");
    }

    private void bootEnv() {
        // default environment
        if (defaultEnvironmentScript != null) {
            exec(defaultEnvironmentScript);
        } else {
            String script = getVar(Scripting.__DIR) + "/environment.groovy";
            File file = new File(script);
            if (file.exists()) {
                exec(script);
            } else {
                logger.warn("No default environment script.");
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
        logger.info("Log4J log4j.configurationFile=" + System.getProperty("log4j.configurationFile"));

        if (getVar(PRE_BOOT) != null) {
            Object p = getVar(PRE_BOOT);
            setupBootList(p, preBoot);
        }

        if (getVar(POST_BOOT) != null) {
            Object p = getVar(POST_BOOT);
            setupBootList(p, postBoot);
        }
    }

    private void setupBootList(Object p, List bootList) {
        if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  {
                if (components.get(l) != null) {
                    expando.setProperty(l.toString(), true);
                } else {
                    bootList.add(l);
                }
            });
        } else if (p != null) {
            if (components.get(p) != null) {
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
                logger.info("Script " + path + " not processed: " + e.getCause().getMessage());
            } else {
                logger.warn("Script not processed due to error.", e);
            }
        }
    }

    private void bootInitScripts() {
        bootInitScripts.forEach(script -> exec(script));
    }

    private void bootComponents() {
        components.forEach((key, value) -> {
            if (key instanceof Closure) {
                logger.info("--> running closure ...");
                Closure closure = (Closure) key;
                if (closure.isCase(EMPTY_OBJECT_ARRAY)) {
                    exec(value);
                }
            } else {
                Object on = expando.getProperty(key.toString());
                if (Boolean.TRUE.equals(on)) {
                    logger.info("--> booting " + key + " ...");
                    exec(value);
                }
            }
            logger.info("-----------------------\n");
        });
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
