/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.common.launch;

import net.e6tech.elements.common.resources.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by futeh.
 */
public class LaunchController implements LaunchListener {
    private static Map<String, Provision> provisions = new HashMap<>();
    private Properties properties = new Properties();
    private List<LaunchListener> listeners = new LinkedList<>();
    private List<String> arguments = new ArrayList<>();
    private ResourceManager resourceManager;
    private CountDownLatch latch;
    private ResourceManagerBuilder resourceManagerBuilder = ResourceManager::new;
    private ScriptLoader scriptLoader = ResourceManager::load;

    public LaunchController() {
        property("home", System.getProperty("home", System.getProperty("user.dir")));
        property("env", System.getProperty("env", "dev"));
        properties.put("args", arguments);
    }

    protected LaunchController(Properties properties) {
        this.properties.putAll(properties);
        this.properties.put("args", arguments);
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public LaunchController property(String property, Object value) {
        if (value == null)
            return this;
        properties.put(property, value);
        return this;
    }

    public LaunchController properties(Map<String, Object> map) {
        properties.putAll(map);
        return this;
    }

    public void addProperties(Properties properties) {
        this.properties.putAll(properties);
    }

    public String getProperty(String key) {
        return this.properties.getProperty(key);
    }

    public String getLaunchScript() {
        return properties.getProperty("launch");
    }

    public void setLaunchScript(String launchScript) {
        properties.setProperty("launch", launchScript);
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments.clear();
        this.arguments.addAll(arguments);
    }

    public void addArgument(String arg) {
        arguments.add(arg);
    }

    public ResourceManagerBuilder getResourceManagerBuilder() {
        return resourceManagerBuilder;
    }

    public void setResourceManagerBuilder(ResourceManagerBuilder resourceManagerBuilder) {
        Objects.requireNonNull(resourceManagerBuilder);
        this.resourceManagerBuilder = resourceManagerBuilder;
    }

    public ScriptLoader getScriptLoader() {
        return scriptLoader;
    }

    public void setScriptLoader(ScriptLoader scriptLoader) {
        Objects.requireNonNull(scriptLoader);
        this.scriptLoader = scriptLoader;
    }

    public LaunchController launchScript(String launchScript) {
        setLaunchScript(launchScript);
        return this;
    }

    public LaunchController addLaunchListener(LaunchListener listener) {
        listeners.add(listener);
        return this;
    }

    public LaunchController removeLaunchListener(LaunchListener listener) {
        listeners.remove(listener);
        return this;
    }

    public LaunchController addCreatedListener(CreatedListener listener) {
        listeners.add(listener);
        return this;
    }

    // listener for catching when both env variables and system properties are defined.
    public LaunchController addBootstrapEndVariables(BootstrapSystemPropertiesListener listener) {
        addCreatedListener(rm ->
                rm.getBootstrap().addBootstrapSystemPropertiesListener(listener));
        return this;
    }

    // Almost same as addBootstrapEndVariables but after various boot list are configured.
    public LaunchController addBootstrapEndEnv(BootstrapEndEnv listener) {
        addCreatedListener(rm ->
            rm.getBootstrap().addBootstrapEndEnv(listener));
        return this;
    }

    public LaunchController evalAfterCreated(String script) {
        return addCreatedListener(rm -> rm.eval(script));
    }

    public LaunchController removeCreatedListener(CreatedListener listener) {
        listeners.remove(listener);
        return this;
    }

    public LaunchController inject(Object object) {
        if (provisions.get(getLaunchScript()) != null) {
            provisions.get(getLaunchScript()).inject(object);
        } else {
            addLaunchListener(provision -> provision.inject(object));
        }
        return this;
    }

    @Override
    public void created(ResourceManager rm) {
        for (LaunchListener listener : listeners)
            listener.created(rm);
    }

    @Override
    public void launched(Provision provision) {
        provisions.put(getLaunchScript(), provision);
        for (LaunchListener listener : listeners)
            listener.launched(provision);

        resourceManager.onLaunched();
    }

    public LaunchController launch() {
        String launchScript = getLaunchScript();
        if (provisions.containsKey(launchScript))
            return this;
        (new Launch(this)).launch();
        return this;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public ResourceManager initResourceManager() {
        if (resourceManager == null) {
            resourceManager = resourceManagerBuilder.build(getProperties());
            created(resourceManager);
        }
        return resourceManager;
    }

    @SuppressWarnings({"squid:S2274", "squid:CommentedOutCodeLine", "squid:S106", "squid:S1148"})
    public void launch(List<LaunchListener> listeners ) {
        String file = getLaunchScript();
        if (file == null)
            throw new IllegalArgumentException("launch file not specified, use launch=<file>");

        initResourceManager();
        listeners.forEach(listener -> listener.created(resourceManager));
        try {
            scriptLoader.load(resourceManager, file);
            resourceManager.onShutdown("LaunchController " + getLaunchScript(), notification -> provisions.remove(getLaunchScript()));
        } catch (Exception e) {
            e.printStackTrace(); // we cannot use Logger yet
            System.exit(1);
        }

        latch.countDown();

        // if ShutdownNotification is detected, this code will call resourceManager.notifyAll in order
        // to break out of the next synchronized block that contains resourceManager.wait.
        resourceManager.addResourceProvider(ResourceProvider.wrap("Launcher", (OnShutdown) () -> {
            synchronized (resourceManager) {
                resourceManager.notifyAll();
            }
        }));

        Thread thread = new Thread(() -> {
            // wait on resourceManager ... if ShutdownNotification is detected, the code just above will break out of
            // the wait.
            synchronized (resourceManager) {
                try {
                    resourceManager.wait();
                    System.out.println("Launcher thread stopped");  // we cannot use Logger yet
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        thread.setDaemon(false);
        thread.start();
    }
}
