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

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.inject.ModuleFactory;
import net.e6tech.elements.common.instance.InstanceFactory;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.logging.TimedLogger;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.notification.ShutdownNotification;
import net.e6tech.elements.common.resources.plugin.PluginManager;
import net.e6tech.elements.common.script.AbstractScriptShell;
import net.e6tech.elements.common.util.monitor.AllocationMonitor;
import org.apache.logging.log4j.ThreadContext;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * The purpose of ResourceManager is to register globally  visible named instances.
 * Secondarily, it is used to bind interfaces or abstract classes to concrete classes.
 * Created by futeh.
 */
public class ResourceManager extends AbstractScriptShell implements ResourcePool {

    private static Logger logger = Logger.getLogger();

    private String name;
    private Injector injector;
    private Module module = ModuleFactory.getInstance().create();
    private List<ResourceProvider> resourceProviders = new LinkedList<>();
    private AllocationMonitor allocation = new AllocationMonitor();
    private Map<String, ResourceManager> resourceManagers;
    private Map<String, Atom> atoms = new LinkedHashMap<>();
    private NotificationCenter notificationCenter = new NotificationCenter();
    private BeanLifecycle beanLifecycle = new BeanLifecycle();
    private PluginManager pluginManager = new PluginManager(this);
    private List<ResourceManagerListener> listeners = new LinkedList<>();
    private Map<Class, ClassInjectionInfo> injections = new Hashtable<>(); // a cache to be used by Resources.

    public ResourceManager() {
        this(new Properties());
    }

    public ResourceManager(Provision provision) {
        initialize(pluginManager.getPluginClassLoader(), provision.getProperties());
        _initialize(provision.getProperties());

        Provision myProvision = loadProvision(provision.getClass());
        myProvision.load(provision.getResourceManager().getScripting().getVariables());
    }

    public ResourceManager(Properties properties) {
        initialize(pluginManager.getPluginClassLoader(), updateProperties(properties));
        _initialize(properties);
    }

    private void _initialize(Properties properties) {
        String logDir = properties.getProperty("logDir");
        if (logDir != null) ThreadContext.put("logDir", logDir);
        else {
            logDir = properties.getProperty(Logger.logDir);
            if (logDir != null) ThreadContext.put("logDir", logDir);
        }

        name = properties.getProperty("name");

        setModuleFactory(ModuleFactory.getInstance());

        Thread.currentThread().setContextClassLoader(pluginManager.getPluginClassLoader());
    }

    public void setModuleFactory(ModuleFactory factory) {
        module = factory.create();

        InstanceFactory instanceFactory = new InstanceFactory();
        module.bindInstance(ModuleFactory.class, factory);
        module.bindInstance(ResourceManager.class, this);
        module.bindInstance(NotificationCenter.class, notificationCenter);
        module.bindInstance(Interceptor.class, Interceptor.getInstance());
        module.bindInstance(InstanceFactory.class, instanceFactory);
        module.bindInstance(PluginManager.class, pluginManager);
        injector = module.build();

        getScripting().put("notificationCenter", notificationCenter);
        getScripting().put("interceptor", Interceptor.getInstance());
        getScripting().put("instanceFactory", instanceFactory);
        getScripting().put("pluginManager", pluginManager);
    }

    public void addListener(ResourceManagerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ResourceManagerListener listener) {
        listeners.remove(listener);
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public NotificationCenter getNotificationCenter() {
        return notificationCenter;
    }

    private static Properties updateProperties(Properties properties) {
        if (properties.getProperty("home") != null) {
            String home = properties.getProperty("home");
            String name = properties.getProperty("name");
            if (name == null) {
                try {
                    name = Paths.get(new File(home).getCanonicalPath()).getFileName().toString();
                    properties.setProperty("name", name);
                } catch (IOException e) {
                    throw new RuntimeException("Invalid home location " + home);
                }
            }
            properties.setProperty(name, home);
        }
        return properties;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (this.name != null) getScripting().remove(this.name);
        this.name = name;
        getScripting().put(name, getProperties().getProperty("home"));
    }

    public Map<String, ResourceManager> getResourceManagers() {
        return resourceManagers;
    }

    public void setResourceManagers(Map<String, ResourceManager> resourceManagers) {
        this.resourceManagers = resourceManagers;
    }

    public ResourceManager getResourceManager(String name) {
        return resourceManagers.get(name);
    }

    public AllocationMonitor getAllocationMonitor() {
        return allocation;
    }

    /**
     * Beware, this method is called from the parent thread.  Typically, a ResourceManager is created and runs in its
     * own thread.
     */
    public void onLaunched() {
        // try to set parent's logDir
        createLoggerContext();

        getScripting().onLaunched();
        super.onLoaded();
        beanLifecycle.clearBeanListeners();
    }

    /**
     * For creating a logger context.  This is especially true when a batch job is kicked off in
     * a thread.  In order for logging to work correctly, its ThreadContext needs to be populated.
     */
    public void createLoggerContext() {
        if (ThreadContext.get("logDir") == null) {
            String logDir = null;
            if (System.getProperty("logDir") != null) logDir = System.getProperty("logDir");
            else if (System.getProperty(Logger.logDir) != null) logDir = System.getProperty(Logger.logDir);
            else {
                Properties properties = getProperties();
                logDir = properties.getProperty("logDir");
                if (logDir == null) logDir = properties.getProperty(Logger.logDir);
            }
            if (logDir != null) ThreadContext.put("logDir", logDir);
        }
    }

    protected void onLoaded() {
        // do nothing, clean up is done in onLaunched.  super.onLoaded will remove closures.
    }

    public <T> T getAtomResource(String atomName, String resourceName) {
        return (T) getAtoms().get(atomName).get(resourceName);
    }

    public Map<String, Atom> getAtoms() {
        return Collections.unmodifiableMap(atoms);
    }

    public Atom getAtom(String name) {
        return atoms.get(name);
    }

    public Atom removeAtom(String name) {
        return atoms.remove(name);
    }

    public Atom createAtom(String atomName, Consumer<Atom> consumer, Atom prototypeAtom, boolean prototype) {
        if (name != null && atoms.get(atomName) != null) return atoms.get(atomName);
        Atom atom = new Atom(this, prototypeAtom);
        atom.setPrototype(prototype);
        atom.setName(atomName);
        // Groovy script holds on to closures that have references to atoms so that they are not
        // GC'ed.  Make sure lambda doesn't reference atom.
        /* StackTraceElement[] stackTrace = Reflection.getCallingStackTrace();
        allocation.monitor(30000L, atom, () -> {
            Throwable throwable = new Throwable();
            throwable.setStackTrace(stackTrace);
            String compName = (atomName == null) ? "anonymous" : atomName;
            logger.warn("Potential Atom leak " + compName, throwable);
        }); */

        if (atomName == null) {
            logger.warn("Atom name is null", new Throwable());
        } else {
            if (!prototype) atoms.put(atomName, atom);
        }
        consumer.accept(atom);
        TimedLogger timed = new TimedLogger(0);
        Atom comp = atom.build();
        timed.log("Atom " + atomName);
        return comp;
    }

    @SuppressWarnings("unchecked")
    public <T extends Provision> T loadProvision(Class<? extends Provision> clazz) {
        Class cls = clazz;

        Provision provision = null;
        try {
            provision = (Provision) cls.newInstance();
            inject(provision);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        boolean alreadyBound = false;
        while (Provision.class.isAssignableFrom(cls)) {
            try {
                bind((Class<Provision>) cls, provision);
            } catch (AlreadyBoundException ex) {
                alreadyBound = true;
            }
            cls = cls.getSuperclass();
        }

        if (alreadyBound) {
            return (T) getInstance(Provision.class);
        } else {
            provision.load(getScripting().getVariables());
            getScripting().put("provision", provision);
            final Provision p = provision;
            listeners.forEach(l -> l.provisionLoaded(p));
            return (T) provision;
        }

    }

    public ResourceManager getResourceManager() {
        return this;
    }

    protected Injector getInjector() {
        return injector;
    }

    public boolean hasInstance(Class clazz) {
        if (Provision.class.isAssignableFrom(clazz)) {
            return true;
        }
        return injector.getInstance(clazz) != null;
    }

    public <T> T getInstance(Class<T> clazz) throws InstanceNotFoundException {
        T value = injector.getInstance(clazz);
        if (value == null) {
            if (Provision.class.isAssignableFrom(clazz)) {
                value = (T) loadProvision((Class<Provision>) clazz);
            } else {
                throw new InstanceNotFoundException("No instance for class " + clazz.getName());
            }
        }
        return value;
    }

    public <T> T bind(Class<T> cls, T resource) {
        Object o = module.getBoundInstance(cls);
        if (o != null) throw new AlreadyBoundException("Class " + cls + " is already bound to " + o);
        module.bindInstance(cls, resource);
        injector = module.build();
        T instance = getInstance(cls);
        listeners.forEach(l -> l.bound(cls, instance));
        return instance;
    }

    public <T> T rebind(Class<T> cls, T resource) {
        module.bindInstance(cls, resource);
        injector = module.build();
        T instance = getInstance(cls);
        listeners.forEach(l -> l.bound(cls, instance));
        return instance;
    }

    public <T> T unbind(Class<T> cls) {
        T instance = (T) module.unbindInstance(cls);
        injector = module.build();
        listeners.forEach(l -> l.unbound(cls, instance));
        return instance;
    }

    public void tryBindClass(Class cls, Class service) {
        try {
            bindClass(cls, service);
        } catch (AlreadyBoundException ex) {
        }
    }

    public void bindClass(Class cls, Class service) {
        Class c = module.getBoundClass(cls);
        if (c != null) throw new AlreadyBoundException("Class " + cls + " is already bound to " + c);
        if (service != null) {
            module.bindClass(cls, service);
        } else {
            module.bindInstance(cls, null);
        }
        injector = module.build();

        if (service != null) {
            listeners.forEach(l -> l.classBound(cls, service));
        } else {
            listeners.forEach(l -> l.classBound(cls, null));
        }
    }

    public <T> T bindNamedInstance(Class<T> a, String name, T b) {
        T instance = module.getBoundNamedInstance(a, name);
        if (instance != null) {
            throw new AlreadyBoundException("Instance named " + name + " is already bound to " + instance);
        } else {
            module.bindNamedInstance(a, name, b);
            injector = module.build();
            listeners.forEach(l -> l.namedInstanceBound(name, a, b));
        }
        return instance;
    }

    public <T> T rebindNamedInstance(Class<T> cls, String name, T resource) {
        T instance = (T) module.bindNamedInstance(cls, name, resource);
        injector = module.build();
        listeners.forEach(l -> l.namedInstanceBound(name, cls, instance));
        return instance;
    }

    public <T> T inject(T obj) {
        if (obj == null) return obj;
        if (obj instanceof InjectionListener) {
            ((InjectionListener) obj).preInject(this);
        }
        injector.inject(obj);
        if (obj instanceof InjectionListener) {
            ((InjectionListener) obj).injected(this);
        }
        listeners.forEach(l -> l.injected(obj));
        return obj;
    }

    public BeanLifecycle getBeanLifecycle() {
        return beanLifecycle;
    }

    public <T> T registerBean(String name, Object instance) {
        Object obj = instance;
        if (instance instanceof Class) {
            obj = newInstance((Class) instance);
        } else {
            inject(obj);
        }
        return addBean(name, obj);
    }

    protected <T> T addBean(String name, Object instance) {
        if (getScripting().getVariables().get(name) != null) {
            throw logger.runtimeException("bean with name=" + name + " already registered");
        }

        try {
            Method method = instance.getClass().getMethod("setName", String.class);
            method.invoke(instance, name);
        } catch (Throwable e) {
        }

        getScripting().put(name, instance);

        listeners.forEach(l -> l.beanAdded(name, instance));

        return (T) instance;
    }

    public void unregisterBean(String name) {
        Object instance = getScripting().remove(name);
        listeners.forEach(l -> l.beanRemoved(name, instance));
    }

    public <T> T getBean(String name) {
        return (T) getScripting().getVariables().get(name);
    }

    public <T> T getBean(Class<T> cls) {
        T value = null;
        Map<String, Object> variables = getScripting().getVariables();
        for (String key : variables.keySet()) {
            Object obj = variables.get(key);
            if (obj == null) continue;
            if (cls.isAssignableFrom(obj.getClass())) {
                if (value != null) throw new RuntimeException("Multiple objects can be assigned to " + cls);
                value = (T) obj;
            }
        }
        return value;
    }

    public Map<String, Object> getBeans() {
        return getBeans(null);
    }

    public <T> Map<String, T> getBeans(Class<T> cls) {
        Map<String, T> map = new HashMap<>();
        getScripting().getVariables().forEach((key, value)-> {
            if (cls == null || (value != null && cls.isAssignableFrom(value.getClass())))
                map.put(key, (T) value);
        });
        return Collections.unmodifiableMap(map);
    }

    public List listBeans() {
        Map<String, Object> variables = getScripting().getVariables();
        List list = new ArrayList(variables.size());
        variables.values().forEach(b -> list.add(b));
        return Collections.unmodifiableList(list);
    }

    public Module getModule() {
        return module;
    }

    @Override
    public synchronized void load(String str) throws ScriptException {
        load(str, true);
    }

    public synchronized void load(String str, boolean logInfo) throws ScriptException {
        long start = System.currentTimeMillis();
        super.load(str);

        if (logInfo) {
            int len = 0;
            List<String> atomString = new LinkedList<>();
            StringBuilder builder = new StringBuilder();
            builder.append("    ");
            int count = atoms.size();
            int i = 1;
            for (String atomName : atoms.keySet()) {
                builder.append(atomName);
                if (i != count) builder.append(", ");
                if (i % 5 == 0) {
                    String msg = builder.toString();
                    if (len < msg.length()) len = msg.length();
                    atomString.add(builder.toString());
                    builder.setLength(0);
                    builder.append("    ");
                } else if (i == count) {
                    String msg = builder.toString();
                    if (len < msg.length()) len = msg.length();
                    atomString.add(builder.toString());
                }
                i++;
            }

            String message = "Done processing " + str;
            String message2 = "ResourceManager " + name + " loaded in " + (System.currentTimeMillis() - start) + "ms";
            if (message.length() > len) len = message.length();
            if (message2.length() > len) len = message2.length();
            char[] line = new char[len];
            Arrays.fill(line, '*');
            logger.info(new String(line));
            logger.info(message);
            logger.info(message2);
            logger.info("Loaded atoms:");
            for (String msg : atomString) logger.info(msg);
            logger.info(new String(line));
        }
    }

    public <Res extends Resources> Res open(Configurator configurator) {
        return open(configurator, (resources) -> {
        });
    }

    public <Res extends Resources> Res open(Configurator configurator, Consumer<Res> preOpen) {
        Res resources = newResources();
        resources.configure(configurator);

        inject(resources);

        if (preOpen != null) {
            resources.setPreOpen(preOpen);
            preOpen.accept(resources);  // before resourceProviders in order to set configuration
        }

        List<ResourceProvider> list = new LinkedList<>();
        synchronized (resourceProviders) {
            list.addAll(resourceProviders);
        }

        List<ResourceProvider> openList = new LinkedList<>();
        for (ResourceProvider p : list) {
            try {
                p.onOpen(resources);
                openList.add(p);
            } catch (NotAvailableException ex) {
            } catch (Throwable th) {
                resources.setExternalResourceProviders(openList);
                resources.onOpen();
                resources.abort();
                throw th;
            }
        }

        resources.setExternalResourceProviders(openList);
        resources.onOpen();

        /** uncomment the following to detect resources leak
        long timeout = 10000;
        if (resources.getConfiguration(Resources.TIMEOUT) != null)
            timeout = resources.getConfiguration(Resources.TIMEOUT);

        if (resources.getConfiguration(Resources.TIMEOUT_EXTENSION) != null) {
            timeout += (Long) resources.getConfiguration(Resources.TIMEOUT_EXTENSION);
        }
        Throwable throwable = new Throwable();
        throwable.setStackTrace(Reflection.getCallingStackTrace());
        allocation.monitor(timeout, resources, () -> {
                logger.warn("Potential Resources leak.", throwable);
        });
         */

        return resources;
    }

    public void addResourceProvider(ResourceProvider p) {
        inject(p);
        synchronized (resourceProviders) {
            resourceProviders.add(p);
        }
        listeners.forEach(l -> l.resourceProviderAdded(p));
    }

    public <T extends Resources> T newResources() {
        Provision provision = getInstance(Provision.class);
        Class clazz = provision.getResourcesClass();
        try {
            Constructor constructor = clazz.getDeclaredConstructor(ResourceManager.class);
            constructor.setAccessible(true);
            T resources = (T) constructor.newInstance(this);
            return inject(resources);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        ShutdownNotification notification = new ShutdownNotification(this);
        getNotificationCenter().getNotificationListeners(notification)
                .forEach(listener -> {
                    logger.info("Shutting down " + listener.getDescription() + " ...");
                    listener.onEvent(notification);
                    logger.info(listener.getDescription() + " is down.");
                });

        List<ResourceProvider> reversed = new ArrayList<>();
        synchronized (resourceProviders) {
            reversed.addAll(resourceProviders);
        }
        Collections.reverse(reversed);
        reversed.forEach(rp -> {
            logger.info("Shutting down " + rp.getDescription() + " ...");
            rp.onShutdown();
            logger.info(rp.getDescription() + " is down.");
        });
    }

    Map<Class, ClassInjectionInfo> getInjections() {
        return injections;
    }

    static class ClassInjectionInfo {
        private static final List<Field> emptyFields = Collections.unmodifiableList(new ArrayList<>());
        private List<Field> injectableFields = emptyFields;

        void addInjectableField(Field field) {
            if (injectableFields == emptyFields) injectableFields = new ArrayList<>();
            injectableFields.add(field);
        }

        List<Field> getInjectableFields() {
            return injectableFields;
        }
    }
}
