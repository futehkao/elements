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
package net.e6tech.elements.common.launch;

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S1700", "squid:MethodCyclomaticComplexity", "squid:ForLoopCounterChangedCheck",
        "squid:S134"})
public class Launch {

    private static final String LAUNCH = "launch";
    private static final String END = "end";

    List<LaunchController> controllers = new ArrayList<>();

    public Launch(LaunchController ... controllers) {
        for (LaunchController controller : controllers) {
            this.controllers.add(controller);
        }
    }

    /**
     * Required properties are
     * launch
     * home
     * env
     *
     * For example
     * Launch launch=conf/provisioning/provision-1.groovy
     *          home=dir1 key1=val1 arg1 arg2
     *      end
     *      launch=conf/provisioning/provision-2.groovy
     *          home=dir2 end
     *      end
     *      global-key1=global-value1
     *      global-key2=global-value2
     *      global-arg1 global-arg2 ...
     *
     *  arguments defined outside of the last "end" are applied to every launch profile.
     *  arguments between launch and end are only visible for that particular launch profile.
     *
     * @param args arguments
     */
    @SuppressWarnings("squid:S3776")
    public static void main(String ... args) {
        List<LaunchController> controllers = new ArrayList<>();

        Properties defaultProperties = new Properties();
        // Getting all - or -- args into defaultProperties
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-") && i < args.length - 1) {
                String key = args[i];
                if (key.startsWith("--"))
                    key = key.substring(2);
                else if (key.startsWith("-"))
                    key = key.substring(1);
                if (!args[i + 1].startsWith("-") && i + 1 < args.length) {
                    String val = args[++i];
                    defaultProperties.put(key, val);
                }
            }
        }

        LaunchController controller = new LaunchController(defaultProperties);

        // loop through args again to get key value and args
        for (String arg : args) {
            if (arg.contains("=")) {
                String[] keyval = arg.split("=");
                controller.property(keyval[0], keyval[1]);
            } else if (arg.equalsIgnoreCase(END)) {
                controllers.add(controller);
                controller = new LaunchController(defaultProperties);
            } else {
                controller.addArgument(arg);
            }
        }

        if (!args[args.length - 1].equalsIgnoreCase(END)) {
            if (controller.getProperty(LAUNCH) != null) {
                controllers.add(controller);
            } else {
                // does not contain launch so args are append to all other properties
                List<String> arguments = controller.getArguments();
                for (LaunchController c : controllers) {
                    for (String arg : arguments) {
                        c.addArgument(arg);
                    }
                }

                Properties properties = controller.getProperties();
                for (String propKey : properties.stringPropertyNames()) {
                    for (LaunchController c : controllers) {
                        if (c.getProperty(propKey) == null)
                            c.property(propKey, properties.getProperty(propKey));
                    }
                }
            }
        }

        launch(controllers);
    }

    private static void launch(List<LaunchController> controllerList) {
        LaunchController[] controllers = controllerList.stream().toArray(LaunchController[]::new);
        Launch launch = new Launch(controllers);
        launch.launch();
    }

    public void launch(LaunchListener ... launchListeners) {
        List<LaunchListener> listeners = new ArrayList<>();
        if (launchListeners != null)
            for (LaunchListener l : launchListeners)
                listeners.add(l);

        CountDownLatch latch = new CountDownLatch(controllers.size());
        for (LaunchController controller : controllers) {
            controller.setLatch(latch);
            controller.launch(listeners);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SystemException(e);
        }

        // fire launched
        for (LaunchController controller : controllers) {
            Provision provision = controller.getResourceManager().getInstance(Provision.class);
            controller.launched(provision);
            listeners.forEach(listener -> listener.launched(provision));
        }
    }
}
