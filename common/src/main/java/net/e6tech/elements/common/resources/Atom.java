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
package net.e6tech.elements.common.resources;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import net.e6tech.elements.common.launch.LaunchListener;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.script.ScriptException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
public class Atom implements Map<String, Object> {
    private static Logger logger = Logger.getLogger();
    private static final String WAIT_FOR = "waitFor";
    private static final String PRE_INIT = "preInit";
    private static final String POST_INIT = "postInit";
    private static final String LAUNCHED = "launched";
    private static final String AFTER = "after";
    private static final String CONFIGURATION = "configuration";
    private static final String EXEC = "exec";
    private static final String NAME = "name";
    private static final String RESOURCES = "resources";
    private static final String RESOURCE_MANAGER = "resourceManager";

    private ResourceManager resourceManager;
    private Resources resources;
    private Map<String, Object> boundInstances = new LinkedHashMap<>();
    private Map<String, Object> externalInstances = new HashMap<>();
    private Configuration configuration;
    private List<Configurable> configurables = new ArrayList<>();
    private String name;
    private Map<String, BiConsumer<String, Object>> directives = new HashMap<>();
    private BeanLifecycle beanLifecycle;

    Atom(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.beanLifecycle = resourceManager.getBeanLifecycle();
        // we create Resources here to avoid engaging ResourceProviders
        // Had we used resourceManager.open, resourceProvider will be open as well which means
        // transaction may be started.  Don't want that. Just want resources for setting up instances.
        resources = resourceManager.newResources();
        BiConsumer<String, Object> put = (key, value) -> boundInstances.put(key, value);
        directives.put(CONFIGURATION, (key, value) -> {
            if (value != null) {
                configuration = new Configuration(resourceManager.getScripting().getProperties());
                configuration.load(value.toString());
                resources.setConfiguration(configuration);
            }
        });
        directives.put(WAIT_FOR, (key, value) -> {
            (new MyBeanListener()).invokeMethod(key, new Object[] {value});
        });
        directives.put(PRE_INIT, put);
        directives.put(POST_INIT, put);
        directives.put(AFTER, (key, value) -> runAfter(value));
        directives.put(LAUNCHED, (key, value) -> runLaunched(value));
        directives.put(EXEC, (key, value) -> {
            try {
                if (value instanceof String) {
                    resourceManager.exec(value.toString());
                } else if (value instanceof List) {
                    resourceManager.exec(((List) value).toArray(new Object[0]));
                }
            } catch (RuntimeException e) {
                throw new NestedScriptException(e.getCause());
            }
        });
    }

    /**
     * Used by groovy scripts.
     * @param cls  The class that identifies the binding object.
     * @param resource  the object to be bound to the {@code cls}
     * @param <T> type of resource
     * @return return the resource object.  If resource is a Class, returns new instance of the class.
     */
    public <T> T bind(Class<T> cls, T resource) {
        return resources.bind(cls, resource);
    }

    public <T> T resourceManangerBind(Class<T> cls, T resource) {
        resources.unbind(cls);
        return resourceManager.bind(cls, resource);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void bindInitialContext(String key, Object value) {
        try {
            (new InitialContext()).bind(key, value);
        } catch (NamingException e) {
            throw logger.runtimeException(e);
        }
    }

    public Map<String,Object> getConfiguration() {
        if (configuration == null) return null;
        return Collections.unmodifiableMap(configuration);
    }

    public void configure(Object obj) {
        configure(obj, null);
    }

    public void configure(Object obj, String prefix) {
        if (configuration == null) {
            configurables.add(new Configurable(obj, prefix));
            return;
        }

        if (obj != null) boundInstances.put(prefix, obj); // needed here because configure may execute a script that requires the instance.
                                                          // when a config string begin with ^, it is turned into a closure.  The expression is
                                                          // then executed in configuration.configure.
        configuration.configure(obj, prefix,
                (str) -> {
                    try {
                        Closure closure = (Closure) resourceManager.getScripting().eval("{ it ->" + str + " }");
                        closure.setDelegate(this);
                        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                        return closure.call(obj);
                    } catch (MissingPropertyException e) {
                        return null;
                    } catch (ScriptException e) {
                        throw new RuntimeException(e);
                    }
                },
                (value, toType, instance) -> resources.inject(instance));
    }

    public Atom build() {

        if (configuration != null) {
            for (Configurable configurable : configurables) {
                configuration.configure(configurable.instance, configurable.prefix,
                        this::get,
                        (value, toType, instance) -> resources.inject(instance));
            }
        }
        configurables.clear();

        resources.onOpen();

        boundInstances.values().forEach(resources::inject);

        if (get(PRE_INIT) != null) {
            Object obj = get(PRE_INIT);
            // obj should be a closure
            resourceManager.runNow(this, obj);
        }

        for (String key : boundInstances.keySet()) {
            Object value = boundInstances.get(key);
            if (externalInstances.containsKey(key)) continue;
            if (value instanceof Initializable) {
                ((Initializable) value).initialize(resources);
            }
            if (!resourceManager.getScripting().isRunnable(value)) {
                beanLifecycle.fireBeanInitialized(key, value);
            }
        }

        if (get(POST_INIT) != null) {
            Object obj = get(POST_INIT);
            // obj should be a closure
            resourceManager.runNow(this, obj);
        }

        // running object that implements Startable
        if (boundInstances.size() > 0) {
            RunStartable runStartable = new RunStartable();
            runStartable.name = getName();
            for (String key : boundInstances.keySet()) {
                Object value = boundInstances.get(key);
                if (externalInstances.containsKey(key)) continue;
                if (value instanceof Startable) {
                    runStartable.add(key, (Startable)value);
                }
            }
            if (runStartable.listeners.size() > 0) resourceManager.runAfter(runStartable);
        }

        // running object that implements OnLaunched
        if (boundInstances.size() > 0) {
            RunLaunched runLaunched = new RunLaunched(resourceManager.getInstance(Provision.class));
            runLaunched.name = getName();
            for (String key : boundInstances.keySet()) {
                Object value = boundInstances.get(key);
                if (externalInstances.containsKey(key)) continue;
                if (value instanceof LaunchListener) {
                    runLaunched.add(key, (LaunchListener) value);
                }
            }
            if (runLaunched.listeners.size() > 0) resourceManager.runLaunched(runLaunched);
        }

        // this only applies when the Component is created outside of loading a script.
        resourceManager.runAfterIfNotLoading();

        return this;
    }

    // not using closure to minimize Component reference
    class RunStartable implements Runnable {
        Map<String, Startable> listeners = new LinkedHashMap<>();
        String name;

        void add(String key, Startable listener) {
            listeners.put(key, listener);
        }

        public void run() {
            try {
                for (String key : listeners.keySet()) {
                    Startable listener = listeners.get(key);
                    listener.start();
                    beanLifecycle.fireBeanStarted(key, listener);
                }
            } catch (RuntimeException ex) {
                logger.error("Error running startable component name = " + name);
                throw ex;
            }
        }
    }

    class RunLaunched implements Runnable {
        Map<String, LaunchListener> listeners = new LinkedHashMap<>();
        String name;
        Provision provision;

        RunLaunched(Provision provision) {
            this.provision = provision;
        }

        void add(String key, LaunchListener listener) {
            listeners.put(key, listener);
        }

        public void run() {
            try {
                for (String key : listeners.keySet()) {
                    LaunchListener listener = listeners.get(key);
                    listener.launched(provision);
                    beanLifecycle.fireBeanLaunched(key, listener);
                }
            } catch (RuntimeException ex) {
                logger.error("Error running launched component name = " + name);
                throw ex;
            }
        }
    }

    public void discard() {
        resources.discard();
        configuration = null;
        configurables = null;
        boundInstances = null;
    }

    public Atom open(Consumer<Resources> consumer) {
        Resources resources = null;
        try {
            resources = resourceManager.open();
            consumer.accept(resources);
        } finally {
            if (resources != null) try { resources.commit();} catch (Throwable th) {}
        }
        return this;
    }

    public <T> T getInstance(Class<T> cl) {
        return (T) resources.getInstance(cl);
    }

    /**
     * runs callable after every script is loaded
     * @param callable
     */
    public void runAfter(Object callable) {
        resourceManager.runAfter(callable);
    }

    public void run(Object callable) {
        resourceManager.runNow(this, callable);
    }

    /**
     * runs after all resourceManagers are launched.
     * @param callable
     */
    public void runLaunched(Object callable) {
        resourceManager.getScripting().runLaunched(callable);
    }

    @Override
    public int size() {
        return resourceManager.getBeans().size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        return resourceManager.getBeans().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return resourceManager.getBeans().containsValue(value);
    }

    public void waitFor(String beanName, Consumer consumer) {
        beanLifecycle.addBeanListener(beanName, new BeanListener() {
            @Override
            public void initialized(Object bean) {
                consumer.accept(bean);
                beanLifecycle.removeBeanListener(this);
            }
        });
    }

    public void waitFor(Class cls, Consumer consumer) {
        beanLifecycle.addBeanListener(cls, new BeanListener() {
            @Override
            public void initialized(Object bean) {
                consumer.accept(bean);
                beanLifecycle.removeBeanListener(this);
            }
        });
    }

    // The following methods mapped to special keywords in put.

    public void configuration(Object c) {
        put(CONFIGURATION, c);
    }

    public void preInit(Closure c) {
        put(PRE_INIT, c);
    }

    public void postInit(Closure c) {
        put(POST_INIT, c);
    }

    public void after(Closure c) {
        put(AFTER, c);
    }

    public void launched(Closure c) {
        put(LAUNCHED, c);
    }

    public Object exec(String str) {
        return put(EXEC, str);
    }

    public void exec(List list) {
        put(EXEC, list);
    }

    private boolean isDirective(String key) {
        return directives.containsKey(key);
    }

    private Object processDirective(String key, Object value) {
        if (EXEC.equals(key) && value instanceof String) {
            return resourceManager.exec(value.toString());
        }
        directives.get(key).accept(key, value);
        return null;
    }

    @Override
    public Object get(Object key) {

        if (key instanceof String && ((String) key).contains(".")) {
            String[] path = ((String) key).split("\\.");
            Object obj = get(path[0].trim());
            for (int i = 1; i < path.length; i++) obj = Reflection.getProperty(obj, path[i].trim());
            return obj;
        }

        if (NAME.equals(key)) {
            return name;
        } else if (RESOURCES.equals(key)) {
            return resources;
        } else if (RESOURCE_MANAGER.equals(key)) {
            return resourceManager;
        } else if (WAIT_FOR.equals(key)) {
            return new MyBeanListener();
        } else if (CONFIGURATION.equals(key)) {
            return configuration;
        }

        Object object = boundInstances.get(key);

        // if null try resourceManager.getBean
        if (object == null) {
            if (key instanceof Class) {
                try {
                    object = resources.getInstance((Class) key);
                } catch (InstanceNotFoundException ex) {
                    object = resourceManager.getBean((Class) key);
                }
            } else {
                object = resourceManager.getBean(key.toString());
            }
        }

        if (object == null) {
            object = resourceManager.getScripting().get(key.toString());
        }
        return object;
    }

    @Override
    public Object put(String key, Object value) {
        // NOTE: at this point resources is mostly likely not open so that inject won't work
        Object instance = null;
        try {
            if (value == null) {
                throw new IllegalArgumentException("value for key=" + key + " is null!  This happens because you did not import the class.");
            }

            if (value instanceof Class) {
                if (ResourceProvider.class.isAssignableFrom((Class) value)) {
                    Class cls = (Class) value;
                    try {
                        value = cls.newInstance();
                    } catch (Exception e) {
                        throw logger.runtimeException(e);
                    }
                    if (!key.startsWith("_")) {
                        resourceManager.addBean(key, value);
                    }
                    resourceManager.addResourceProvider((ResourceProvider) value);
                    instance = value;
                } else {
                    try {
                        instance = ((Class) value).newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        throw logger.runtimeException(e);
                    }

                    if (!key.startsWith("_")) {
                        resourceManager.addBean(key, instance);
                        resources.onOpen();
                        resources.inject(instance);
                    }

                    if (instance == null) {
                        throw new RuntimeException("Cannot instantiate " + value);
                    }

                    Class cls = instance.getClass();
                    resources.rebind(cls, instance);
                }
                configure(instance, key);
            }  else if (value instanceof ResourceProvider) {
                if (!key.startsWith("_")) {
                    resourceManager.addBean(key, value);
                }
                resourceManager.addResourceProvider((ResourceProvider) value);
                instance = value;
                configure(instance, key);
            } else if (value instanceof Configuration) {
                instance = value;
                resources.setConfiguration((Configuration) value);
                configuration = (Configuration) value;
            } else if (isDirective(key)) {
                return processDirective(key, value);
            } else if (RESOURCES.equals(key)) {
                throw new IllegalArgumentException("Cannot set resources");
            } else if (RESOURCE_MANAGER.equals(key)) {
                throw new IllegalArgumentException("Cannot set resourceManager");
            } else if (resourceManager.getScripting().isRunnable(value)) {
                // value is a closure
                if (boundInstances.get(key) != null) {
                    //Check if closure has an associated instance
                    instance = boundInstances.get(key);
                    resourceManager.runNow(instance, value);
                }  else {
                    instance = value;
                }
            } else {
                if (!resourceManager.getScripting().isRunnable(value)) {
                    instance = resources.rebind((Class) value.getClass(), value);
                    if (!key.startsWith("_")) {
                        resourceManager.addBean(key, instance);
                        resources.onOpen();
                        resources.inject(instance);
                    }
                    externalInstances.put(key, instance);
                    configure(instance, key);
                } else {
                    // this is either a Runnable or a Closure
                    instance = value;
                }
            }

            // the below is to apply closure to instance.
           if (instance != null && !resourceManager.getScripting().isRunnable(instance)) {  // make sure instance is not a closure itself
                // instance is not null (but closure is). See if there is a closure already in
                // boundInstances and applies it to the instance.
                if (resourceManager.getScripting().isRunnable(boundInstances.get(key))) {
                    Object closure = boundInstances.get(key);
                    resourceManager.runNow(instance, closure);
                }
            }

            if (instance != null) {
                if (instance instanceof ClassBeanListener) {
                    ClassBeanListener l = (ClassBeanListener) instance; 
                    for (Class cl : l.listenFor()) beanLifecycle.addBeanListener(cl, l);
                }
                if (instance instanceof NamedBeanListener) {
                    NamedBeanListener l = (NamedBeanListener) instance;
                    for (String beanName : l.listenFor()) beanLifecycle.addBeanListener(beanName, l);
                }
                boundInstances.put(key, instance);
            }

        } catch (NestedScriptException th) {
            // no logging as it is done by the nested script.
            throw th;
        } catch (Throwable th) {
            logger.error("Component name=" + getName() + " has issues", th);
            throw new RuntimeException(th);
        }

        return instance;
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (String key : m.keySet()) {
            put(key, m.get(key));
        }
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> keySet() {
        return boundInstances.keySet();
    }

    @Override
    public Collection<Object> values() {
        return boundInstances.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return boundInstances.entrySet();
    }

    class Configurable {
        Object instance;
        String prefix;
        Configurable(Object instance, String prefix) {
            this.instance = instance;
            this.prefix = prefix;
        }
    }

    private static class NestedScriptException extends RuntimeException {

        public NestedScriptException() {
        }

        public NestedScriptException(String message) {
            super(message);
        }

        public NestedScriptException(String message, Throwable cause) {
            super(message, cause);
        }

        public NestedScriptException(Throwable cause) {
            super(cause);
        }
    }

    class MyBeanListener extends GroovyObjectSupport {

        // name is the name of the bean
        public Object invokeMethod(String name, Object args) {
            Object[] arguments = (Object[]) args;
            Closure closure = (Closure) arguments[0];
            beanLifecycle.addBeanListener(name, new BeanListener() {
                @Override
                public void initialized(Object bean) {
                    closure.call(bean);
                    beanLifecycle.removeBeanListener(this);
                }
            });

            return null;
        }
    }
}
