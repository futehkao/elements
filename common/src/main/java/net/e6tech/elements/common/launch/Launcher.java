/*
 * Copyright 2016 Futeh Kao
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

import net.e6tech.elements.common.notification.NotificationListener;
import net.e6tech.elements.common.notification.ShutdownNotification;
import net.e6tech.elements.common.resources.OnShutdown;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.ResourceProvider;
import net.e6tech.elements.common.resources.Resources;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by futeh.
 */
class Launcher {
    ResourceManager resourceManager;
    LaunchController controller;
    CountDownLatch latch;

    String provisionDir;

    public Launcher(LaunchController controller) {
        this.controller = controller;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public void launch(List<LaunchListener> listeners ) {
        String file = controller.getLaunchScript();
        if (file == null) throw new IllegalArgumentException("launch file not specified, use launch=<file>");

        Thread thread = new Thread(() -> {
            resourceManager = new ResourceManager(controller.getProperties());
            controller.created(resourceManager);
            listeners.forEach(listener -> listener.created(resourceManager));
            try {
                resourceManager.load(file);
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
            latch.countDown();

            // if ShutdownNotification is detected, this code will call resourceManager.notifyAll in order
            // to break out of the next synchronized block that contains resourceManager.wait.
            resourceManager.addResourceProvider(ResourceProvider.wrap("Launcher", (OnShutdown) () -> {
                synchronized (resourceManager) {
                    resourceManager.notifyAll();;
                }
            }));

            // wait on resourceManager ... if ShutdownNotification is detected, the code just above will break out of
            // the wait.
            synchronized (resourceManager) {
                try {
                    resourceManager.wait();
                    System.out.println("Launcher thread stopped");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        thread.setDaemon(false);
        thread.start();
    }

    void onLaunched() {
        resourceManager.onLaunched();
    }
}
