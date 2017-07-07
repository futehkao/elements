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

package net.e6tech.elements.common.notification;

import com.google.inject.Inject;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Startable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 */
public class NotificationProcessor implements NotificationListener {
    @Inject(optional = true)
    protected ExecutorService threadPool;

    @Inject(optional = true)
    protected Genesis genesis;

    @Inject
    protected NotificationCenter notificationCenter;

    @Inject
    protected Provision provision;

    private Map<Class<? extends Notification>, Method> methods = new HashMap<>();
    private Class<? extends Notification>[] notificationTypes = new Class[0];

    public NotificationProcessor() {
        Class cls = getClass();
        List<Class<? extends Notification>> types = new ArrayList<>();
        while (cls != null & !cls.equals(Object.class)) {
            Method[] methods = cls.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals("processEvent")
                        && method.getParameterCount() == 1
                        && Notification.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    method.setAccessible(true);
                    Class<? extends Notification> notificationType = (Class<? extends Notification>) method.getParameterTypes()[0];
                    this.methods.computeIfAbsent(notificationType, (key) -> method);
                    types.add(notificationType);
                }
            }
            cls = cls.getSuperclass();
        }
        notificationTypes = types.toArray(new Class[types.size()]);
    }

    public Class<? extends Notification>[] getNotificationTypes() {
        return notificationTypes;
    }

    /**
     * The onEvent is running on the same thread and using the same Resources as the
     * sender.  This method needs to handle the event fast and schedule longer running task
     * using the run method.
     *
     * @param notification Notification instance
     */
    @Override
    public void onEvent(Notification notification) {
        Class cls = notification.getClass();
        while (!cls.equals(Object.class)) {
            Method method = methods.get(notification.getClass());
            if (method != null) {
                try {
                    method.invoke(this, notification);
                } catch (Throwable e) {
                    // logger.warn
                }
                return;
            } else {
                cls = cls.getSuperclass();
            }
        }

        catchEvent(notification);
    }

    /**
     * It is the catch all processEvent method.  It is running on the same thread and using
     * the same Resources as the caller.  Please schedule long running task using the run method.
     * @param notification  Notification instance
     */
    public void catchEvent(Notification notification) {
    }

    public void async(Runnable runnable) {
        if (genesis != null) {
            genesis.async(runnable);
        } else if (threadPool != null) {
            threadPool.execute(runnable);
        } else {
            Thread thread = new Thread(runnable);
            thread.start();
        }
    }
}
