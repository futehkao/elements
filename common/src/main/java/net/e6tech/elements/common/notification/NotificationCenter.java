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

package net.e6tech.elements.common.notification;

import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.subscribe.Subscriber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by futeh on 1/21/16.
 */
@SuppressWarnings({"squid:S1149"})
public class NotificationCenter implements Broadcast {

    private Map<Object, List<NotificationListener>> srcNotificationListeners = new ConcurrentHashMap<>();
    private Map<Class, List<NotificationListener>> notificationListeners = new ConcurrentHashMap<>();

    // for broadcasting
    Map<Object, List<Subscriber>> subscribers = new ConcurrentHashMap<>();
    List<Broadcast> broadcasts = new CopyOnWriteArrayList<>();

    public void addSourceNotificationListener(Object src, NotificationListener listener) {
        List<NotificationListener> listeners = srcNotificationListeners.computeIfAbsent(src, n -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
    }

    public void removeSourceNotificationListener(Object src, NotificationListener listener) {
        List<NotificationListener> listeners = srcNotificationListeners.computeIfAbsent(src, n -> new CopyOnWriteArrayList<>());
        listeners.remove(listener);
    }

    public <T extends Notification> void addNotificationListener(Class<T> cls, NotificationListener<T> listener) {
        List<NotificationListener> listeners = notificationListeners.computeIfAbsent(cls, n -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
    }

    public <T extends Notification> void removeNotificationListener(Class<T> cls, NotificationListener<T> listener) {
        List<NotificationListener> listeners = notificationListeners.computeIfAbsent(cls, n -> new CopyOnWriteArrayList<>());
        listeners.remove(listener);
    }

    public void fireNotification(Notification notification) {
        if (notification.source() != null) {
            List<NotificationListener> listeners = srcNotificationListeners.get(notification.source());
            if (listeners != null) {
                listeners.forEach(listener -> listener.onEvent(notification));
            }
        }

        Class cls = notification.getClass();
        while (!cls.equals(Object.class)) {
            List<NotificationListener> listeners = notificationListeners.get(cls);
            if (listeners != null) {
                listeners.forEach(listener -> listener.onEvent(notification));
                break;
            }
            cls = cls.getSuperclass();
        }
    }

    public List<NotificationListener> getNotificationListeners(Notification notification) {
        List<NotificationListener> listeners = new ArrayList<>();
        if (notification.source() != null) {
            List<NotificationListener> list = srcNotificationListeners.get(notification.source());
            if (list != null)
                listeners.addAll(list);
        }
        List<NotificationListener> list = notificationListeners.get(notification.getClass());
        if (list != null)
            listeners.addAll(list);

        return list;
    }

    // ***************************************************************************************
    // Broadcast
    // ***************************************************************************************
    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            list.add(subscriber);
        }
        for (Broadcast broadcast: broadcasts) {
            broadcast.subscribe(topic, subscriber);
        }
    }

    @Override
    public <T extends Serializable> void subscribe(Class<T> topic, Subscriber<T> listener) {
        subscribe(topic.getName(), listener);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            list.remove(subscriber);
        }

        for (Broadcast broadcast: broadcasts) {
            broadcast.unsubscribe(topic, subscriber);
        }
    }

    @Override
    public void unsubscribe(Class topic, Subscriber subscriber) {
        unsubscribe(topic.getName(), subscriber);
    }

    @Override
    public void publish(String topic, Serializable object) {
        for (Broadcast broadcast: broadcasts) {
            broadcast.publish(topic, object);
        }
    }

    @Override
    public <T extends Serializable> void publish(Class<T> cls, T object) {
        for (Broadcast broadcast: broadcasts) {
            broadcast.publish(cls, object);
        }
    }

    public void addBroadcast(Broadcast broadcast) {
        broadcasts.add(broadcast);
        for (Map.Entry<Object, List<Subscriber>> entry : subscribers.entrySet()) {
            for (Subscriber subscriber : entry.getValue()) {
                if (entry.getKey() instanceof  String) {
                    broadcast.subscribe((String) entry.getKey(), subscriber);
                } else if (entry.getKey() instanceof Class) {
                    broadcast.subscribe((Class) entry.getKey(), subscriber);
                }
            }
        }
    }

    public void removeBroadcast(Broadcast broadcast) {
        broadcasts.remove(broadcast);
        for (Map.Entry<Object, List<Subscriber>> entry : subscribers.entrySet()) {
            for (Subscriber subscriber : entry.getValue()) {
                if (entry.getKey() instanceof  String) {
                    broadcast.unsubscribe((String) entry.getKey(), subscriber);
                } else if (entry.getKey() instanceof Class) {
                    broadcast.unsubscribe((Class) entry.getKey(), subscriber);
                }
            }
        }
    }

}
