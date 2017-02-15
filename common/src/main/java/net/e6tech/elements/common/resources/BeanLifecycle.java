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

package net.e6tech.elements.common.resources;

import java.util.*;

/**
 * Created by futeh.
 */
public class BeanLifecycle {
    private static final int BEAN_INITIALIZED = 0;
    private static final int BEAN_STARTED = 1;
    private static final int BEAN_LAUNCHED = 2;

    private Map<String, Object> initializedBeans = new Hashtable<>();
    private Map<String, Object> startedBeans = new Hashtable<>();
    private Map<String, Object> launchedBeans = new Hashtable<>();
    private Map<String, List<BeanListener>> namedBeanListeners = new Hashtable<>();
    private Map<Class, List<BeanListener>> classBeanListeners = new Hashtable<>();

    public void addBeanListener(String name, BeanListener beanListener) {
        if (initializedBeans.get(name) != null) {
            beanListener.initialized(initializedBeans.get(name));
            return;
        }
        List<BeanListener> listeners = namedBeanListeners.computeIfAbsent(name, n -> new Vector<>());
        listeners.add(beanListener);
    }

    public void addBeanListener(Class cls, BeanListener beanListener) {
        for (Object bean : initializedBeans.values()) {
            if (cls.isAssignableFrom(bean.getClass())) {
                beanListener.initialized(bean);
            }
        }

        List<BeanListener> listeners = classBeanListeners.computeIfAbsent(cls, n -> new Vector<>());
        listeners.add(beanListener);
    }

    public void removeBeanListener(BeanListener listener) {
        for (List<BeanListener> listeners : namedBeanListeners.values()) listeners.remove(listener);
        for (List<BeanListener> listeners : classBeanListeners.values()) listeners.remove(listener);
    }

    public void fireBeanInitialized(String beanName, Object bean) {
        fireBeanEvent(beanName, bean, BEAN_INITIALIZED);
        initializedBeans.put(beanName, bean);
    }

    public boolean isBeanInitialized(Object bean) {
        return initializedBeans.containsValue(bean);
    }

    public void fireBeanStarted(String beanName, Object bean) {
        fireBeanEvent(beanName, bean, BEAN_STARTED);
        startedBeans.put(beanName, bean);
    }

    public boolean isBeanStarted(Object bean) {
        return startedBeans.containsValue(bean);
    }

    public void fireBeanLaunched(String beanName, Object bean) {
        fireBeanEvent(beanName, bean, BEAN_LAUNCHED);
        launchedBeans.put(beanName, bean);
    }

    public boolean isBeanLaunched(Object bean) {
        return launchedBeans.containsValue(bean);
    }

    public void clearBeanListeners() {
        initializedBeans.clear();
        startedBeans.clear();
        launchedBeans.clear();
        namedBeanListeners.clear();
        classBeanListeners.clear();
    }

    private void fireBeanEvent(String beanName, Object bean, int eventType) {
        List<BeanListener> list = null; // to avoid concurrent mod to listeners
        if (beanName != null) {
            list = new ArrayList<>();
            List<BeanListener> listeners = namedBeanListeners.get(beanName);
            if (listeners != null) {
                list.addAll(listeners);
            }
        }
        for (Class cls : classBeanListeners.keySet()) {
            if (list == null) list = new ArrayList<>();
            if (cls.isAssignableFrom(bean.getClass())) {
                List<BeanListener> listeners = classBeanListeners.get(cls);
                if (listeners != null) list.addAll(listeners);
            }
        }
        if (list != null) list.forEach((beanListener) -> {
            notifyBeanListener(beanListener, bean, eventType);
        });
    }

    private void notifyBeanListener(BeanListener beanListener, Object bean, int eventType) {
        switch (eventType) {
            case BEAN_INITIALIZED: beanListener.initialized(bean); break;
            case BEAN_STARTED: beanListener.started(bean); break;
            case BEAN_LAUNCHED: beanListener.launched(bean); break;
        }
    }
}
