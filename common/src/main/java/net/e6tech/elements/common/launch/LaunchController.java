/*
 * Copyright 2015 Futeh Kao
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
        this.arguments = arguments;
    }

    public void addArgument(String arg) {
        arguments.add(arg);
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
            resourceManager = new ResourceManager(getProperties());
            created(resourceManager);
        }
        return resourceManager;
    }

    public void launch(List<LaunchListener> listeners ) {
        String file = getLaunchScript();
        if (file == null)
            throw new IllegalArgumentException("launch file not specified, use launch=<file>");

        initResourceManager();
        listeners.forEach(listener -> listener.created(resourceManager));
        try {
            resourceManager.load(file);
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

        /* Another way of doing it ...
            resourceManager.getNotificationCenter().addNotificationListener(ShutdownNotification.class,
                NotificationListener.wrap(getClass().getName(), (notification) -> {
                synchronized (resourceManager) {
                    resourceManager.notifyAll();
                }
            }));
            */

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
