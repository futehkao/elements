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

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.inject.ModuleFactory;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.logging.TimedLogger;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.notification.NotificationListener;
import net.e6tech.elements.common.notification.ShutdownNotification;
import net.e6tech.elements.common.resources.plugin.PluginManager;
import net.e6tech.elements.common.script.AbstractScriptShell;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.monitor.AllocationMonitor;
import org.apache.logging.log4j.ThreadContext;

import javax.script.ScriptException;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The purpose of ResourceManager is to register globally  visible named instances.
 * Secondarily, it is used to bind interfaces or abstract classes to concrete classes.
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class ResourceManager extends AbstractScriptShell implements ResourcePool {

    private static Logger logger = Logger.getLogger();
    static final String LOG_DIR_ABBREV = "logDir";
    private static final String ALREADY_BOUND_MSG = "Class %s is already bound to %s";
    private static Map<String, ResourceManager> resourceManagers = new ConcurrentHashMap<>();

    private String name;
    private Injector injector;
    private Module module = ModuleFactory.getInstance().create();
    private List<ResourceProvider> resourceProviders = new LinkedList<>();
    private AllocationMonitor allocation = new AllocationMonitor();

    private Map<String, Atom> atoms = new LinkedHashMap<>();
    private NotificationCenter notificationCenter = new NotificationCenter();
    private BeanLifecycle beanLifecycle = new BeanLifecycle();
    private PluginManager pluginManager = new PluginManager(this);
    private List<ResourceManagerListener> listeners = new LinkedList<>();
    private Map<Class, ClassInjectionInfo> injections = new ConcurrentHashMap<>(); // a cache to be used by Resources.
    private boolean silent = false;

    public ResourceManager() {
        this(new Properties());
    }

    // this is for running a script based on an existing provision, i.e. a very transient process.
    public ResourceManager(Provision provision) {
        initialize(pluginManager.getPluginClassLoader(), provision.getProperties());
        selfInit(provision.getProperties());

        Provision myProvision = loadProvision(provision.getClass());
        myProvision.load(provision.getResourceManager().getScripting().getVariables());
    }

    // This is for launch a brand new ResourceManager
    public ResourceManager(Properties properties) {
        initialize(pluginManager.getPluginClassLoader(), updateProperties(properties));
        selfInit(properties);

        if (getName() != null) {
            if (getName() != null && getResourceManagers().containsKey(getName()))
                throw new SystemException("ResourceManager with name=" + getName() + " exists.");
            resourceManagers.put(getName(), this);
        }
    }

    public static Map<String, ResourceManager> getResourceManagers() {
        return Collections.unmodifiableMap(resourceManagers);
    }

    public static ResourceManager getResourceManager(String name) {
        return resourceManagers.get(name);
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
        getScripting().setSilent(silent);
    }

    public ResourceManager silent(boolean silent) {
        setSilent(silent);
        return this;
    }

    public Bootstrap getBootstrap() {
        return nullableVar("bootstrap");
    }

    public ClassLoader getPluginClassLoader() {
        return getPluginManager().getPluginClassLoader();
    }

    private void selfInit(Properties properties) {
        String logDir = properties.getProperty(LOG_DIR_ABBREV);
        if (logDir != null)
            ThreadContext.put(LOG_DIR_ABBREV, logDir);
        else {
            logDir = properties.getProperty(Logger.logDir);
            if (logDir != null)
                ThreadContext.put(LOG_DIR_ABBREV, logDir);
        }

        name = properties.getProperty("name");

        setModuleFactory(ModuleFactory.getInstance());

        Thread.currentThread().setContextClassLoader(pluginManager.getPluginClassLoader());
    }

    public void setModuleFactory(ModuleFactory factory) {
        module = factory.create();

        module.bindInstance(ModuleFactory.class, factory);
        module.bindInstance(ResourceManager.class, this);
        module.bindInstance(NotificationCenter.class, notificationCenter);
        module.bindInstance(Interceptor.class, Interceptor.getInstance());
        module.bindInstance(PluginManager.class, pluginManager);
        injector = module.build(false);

        getScripting().put("notificationCenter", notificationCenter);
        getScripting().put("interceptor", Interceptor.getInstance());
        getScripting().put("pluginManager", pluginManager);
        getScripting().put("bootstrap", new Bootstrap(this));
    }

    public void addListener(ResourceManagerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ResourceManagerListener listener) {
        listeners.remove(listener);
    }

    public void onShutdown(String description, NotificationListener<ShutdownNotification> listener) {
        getNotificationCenter()
                .addNotificationListener(ShutdownNotification.class, NotificationListener.wrap(description, listener));
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
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
                    throw logger.systemException("Invalid home location " + home, e);
                }
            }
            properties.setProperty(name, home);
        }
        return properties;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        if (this.name != null) {
            getScripting().remove(this.name);
            resourceManagers.remove(this.name);
        }

        this.name = name;
        getScripting().put(name, getProperties().getProperty("home"));
        resourceManagers.put(this.name, this);
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
        if (ThreadContext.get(LOG_DIR_ABBREV) == null) {
            String logDir = null;
            if (System.getProperty(LOG_DIR_ABBREV) != null)
                logDir = System.getProperty(LOG_DIR_ABBREV);
            else if (System.getProperty(Logger.logDir) != null)
                logDir = System.getProperty(Logger.logDir);
            else {
                Properties properties = getProperties();
                logDir = properties.getProperty(LOG_DIR_ABBREV);
                if (logDir == null)
                    logDir = properties.getProperty(Logger.logDir);
            }
            if (logDir != null)
                ThreadContext.put(LOG_DIR_ABBREV, logDir);
        }
    }

    @Override
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

    @SuppressWarnings("squid:CommentedOutCodeLine")
    public Atom createAtom(String atomName, Consumer<Atom> consumer, Atom prototypeAtom, boolean prototype) {
        if (name != null && atoms.get(atomName) != null)
            return atoms.get(atomName);
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
            if (!prototype)
                atoms.put(atomName, atom);
            else {
                String overrideName = (String) getScripting().get(Atom.OVERRIDE_NAME);
                if (overrideName != null)
                    atom.setName(overrideName);
            }

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
            provision = (Provision) cls.getDeclaredConstructor().newInstance();
            inject(provision);
        } catch (Exception e) {
            throw new SystemException(e);
        }

        boolean alreadyBound = false;
        while (Provision.class.isAssignableFrom(cls)) {
            try {
                bind((Class<Provision>) cls, provision);
            } catch (AlreadyBoundException ex) {
                Logger.suppress(ex);
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

    @SuppressWarnings("squid:S1905")
    public <T> T getInstance(Class<T> clazz) {
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
        if (o != null)
            throw new AlreadyBoundException(String.format(ALREADY_BOUND_MSG, cls, o));
        module.bindInstance(cls, resource);
        injector = module.build(false);
        T instance = getInstance(cls);
        listeners.forEach(l -> l.bound(cls, instance));
        return instance;
    }

    public <T> T rebind(Class<T> cls, T resource) {
        module.rebindInstance(cls, resource);
        injector = module.build(false);
        T instance = getInstance(cls);
        listeners.forEach(l -> l.bound(cls, instance));
        return instance;
    }

    public <T> T unbind(Class<T> cls) {
        T instance = (T) module.unbindInstance(cls);
        injector = module.build(false);
        listeners.forEach(l -> l.unbound(cls, instance));
        return instance;
    }

    public void tryBindClass(Class cls, Class service) {
        try {
            bindClass(cls, service);
        } catch (AlreadyBoundException ex) {
            Logger.suppress(ex);
        }
    }

    public void bindClass(Class cls, Class service) {
        Class c = module.getBoundClass(cls);
        if (c != null)
            throw new AlreadyBoundException(String.format(ALREADY_BOUND_MSG, cls, c));
        if (service != null) {
            module.bindClass(cls, service);
        } else {
            module.bindInstance(cls, null);
        }
        injector = module.build(true);

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
            injector = module.build(false);
            listeners.forEach(l -> l.namedInstanceBound(name, a, b));
        }
        return instance;
    }

    public <T> T rebindNamedInstance(Class<T> cls, String name, T resource) {
        T instance = (T) module.rebindNamedInstance(cls, name, resource);
        injector = module.build(false);
        listeners.forEach(l -> l.namedInstanceBound(name, cls, instance));
        return instance;
    }

    public <T> T inject(T obj) {
        return inject(obj, true);
    }

    /**
     *
     * @param obj target
     * @param strict usually it should be true.  Only false if you are injecting a prototype plugin.
     * @param <T> Type of obj to be injected
     * @return the injected object
     */
    public <T> T inject(T obj, boolean strict) {
        if (obj == null)
            return null;

        if (strict) {
            if (obj instanceof InjectionListener) {
                ((InjectionListener) obj).preInject(this);
            }

            injector.inject(obj, true);

            if (obj instanceof InjectionListener) {
                ((InjectionListener) obj).injected(this);
            }

            listeners.forEach(l -> l.injected(obj));
        } else {
            injector.inject(obj, false);
        }

        return obj;
    }

    public BeanLifecycle getBeanLifecycle() {
        return beanLifecycle;
    }

    public <T> T registerBean(String name, Object instance) {
        return addBean(name, createBean(instance), false);
    }

    private Object createBean(Object instance) {
        if (instance == null)
            throw new NullPointerException("instance is null");
        Object obj = instance;
        if (instance instanceof Class) {
            obj = newInstance((Class) instance);
        } else {
            inject(obj, false);
        }
        return obj;
    }

    protected <T> T addBean(String name, Object instance) {
        return addBean(name, instance, false);
    }

    protected <T> T addBean(String name, Object instance, boolean overwrite) {
        if (!overwrite &&  getScripting().getVariables().get(name) != null) {
            throw logger.systemException("bean with name=" + name + " already registered");
        }

        String existingName = null;
        try {
            Method method = instance.getClass().getMethod("getName");
            existingName = (String) method.invoke(instance, name);
        } catch (Exception ex) {
            Logger.suppress(ex);
        }

        try {
            if (existingName == null) {
                Method method = instance.getClass().getMethod("setName", String.class);
                method.invoke(instance, name);
            }
        } catch (Exception ex) {
            Logger.suppress(ex);
        }

        getScripting().put(name, instance);
        listeners.forEach(l -> l.beanAdded(name, instance));
        return (T) instance;
    }

    public void unregisterBean(String name) {
        Object instance = getScripting().remove(name);
        if (instance != null)
            listeners.forEach(l -> l.beanRemoved(name, instance));
    }

    @Override
    public <T> T getBean(String name) {
        return (T) getScripting().getVariables().get(name);
    }

    @Override
    public <T> T getBean(Class<T> cls) {
        T value = null;
        Map<String, Object> variables = getScripting().getVariables();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object obj = entry.getValue();
            if (obj == null)
                continue;
            if (cls.isAssignableFrom(obj.getClass())) {
                if (value != null)
                    throw new SystemException("Multiple objects can be assigned to " + cls);
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
        list.addAll(variables.values());
        return Collections.unmodifiableList(list);
    }

    public Module getModule() {
        return module;
    }

    @Override
    public synchronized void load(String str) throws ScriptException {
        long start = System.currentTimeMillis();
        super.load(str);
        printAtoms(str, start);
    }

    @Override
    public synchronized void load(String loadDir, String str) throws ScriptException {
        long start = System.currentTimeMillis();
        logger.info("Using specified load directory {}", loadDir);
        super.load(loadDir, str);
        printAtoms(str, start);
    }

    @SuppressWarnings("squid:S3776")
    private void printAtoms(String str, long start) {
        if (!silent) {
            int len = 0;
            List<String> atomString = new LinkedList<>();
            StringBuilder builder = new StringBuilder();
            builder.append("    ");
            int count = atoms.size();
            int i = 1;
            for (String atomName : atoms.keySet()) {
                builder.append(atomName);
                if (i != count)
                    builder.append(", ");
                if (i % 5 == 0) {
                    String msg = builder.toString();
                    if (len < msg.length())
                        len = msg.length();
                    atomString.add(builder.toString());
                    builder.setLength(0);
                    builder.append("    ");
                } else if (i == count) {
                    String msg = builder.toString();
                    if (len < msg.length())
                        len = msg.length();
                    atomString.add(builder.toString());
                }
                i++;
            }

            String message = "Done processing " + str;
            String message2 = "ResourceManager " + name + " loaded in " + (System.currentTimeMillis() - start) + "ms";
            if (message.length() > len)
                len = message.length();
            if (message2.length() > len)
                len = message2.length();
            char[] line = new char[len];
            Arrays.fill(line, '*');
            if (logger.isInfoEnabled()) {
                logger.info(new String(line));
                logger.info(message);
                logger.info(message2);
                logger.info("Loaded atoms:");
                for (String msg : atomString)
                    logger.info(msg);
                logger.info("{}\n", new String(line));
            }
        }
    }

    public <T extends Resources> T open(Configurator configurator) {
        return open(configurator, resources -> {
            // do nothing
        });
    }

    public <T extends Resources> T open(Configurator configurator, Consumer<T> preOpen) {
        T resources = newResources();
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
                Logger.suppress(ex);
            } catch (Exception th) {
                Logger.suppress(th);
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
            throw new SystemException(e);
        }
    }

    public void shutdown() {
        ShutdownNotification notification = new ShutdownNotification(this);
        getNotificationCenter().getNotificationListeners(notification)
                .forEach(listener -> {
                    if (!silent)
                        logger.info("Shutting down {} ...", listener.getDescription());
                    listener.onEvent(notification);
                    if (!silent)
                        logger.info("{} is down.", listener.getDescription());
                });

        List<ResourceProvider> reversed = new ArrayList<>();
        synchronized (resourceProviders) {
            reversed.addAll(resourceProviders);
        }
        Collections.reverse(reversed);
        reversed.forEach(rp -> {
            if (!silent)
                logger.info("Shutting down {} ...", rp.getDescription());
            rp.onShutdown();
            if (!silent)
                logger.info("{} is down.", rp.getDescription());
        });
        if (name != null)
            resourceManagers.remove(name);

        try {
            getScripting().shutdown();
            pluginManager.getPluginClassLoader().close();
        } catch (Exception e) {
            // don't care at this point.
        }
        getAllocationMonitor().shutdown();
    }

    Map<Class, ClassInjectionInfo> getInjections() {
        return injections;
    }

    static class ClassInjectionInfo {
        private static final List emptyList = Collections.unmodifiableList(new ArrayList<>());
        private List<Field> injectableFields = emptyList;
        private List<PropertyDescriptor> injectableProperties = emptyList;

        void addInjectableField(Field field) {
            if (injectableFields == emptyList)
                injectableFields = new ArrayList<>();
            injectableFields.add(field);
        }

        List<Field> getInjectableFields() {
            return injectableFields;
        }

        void addInjectableProperty(PropertyDescriptor desc) {
            if (injectableProperties == emptyList)
                injectableProperties = new ArrayList<>();
            injectableProperties.add(desc);
        }

        List<PropertyDescriptor> getInjectableProperties() {
            return injectableProperties;
        }
    }
}
