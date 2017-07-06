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

import com.google.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.plugin.PluginPath;
import net.e6tech.elements.common.resources.plugin.Plugin;
import net.e6tech.elements.common.resources.plugin.PluginManager;
import net.e6tech.elements.common.util.ExceptionMapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A Resources instance is use to manage resource level injection and resources.
 * Rules for injection.  Only annotate injection for instances that are configured
 * during program start up.  During runtime, it is better to store resources via put.
 * This prevents a overly complicated dependency wiring.  For dynamically created
 * instances, really the only needed injected resource is the Resources instance and
 * resources provided by ResourceProviders.
 *
 *
 * Created by futeh.
 */
@BindClass(Resources.class)
public class Resources implements AutoCloseable, ResourcePool {

    private static Logger logger = Logger.getLogger(Resources.class);
    private static final Map<Class, ClassInjectionInfo> injections = new HashMap<>();
    private static final Map<Class<? extends Annotation>, Annotation> emptyAnnotations = Collections.unmodifiableMap(new HashMap<>());
    private static final List<ResourceProvider> emptyResourceProviders = Collections.unmodifiableList(new ArrayList<>());

    @Inject
    private ResourceManager resourceManager;

    @Inject(optional = true)
    private Retry retry;

    protected ResourcesState state = new ResourcesState();
    protected Configurator configurator = new Configurator();
    private List<ResourceProvider> externalResourceProviders;
    private Consumer<? extends Resources> preOpen;
    private List<Replay<? extends Resources, ?>> unitOfWork = new LinkedList<>();
    Object lastResult;
    boolean submitting = false;

    protected Resources() {
        getModule().bindInstance(getClass(), this);
    }

    void setPreOpen(Consumer<? extends Resources> preOpen) {
        this.preOpen = preOpen;
    }

    public synchronized boolean isCommitted() {
        return state.getState() == ResourcesState.State.Committed;
    }


    public synchronized boolean isOpen() {
        return state.getState() == ResourcesState.State.Open;
    }

    public synchronized boolean isAborted() {
        return state.getState() == ResourcesState.State.Aborted;
    }

    public synchronized boolean isClosed() {
        return !isOpen();
    }

    public synchronized boolean isDiscarded() {
        return resourceManager == null;
    }

    List<ResourceProvider> getExternalResourceProviders() {
        if (externalResourceProviders == null) return emptyResourceProviders;
        return externalResourceProviders;
    }

    void setExternalResourceProviders(List<ResourceProvider> externalResourceProviders) {
        this.externalResourceProviders = externalResourceProviders;
    }

    private List<ResourceProvider> getResourceProviders() {
        return state.getResourceProviders();
    }

    public synchronized void addResourceProvider(ResourceProvider resourceProvider) {
        getResourceProviders().add(resourceProvider);
        if (isOpen()) {
            resourceProvider.onOpen(this);
        }

        if (isCommitted()) {
            resourceProvider.onCommit(this);
        }

        if (isCommitted()) {
            resourceProvider.afterCommit(this);
        }

        if (isAborted()) {
            resourceProvider.onAbort(this);
        }
    }

    public void onCommit(OnCommit onCommit) {
        addResourceProvider(onCommit);
    }

    public void onCommit(Runnable runnable) {
        OnCommit on = (res) -> {
            runnable.run();
        };
        onCommit(on);
    }

    public void afterCommit(AfterCommit afterCommit) {
        addResourceProvider(afterCommit);
    }

    public void afterCommit(Runnable runnable) {
        AfterCommit after = (res) -> {
            runnable.run();
        };
        afterCommit(after);
    }

    public synchronized void onOpen(OnOpen onOpen) {
        addResourceProvider(onOpen);
    }

    public synchronized void onOpen(Runnable runnable) {
        OnOpen on = (res) -> {
            runnable.run();
        };
        onOpen(on);
    }

    public synchronized void onAbort(OnAbort onAbort) {
        addResourceProvider(onAbort);
    }

    public synchronized void onAbort(Runnable runnable) {
        OnAbort on = (res) -> {
            runnable.run();
        };
        onAbort(on);
    }

    public synchronized void onClosed(OnClosed onClosed) {
        addResourceProvider(onClosed);
    }

    public synchronized void onClosed(Runnable runnable) {
        OnClosed on = (res) -> {
            runnable.run();
        };
        onClosed(on);
    }

    public synchronized boolean remove(ResourceProvider provider) {
        return getResourceProviders().remove(provider);
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    /**
     * Plugins are identified by class, a name and then a particular plugin class.  The argument are injected into the plugin.
     * For example, let say there is a class called Partner and it may be associated with several plugin types.  Furthermore,
     * the plugins associated with a Partner may vary based on the partner.  To create the plugin, one may search based on
     * Partner.class, partner name, plugin class and a list of arguments to be injected into the plugin.
     * @param c1
     * @param n1
     * @param c2
     * @param args
     * @param <T>
     * @return
     */
    public <S, T extends Plugin> T getPlugin(Class<S> c1, String n1, Class<T> c2, Object ... args) {
        return (T) getPlugin(PluginPath.of(c1, n1).and(c2), args);
    }

    public <R,S,T extends Plugin> T getPlugin(Class<R> c1, String n1, Class<S> c2, String n2, Class<T> c3, Object ... args) {
        return (T) getPlugin(PluginPath.of(c1, n1).and(c2, n2).and(c3), args);
    }

    public <T extends Plugin> T getPlugin(PluginPath<T> path, Object ... args) {
        PluginManager plugin = getInstance(PluginManager.class);
        return (T) plugin.from(this).get(path, args);
    }

    public <T> T getVariable(String variable) {
        return resourceManager.getVariable(variable);
    }

    public InjectionModule getModule() {
        return state.getModule();
    }

    public void addModule(InjectionModule module) {
        state.addModule(module);
    }

    public <T> Binding<T> getBinding(Class<T> cls) {
        Binding<T> boundInstance = new Binding<>(this, cls);
        return boundInstance;
    }

    public <T> T tryBind(Class<T> cls, Callable<T> callable) {
        return state.tryBind(this, cls, callable);
    }

    public <T> boolean isBound(Class<T> cls) {
        return (getModule().getBoundInstance(cls) != null) ? true : false;
    }

    public <T> T bind(Class<T> cls, T resource) {
        return state.bind(this, cls, resource);
    }

    public <T> T rebind(Class<T> cls, T resource) {
        return state.rebind(this, cls, resource);
    }

    public <T> T unbind(Class<T> cls) {
        return state.unbind(cls);
    }

    /**
     * This method is mostly used for unit testing.  the bound class disappear once resources is closed.
     * Unless you know what you are doing, please don't use.
     * @param cls Class to bind to
     * @param service  service class
     */
    public void bindClass(Class cls, Class service) {
        state.bindClass(cls, service);
    }

    public <T> T bindNamedInstance(String name, Class<T> cls, T resource) {
        return state.bindNamedInstance(name, cls, resource);
    }

    public <T> T rebindNamedInstance(String name, Class<T> cls, T resource) {
        return state.rebindNamedInstance(name, cls, resource);
    }

    public <T> T getBoundNamedInstance(String name) {
        return getModule().getBoundNamedInstance(name);
    }


    public <T> T inject(T object) {
        return inject(object, new HashSet<>());
    }

    private <T> T inject(T object, Set<Object> seen) {
        if (object == null) return null;
        // the commented out line indicates that we cannot use seen.contains(object)
        // because it is being injected and its hashCode may not be ready to be computed
        // so that the object should not be added to seen.
        // if (seen.contains(System.identityHashCode(object)) && seen.contains(object))
        // as a compromise, we use identifyHashCode
        if (seen.contains(System.identityHashCode(object)))
            return object;  // already been injected.
        T injected = state.inject(this, object);
        seen.add(System.identityHashCode(object));
        // seen.add(object);  object may not be initialized fully to compute hashCode.

        ClassInjectionInfo info;
        synchronized (injections) {
            info = injections.get(object.getClass());
            if (info == null) {
                info = new ClassInjectionInfo();
                injections.put(object.getClass(), info);
                Class cls = object.getClass();
                Package p = cls.getPackage();
                if (p == null
                        || (!p.getName().startsWith("java.")
                        && !p.getName().startsWith("javax."))) {
                    while (cls != null && !cls.equals(Object.class)) {
                        for (Field f : cls.getDeclaredFields()) {
                            if (f.getAnnotation(Injectable.class) != null
                                    || f.getType().getAnnotation(Injectable.class) != null) {
                                f.setAccessible(true);
                                info.addInjectableField(f);
                            }
                        }
                        cls = cls.getSuperclass();
                    }
                }
            }
        }

        for (Field f : info.getInjectableFields()) {
            try {
                Object injectField = f.get(object);
                if (injectField != null) {
                    inject(injectField, seen);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return injected;
    }

    public boolean hasInstance(Class cls) {
        return state.hasInstance(this, cls);
    }

    public <T> T getInstance(Class<T> cls) throws InstanceNotFoundException {
        return state.getInstance(this, cls);
    }

    public <T> T getInstance(Class<T> cls, Supplier<T> call) {
        try {
            return state.getInstance(this, cls);
        } catch (InstanceNotFoundException ex) {
            return call.get();
        }
    }

    public Configurator configurator() {
        return configurator;
    }

    public void configure(Configurator configurator) {
        this.configurator.putAll(configurator);
    }

    public synchronized void onOpen() {
        state.initModules(this);
        if (!isOpen()) {
            state.setState(ResourcesState.State.Open);
            // this loop can produce recursive onOpen call
            for (ResourceProvider resourceProvider : state.getResourceProviders()) {
                resourceProvider.onOpen(this);
            }
        }
    }

    protected <Res extends Resources, R> R replay(Throwable th, Replay<Res, R> replay) {
        if (isAborted() || retry == null) {
            log("Aborting due to exception", th);
            if (!isAborted()) abort();
            if (th instanceof RuntimeException) throw (RuntimeException) th;
            throw new RuntimeException(th);
        }
        try {
            return retry.retry(th, () -> {
                StringBuilder builder = new StringBuilder();
                builder.append("Resources retrying due to error: ")
                        .append(ExceptionMapper.unwrap(th).getClass())
                        .append(", message: ")
                        .append(th.getMessage());
                Reflection.printStackTrace(builder, "    ", 2, 8);
                logger.warn(builder.toString());

                try { abort(); } catch (Throwable th2) {}

                Res retryResources = (Res) resourceManager.open(null, preOpen);
                // copy retryResources to this.  retryResources is not used.  We only need to create a new ResourcesState.
                state = retryResources.state;
                Iterator<Replay<? extends Resources, ?>> iterator = unitOfWork.iterator();
                while (iterator.hasNext()) {
                    Object ret = ((Replay<Res, ?>) iterator.next()).replay((Res) this);
                    if (!iterator.hasNext()) {
                        lastResult = ret;
                    }
                }
                return replay.replay((Res) this);
            });
        } catch (Throwable th2) {
            log("Aborting due to exception", th2);
            abort();
            if (th2 instanceof RuntimeException) throw (RuntimeException) th2;
            throw new RuntimeException(th2);
        }
    }

    // return null because we want this type of work to be stateless outside of
    // Resources.
    public synchronized <Res extends Resources> void submit(Transactional.ConsumerWithException<Res> work) {
        play(new Replay<Res, Object>(work));
    }

    public synchronized <Res extends Resources, R> R submit(Transactional.FunctionWithException<Res, R> work) {
        return play(new Replay<Res, R>(work));
    }

    private <Res extends Resources, R> R play(Replay<Res, R> replay) {
        R ret = null;
        boolean topLevel = !submitting;
        submitting = true;
        try {
            try {
                ret = replay.replay((Res) this);
            } catch (Throwable th) {
                ret = replay(th, replay);
            }
            lastResult = ret;
        } finally {
            if (topLevel) { // prevents nested submission to be added
                submitting = false;
                unitOfWork.add(replay);
            }
        }
        return ret;
    }

    private void log(String msg, Throwable th) {
        Provision provision = resourceManager.getInstance(Provision.class);
        provision.log(logger, msg, th);
    }

    public synchronized <R> R commit() {
        R ret = null;
        try {
            ret = _commit();
        } catch (Throwable th) {
            ret = replay(th, new Replay<Resources, R>((res)-> {return _commit();}));
        } finally {
            if (isCommitted()) {
                // commit successful
                cleanup();
                state.setState(ResourcesState.State.Committed);
            }
        }
        return ret;
    }

    private <R> R _commit() {
        R ret = null;
        if (resourceManager == null) return null;
        if (isAborted()) return (R) lastResult;
        if (!isOpen()) throw new IllegalStateException("Already closed");

        // use index because additional ResourceProviders may be added during the loop.
        for (int i = 0; i < state.getResourceProviders().size(); i++) {
            ResourceProvider resourceProvider = state.getResourceProviders().get(i);
            resourceProvider.onCommit(this);
            if (isAborted()) {
                return (R) lastResult;
            }
        }

        for (ResourceProvider p : getExternalResourceProviders()) {
            p.onCommit(this);
        }

        for (int i = 0; i < state.getResourceProviders().size(); i++) {
            ResourceProvider resourceProvider = state.getResourceProviders().get(i);
            try {
                resourceProvider.afterCommit(this);
            } catch (Throwable th) {}
        }

        state.setState(ResourcesState.State.Committed);
        ret = (R) lastResult;

        return ret;
    }

    public synchronized void abort() {
        try {
            if (resourceManager == null) return;

            if (!isAborted()) {
                for (int i = 0; i < state.getResourceProviders().size(); i++) {
                    ResourceProvider resourceProvider = state.getResourceProviders().get(i);
                    try {
                        resourceProvider.onAbort(this);
                    } catch (Throwable th) {
                    }
                }
                for (ResourceProvider p : getExternalResourceProviders()) {
                    try {
                        p.onAbort(this);
                    } catch (Throwable th) {
                    }
                }
            }
        } finally {
            cleanup();
            state.setState(ResourcesState.State.Aborted);
        }
    }

    public void close() throws Exception {
        if (!isOpen()) return;

        if (!isAborted()) {
            commit();
        } else {
            abort();
        }
    }

    synchronized void discard() {
        resourceManager = null;
        state.discard();
    }

    private void cleanup() {
        try {
            for (ResourceProvider resourceProvider : state.getResourceProviders()) {
                resourceProvider.onClosed(this);
            }
            for (ResourceProvider p : getExternalResourceProviders()) {
                p.onClosed(this);
            }
        } catch (Exception ex) {
            // ignore everything.
        }
        state.cleanup();
        getModule().bindInstance(Resources.class, this);
        lastResult = null;
    }

    public <T extends Provision> T provision() {
        return (T) getInstance(Provision.class);
    }

    private static class Replay<Res, R> {

        Transactional.ConsumerWithException<Res> consumer;
        Transactional.FunctionWithException<Res, R> function;

        Replay(Transactional.ConsumerWithException<Res> work) {
            consumer = work;
        }

        Replay(Transactional.FunctionWithException<Res, R> work) {
            function = work;
        }

        R replay(Res res) throws Throwable {
            if (consumer != null) {
                consumer.accept(res);
                return null;
            } else {
                return function.apply(res);
            }
        }
    }

    private static class ClassInjectionInfo {
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
