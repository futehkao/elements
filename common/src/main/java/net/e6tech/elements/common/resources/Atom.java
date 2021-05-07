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
package net.e6tech.elements.common.resources;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import net.e6tech.elements.common.launch.LaunchListener;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.notification.Notification;
import net.e6tech.elements.common.notification.NotificationListener;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.datastructure.Pair;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
@SuppressWarnings({"unchecked", "squid:S1845", "squid:S3776"})
public class Atom implements Map<String, Object> {
    public static final String OVERRIDE_SETTINGS = "__atom_override_settings";
    public static final String OVERRIDE_NAME = "__atom_override_name";
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
    private Map<String, Object> overrideSettings = new LinkedHashMap<>();
    private Map<String, Object> defaultSettings = new LinkedHashMap<>();
    private Configuration configuration;
    private String name;
    private Map<String, BiConsumer<String, Object>> directives = new HashMap<>();
    private BeanLifecycle beanLifecycle;
    private boolean prototype = false;
    private Configuration.Resolver resolver = this::resolve;
    private ClassLoader scriptLoader;

    Atom(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.beanLifecycle = resourceManager.getBeanLifecycle();
        // we create Resources here to avoid engaging ResourceProviders
        // Had we used resourceManager.open, resourceProvider will be open as well which means
        // transaction may be started.  Don't want that. Just want resources for setting up instances.
        // Since ResourceProviders are not open, no need to commit or abort resources.
        resources = resourceManager.newResources();
        resources.bind(Configuration.Resolver.class, resolver);
        resources.bind(Atom.class, this);
        resourceManager.getVariable(OVERRIDE_SETTINGS).ifPresent(map -> overrideSettings = resourceManager.nullableVar(OVERRIDE_SETTINGS));

        BiConsumer<String, Object> addClosure = (key, value) -> {
            Object existing = boundInstances.get(key);
            List<Closure> list;
            if (existing instanceof List) {
                list = (List<Closure>) existing;
            } else {
                list = new LinkedList<>();
                boundInstances.put(key, list);
            }
            if (value instanceof Closure) {
                Closure closure = (Closure) value;
                list.add(closure);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                closure.setDelegate(this);
            }
        };
        directives.put(CONFIGURATION, (key, value) -> {
            if (value != null) {
                if (configuration == null) {
                    configuration = new Configuration(resourceManager.getScripting().getProperties());
                    configuration.load(value.toString());
                } else {
                    Configuration more = new Configuration(resourceManager.getScripting().getProperties());
                    more.load(value.toString());
                    configuration.load(more);
                }
                resources.configurator.putAll(configuration);
            }
        });
        directives.put(PRE_INIT, addClosure);
        directives.put(POST_INIT, addClosure);
        directives.put(AFTER, addClosure);
        directives.put(LAUNCHED, addClosure);
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

    public Atom(ResourceManager resourceManager, Atom prototype) {
        this(resourceManager);

        if (prototype == null)
            return;
        if (!prototype.isPrototypeAtom())
            throw new IllegalArgumentException("Atom named " + prototype.getName() + " is not a prototype.");
        resources = prototype.resources;
        boundInstances = prototype.boundInstances;
        resources.rebind(Configuration.Resolver.class, resolver);
        // do not copy configuration
    }

    public <T> T resourceManagerBind(Class<T> cls, T resource) {
        if (cls == null) {
            String classDesc = (resource == null) ? "" : " Expecting " + resource.getClass().getName() + " or its super class";
            logger.error("Please import the appropriate class in the script.{}", classDesc);
            throw new IllegalArgumentException();
        }
        resources.unbind(cls);
        return resourceManager.bind(cls, resource);
    }

    public boolean isPrototypeAtom() {
        return prototype;
    }

    public void setPrototypeAtom(boolean prototype) {
        this.prototype = prototype;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getOverrideSettings() {
        return overrideSettings;
    }

    public void setOverrideSettings(Map<String, Object> overrideSettings) {
        this.overrideSettings = overrideSettings;
    }

    public Map<String, Object> getDefaultSettings() {
        return defaultSettings;
    }

    public void setDefaultSettings(Map<String, Object> defaultSettings) {
        this.defaultSettings = defaultSettings;
    }

    public ClassLoader getScriptLoader() {
        return scriptLoader;
    }

    public void setScriptLoader(ClassLoader scriptLoader) {
        this.scriptLoader = scriptLoader;
    }

    public void bindInitialContext(String key, Object value) {
        try {
            (new InitialContext()).bind(key, value);
        } catch (NamingException e) {
            throw logger.systemException(e);
        }
    }

    public Map<String, Object> getConfiguration() {
        if (configuration == null)
            return null;
        return Collections.unmodifiableMap(configuration);
    }

    private Object resolve(String expression) {
        try {
            String exp = expression;
            while (exp.startsWith("^"))
                exp = exp.substring(1);
            Closure closure = (Closure) resourceManager.getScripting().eval("{->" + exp + " }");
            closure.setDelegate(this);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            return closure.call();
        } catch (MissingPropertyException e) {
            logger.trace(e.getMessage(), e);
            return null;
        }
    }

    public void configure(Object obj) {
        configure(obj, null);
    }

    public void configure(Object obj, String prefix) {

        if (obj instanceof NotificationListener) {
            NotificationListener listener = (NotificationListener) obj;
            Class<? extends Notification>[] notificationTypes = listener.getNotificationTypes();
            for (Class<? extends Notification> notificationType : notificationTypes) {
                resourceManager.getNotificationCenter().addNotificationListener(notificationType, listener);
            }
        }

        if (configuration == null) {
            return;
        }

        if (obj instanceof Instantiated)
            ((Instantiated) obj).instantiated(resources);

        if (obj != null)
            boundInstances.put(prefix, obj); // needed here because annotate may execute a script that requires the instance.
        // when a config string begin with ^, it is turned into a closure.  The expression is
        // then executed in configuration.annotate.
        configuration.configure(obj, prefix,
                this::resolve,
                (value, toType, instance) -> {
                    if (instance != null) {
                        Package p = instance.getClass().getPackage();
                        if (p == null
                                || (!p.getName().startsWith("java.")
                                && !p.getName().startsWith("javax.")))
                            resources.inject(instance);
                    }
                });
    }

    @SuppressWarnings({"squid:S134", "squid:MethodCyclomaticComplexity"})
    public Atom build() {
        if (isPrototypeAtom())
            return this;

        long start = System.currentTimeMillis();
        resources.onOpen();
        boundInstances.values().forEach(resources::inject);

        // install waiting, after and launched logic
        runWaitFor(boundInstances.get(WAIT_FOR));
        runAfter(boundInstances.get(AFTER));
        runLaunched(boundInstances.get(LAUNCHED));

        run(boundInstances.get(PRE_INIT));

        // call initialized for beans that implements Initializable
        for (Map.Entry<String, Object> entry : boundInstances.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Initializable && !beanLifecycle.isBeanInitialized(value)) {
                ((Initializable) value).initialize(resources);
            }
            if (!resourceManager.getScripting().isRunnable(value)) {
                beanLifecycle.fireBeanInitialized(entry.getKey(), value);
            }
        }

        run(boundInstances.get(POST_INIT));

        // running object that implements Startable
        if (boundInstances.size() > 0) {
            RunStartable runStartable = new RunStartable(resourceManager);
            runStartable.name = getName();
            for (Map.Entry<String, Object> entry : boundInstances.entrySet()) {
                if (entry.getValue() instanceof Startable) {
                    runStartable.add(entry.getKey(), (Startable) entry.getValue());
                }
            }
            if (!runStartable.startables.isEmpty())
                resourceManager.runAfter(runStartable);
        }

        // running object that implements OnLaunched
        RunLaunched runLaunched = new RunLaunched(resourceManager.getInstance(Provision.class));
        if (boundInstances.size() > 0) {
            runLaunched.name = getName();
            for (Map.Entry<String, Object> entry : boundInstances.entrySet()) {
                if (entry.getValue() instanceof LaunchListener) {
                    runLaunched.add(entry.getKey(), (LaunchListener) entry.getValue());
                }
            }

            if (!runLaunched.listeners.isEmpty())
                resourceManager.runLaunched(runLaunched);
        }

        // this only applies when the Atom is created outside of loading a script.
        resourceManager.runAfterIfNotLoading();

        if (!resourceManager.isSilent())
            logger.info("Atom {} loaded in {}ms", getName(), (System.currentTimeMillis() - start));

        return this;
    }

    public void disable(Object bean) {
        resourceManager.getBeanLifecycle().disableBean(bean);
    }

    // not using closure to minimize Component reference
    static class RunStartable implements Runnable {
        Map<String, Startable> startables = new LinkedHashMap<>();
        String name;
        ResourceManager resourceManager;

        RunStartable(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        void add(String key, Startable listener) {
            startables.put(key, listener);
        }

        public void run() {
            try {
                for (Map.Entry<String, Startable> entry : startables.entrySet()) {
                    Startable startable = entry.getValue();
                    BeanLifecycle lifecycle = resourceManager.getBeanLifecycle();
                    if (!lifecycle.isBeanStarted(startable) && !lifecycle.isBeanDisabled(startable)) {
                        long s = System.currentTimeMillis();
                        startable.start();
                        if (!resourceManager.isSilent())
                            logger.info("Class {} started in {}ms", startable.getClass().getName(), (System.currentTimeMillis() - s));
                        resourceManager.getBeanLifecycle().fireBeanStarted(entry.getKey(), startable);
                    }
                }
            } catch (RuntimeException ex) {
                logger.error("Error running startable component name = {}", name);
                throw ex;
            }
        }
    }

    static class RunLaunched implements Runnable {
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
                for (Map.Entry<String, LaunchListener> entry : listeners.entrySet()) {
                    LaunchListener listener = entry.getValue();
                    BeanLifecycle lifecycle = provision.getResourceManager().getBeanLifecycle();
                    if (!lifecycle.isBeanLaunched(listener) && !lifecycle.isBeanDisabled(listener)) {
                        listener.launched(provision);
                        provision.getResourceManager().getBeanLifecycle().fireBeanLaunched(entry.getKey(), listener);
                    }
                }
            } catch (RuntimeException ex) {
                logger.error("Error running launched component name = {}", name);
                throw ex;
            }
        }
    }

    protected void cleanup() {
        resources.cleanup();
        configuration = null;
        boundInstances = null;
        resources = null;
        resourceManager = null;
        beanLifecycle = null;
        directives = null;
    }

    public Atom open(Consumer<Resources> consumer) {
        Resources res = null;
        try {
            res = resourceManager.open(null);
            consumer.accept(res);
        } finally {
            if (res != null)
                res.commit();
        }
        return this;
    }

    public <T> T getInstance(Class<T> cl) {
        return resources.getInstance(cl);
    }

    /*
     * runs callable after every script is loaded
     */
    public void runAfter(Object obj) {
        if (obj == null)
            return;
        if (obj instanceof List) {
            List l = (List) obj;
            for (Object o : l)
                resourceManager.runAfter(o);
        } else {
            resourceManager.runAfter(obj);
        }
    }

    public void run(Object caller, Object obj) {
        if (obj == null)
            return;
        if (obj instanceof List) {
            List l = (List) obj;
            for (Object o : l)
                resourceManager.runNow(caller, o);
        } else {
            resourceManager.runNow(caller, obj);
        }
    }

    public void run(Object obj) {
        run(this, obj);
    }

    /*
     * runs after all resourceManagers are launched.
     */
    public void runLaunched(Object obj) {
        if (obj == null)
            return;
        if (obj instanceof List) {
            List l = (List) obj;
            for (Object o : l)
                resourceManager.getScripting().runLaunched(o);
        } else {
            resourceManager.getScripting().runLaunched(obj);
        }
    }

    public void runWaitFor(Object obj) {
        if (obj == null)
            return;
        if (obj instanceof List) {
            List<Pair<String, Object>> l = (List) obj;
            for (Pair<String, Object> pair : l) {
                beanLifecycle.addBeanListener(pair.key(), new BeanListener() {
                    @Override
                    public void initialized(Object bean) {
                        if (pair.value() instanceof Closure) {
                            Closure closure = (Closure) pair.value();
                            closure.call(bean);
                        } else {
                            run(bean, pair.value());
                        }
                        beanLifecycle.removeBeanListener(this);
                    }
                });
            }
        } else if (obj instanceof Pair) {
            Pair<String, Object> pair = (Pair) obj;
            beanLifecycle.addBeanListener(pair.key(), new BeanListener() {
                @Override
                public void initialized(Object bean) {
                    if (pair.value() instanceof Closure) {
                        Closure closure = (Closure) pair.value();
                        closure.call(bean);
                    } else {
                        run(bean, pair.value());
                    }
                    beanLifecycle.removeBeanListener(this);
                }
            });
        }
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
    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public Object get(Object key) {

        if (key instanceof String && ((String) key).contains(".")) {
            String[] path = ((String) key).split("\\.");
            Object obj = get(path[0].trim());
            for (int i = 1; i < path.length; i++)
                obj = Reflection.getProperty(obj, path[i].trim());
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

        Object object = overrideSettings.get(key);

        if (object == null) {
            object = boundInstances.get(key);
        }

        // if null try resourceManager.getBean
        if (object == null) {
            if (key instanceof Class) {
                try {
                    object = resources.getInstance((Class) key);
                } catch (InstanceNotFoundException ex) {
                    Logger.suppress(ex);
                    object = resourceManager.getBean((Class) key);
                }
            } else {
                object = resourceManager.getBean(key.toString());
            }
        }

        if (object == null) {
            object = resourceManager.getScripting().get(key.toString());
        }

        if (object == null) {
            object = defaultSettings.get(key);
        }

        if (object == null
                && !directives.containsKey(key)) {
            logger.warn("variable {} not found", key);
        }
        return object;
    }

    private void registerBean(String key, Object value) {
        if (!key.startsWith("_")) {
            Object existing = resourceManager.getBean(key);
            if (existing == null) {
                resourceManager.addBean(key, value);
                resources.onOpen();
                resources.inject(value);
            } else {
                if (existing == value) {
                    // ok ignore
                } else {
                    throw logger.systemException("bean with name=" + name + " already registered");
                }
            }
        }
    }

    Object putResourceProvider(String key, Class cls) {
        Object instance = null;
        try {
            if (resourceManager.getBean(key) != null && !key.startsWith("_")) {
                instance = resourceManager.getBean(key);
                if (!cls.isAssignableFrom(instance.getClass())) {
                    throw new IllegalArgumentException("key=" + key + " has already been registered with ResourceManager.");
                }
            } else {
                instance = cls.getDeclaredConstructor().newInstance();
                resourceManager.addResourceProvider((ResourceProvider) instance);
                if (!key.startsWith("_")) {
                    resourceManager.addBean(key, instance);
                }
            }
        } catch (Exception e) {
            throw logger.systemException(e);
        }
        return instance;
    }

    Object putClass(String key, Class cls) {
        // creating an instance from Class
        Object instance = null;
        try {
            if (resourceManager.getBean(key) != null && !key.startsWith("_")) {
                instance = resourceManager.getBean(key);
                if (!cls.isAssignableFrom(instance.getClass())) {
                    throw new IllegalArgumentException("key=" + key + " has already been registered with ResourceManager.");
                }
            } else {
                instance = cls.getDeclaredConstructor().newInstance();
            }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw logger.systemException(e);
        }

        registerBean(key, instance);

        if (instance == null) {
            throw new SystemException("Cannot instantiate " + cls);
        }
        resources.rebind(cls, instance);
        return instance;
    }

    private Class loadClass(String className) {
        Class cls = null;
        try {
            cls = getClass().getClassLoader().loadClass(className);
        } catch (ClassNotFoundException ex) {
            // OK, just testing if the string represent a
        }
        try {
            if (cls == null && scriptLoader != null)
                cls = scriptLoader.loadClass(className);
        } catch (ClassNotFoundException ex) {
            // OK, just testing if the string represent a
        }
        return cls;
    }

    @Override
    @SuppressWarnings({"squid:S1141", "squid:S134", "squid:MethodCyclomaticComplexity"})
    public Object put(String key, Object value) {
        // NOTE: at this point resources is mostly likely not open so that inject won't work
        Object instance = null;
        boolean duplicate = false;
        try {
            if (value == null) {
                throw new IllegalArgumentException("value for key=" + key + " is null!  This happens because you did not import the class.");
            }

            if (value instanceof String) {
                String str = (String) value;
                Class cls = loadClass(str);
                if (cls != null)
                    value = cls;
            }

            if (value instanceof Class) {
                if (boundInstances.containsKey(key)) {
                    instance = boundInstances.get(key);
                    // we need to make instance is of the right class
                    Class cls = (Class) value;
                    if (!instance.getClass().isAssignableFrom(cls)) {
                        throw new IllegalArgumentException("Incompatible instance found for key=" + key
                                + ", the existing instance has type=" + instance.getClass().getName() + " but requested type=" + cls.getName());
                    }
                    duplicate = true;
                } else if (ResourceProvider.class.isAssignableFrom((Class) value)) {
                    instance = putResourceProvider(key, (Class) value);
                } else {
                    // creating an instance from Class
                    instance = putClass(key, (Class) value);
                }
                configure(instance, key);
            } else if (value instanceof ResourceProvider) {
                if (!key.startsWith("_")) {
                    resourceManager.addBean(key, value);
                }
                resourceManager.addResourceProvider((ResourceProvider) value);
                instance = value;
                configure(instance, key);
            } else if (value instanceof Configuration) {
                instance = value;
                resources.configurator.putAll((Configuration) value);
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
                } else {
                    instance = value;
                }
            } else {
                if (!resourceManager.getScripting().isRunnable(value)) { // value is not a closure
                    instance = resources.rebind((Class) value.getClass(), value);
                    registerBean(key, instance);
                    configure(instance, key);
                } else {
                    // this is either a Runnable or a Closure
                    instance = value;
                }
            }

            if (!duplicate) {
                // the below is to apply closure to instance.
                if (instance != null
                        && !resourceManager.getScripting().isRunnable(instance)                 // make sure instance is not a closure itself
                        && resourceManager.getScripting().isRunnable(boundInstances.get(key))   // See if there is a closure already in
                    // boundInstances and applies it to the instance.
                        ) {
                    Object closure = boundInstances.get(key);
                    resourceManager.runNow(instance, closure);
                }

                if (instance != null) {
                    if (instance instanceof ClassBeanListener) {
                        ClassBeanListener l = (ClassBeanListener) instance;
                        for (Class cl : l.listenFor())
                            beanLifecycle.addBeanListener(cl, l);
                    }
                    if (instance instanceof NamedBeanListener) {
                        NamedBeanListener l = (NamedBeanListener) instance;
                        for (String beanName : l.listenFor())
                            beanLifecycle.addBeanListener(beanName, l);
                    }
                    boundInstances.put(key, instance);
                }
            }

        } catch (NestedScriptException th) {
            // no logging as it is done by the nested script.
            throw th;
        } catch (Exception th) {
            throw new SystemException("Component name=" + getName() + " has issues", th);
        }

        return instance;
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Map.Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
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
        @Override
        public Object invokeMethod(String name, Object args) {
            Object existing = boundInstances.get(WAIT_FOR);
            List<Pair> list;
            if (existing instanceof List) {
                list = (List<Pair>) existing;
            } else {
                list = new LinkedList<>();
                boundInstances.put(WAIT_FOR, list);
            }
            Object[] arguments = (Object[]) args;
            Object value = arguments.length > 0 ? arguments[0] : null;
            if (value instanceof Closure) {
                list.add(new Pair(name, value));
            }

            return null;
        }
    }
}
