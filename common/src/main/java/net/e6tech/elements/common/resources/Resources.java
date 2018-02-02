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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.logging.LogLevel;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.plugin.Plugin;
import net.e6tech.elements.common.resources.plugin.PluginManager;
import net.e6tech.elements.common.resources.plugin.PluginPath;
import net.e6tech.elements.common.resources.plugin.PluginPaths;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
@SuppressWarnings({"squid:S1141", "squid:S134", "squid:S1602", "squid:S00100", "squid:MethodCyclomaticComplexity"})
public class Resources implements AutoCloseable, ResourcePool {

    private static Logger logger = Logger.getLogger(Resources.class);
    private static final String ABORT_DUE_TO_EXCEPTION = "Aborting due to exception";

    private ResourceManager resourceManager;

    @Inject(optional = true)
    private Retry retry;

    protected ResourcesState state;
    protected Configurator configurator = new Configurator();
    protected Configurator initialConfigurator;
    private Consumer<? extends Resources> preOpen;
    private List<Replay<? extends Resources, ?, ? extends Exception>> replays = new LinkedList<>();
    Object lastResult;
    private Throwable lastException;
    boolean submitting = false;

    protected Resources(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        state = new ResourcesState(this);
        getModule().bindInstance(getClass(), this);
    }

    void setPreOpen(Consumer<? extends Resources> preOpen) {
        this.preOpen = preOpen;
    }

    public synchronized boolean isCommitted() {
        return state.getState() == ResourcesState.State.COMMITTED;
    }

    public synchronized boolean isOpen() {
        return state.getState() == ResourcesState.State.OPEN;
    }

    public synchronized boolean isAborted() {
        return state.getState() == ResourcesState.State.ABORTED;
    }

    public synchronized boolean isClosed() {
        return !isOpen();
    }

    public synchronized boolean isDiscarded() {
        return resourceManager == null;
    }

    List<ResourceProvider> getExternalResourceProviders() {
        return state.getExternalResourceProviders();
    }

    void setExternalResourceProviders(List<ResourceProvider> externalResourceProviders) {
        state.setExternalResourceProviders(externalResourceProviders);
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
        OnCommit on = res -> runnable.run();
        onCommit(on);
    }

    public void afterCommit(AfterCommit afterCommit) {
        addResourceProvider(afterCommit);
    }

    public void afterCommit(Runnable runnable) {
        AfterCommit after = res -> runnable.run();
        afterCommit(after);
    }

    public synchronized void onOpen(OnOpen onOpen) {
        addResourceProvider(onOpen);
    }

    public synchronized void onOpen(Runnable runnable) {
        OnOpen on = res -> runnable.run();
        onOpen(on);
    }

    public synchronized void onAbort(OnAbort onAbort) {
        addResourceProvider(onAbort);
    }

    public synchronized void onAbort(Runnable runnable) {
        OnAbort on = res -> runnable.run();
        onAbort(on);
    }

    public synchronized void onClosed(OnClosed onClosed) {
        addResourceProvider(onClosed);
    }

    public synchronized void onClosed(Runnable runnable) {
        OnClosed on = res -> runnable.run();
        onClosed(on);
    }

    public synchronized boolean remove(ResourceProvider provider) {
        return getResourceProviders().remove(provider);
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    /*
     * Plugins are identified by class, a name and then a particular plugin class.  The argument are injected into the plugin.
     * For example, let say there is a class called Partner and it may be associated with several plugin types.  Furthermore,
     * the plugins associated with a Partner may vary based on the partner.  To create the plugin, one may search based on
     * Partner.class, partner name, plugin class and a list of arguments to be injected into the plugin.
     */
    public <S, T extends Plugin> Optional<T> getPlugin(Class<S> c1, String n1, Class<T> c2, Object ... args) {
        return getPlugin(PluginPath.of(c1, n1).and(c2), args);
    }

    public <R,S,T extends Plugin> Optional<T> getPlugin(Class<R> c1, String n1, Class<S> c2, String n2, Class<T> c3, Object ... args) {
        return getPlugin(PluginPath.of(c1, n1).and(c2, n2).and(c3), args);
    }

    public <T extends Plugin> Optional<T> getPlugin(PluginPath<T> path, Object ... args) {
        PluginManager plugin = getInstance(PluginManager.class);
        return plugin.from(this).get(path, args);
    }

    public <T extends Plugin> Optional<T> getPlugin(PluginPaths<T> paths, Object ... args) {
        PluginManager plugin = getInstance(PluginManager.class);
        return plugin.from(this).get(paths, args);
    }

    public <T> T getVariable(String variable) {
        return resourceManager.getVariable(variable);
    }

    public Module getModule() {
        return state.getModule();
    }

    public void addModule(Module module) {
        state.addModule(module);
    }

    <T> Binding<T> getBinding(Class<T> cls) {
        return new Binding<>(this, cls);
    }

    public <E extends Exception> void briefly(Transactional.ConsumerWithException<Bindings, E> consumer) throws E {
        Bindings bindings = new Bindings(this);
        try {
            consumer.accept(bindings);
        } finally {
            bindings.restore();
        }
    }

    public <T, E extends Exception> T briefly(Transactional.FunctionWithException<Bindings, T, E> function) throws E {
        Bindings bindings = new Bindings(this);
        try {
            return function.apply(bindings);
        } finally {
            bindings.restore();
        }
    }

    public <T> T tryBind(Class<T> cls, Callable<T> callable) {
        return state.tryBind(cls, callable);
    }

    public <T> boolean isBound(Class<T> cls) {
        return (getModule().getBoundInstance(cls) != null) ? true : false;
    }

    public <T> T bind(Class<T> cls, T resource) {
        return state.bind(cls, resource);
    }

    public <T> T rebind(Class<T> cls, T resource) {
        return state.rebind(cls, resource);
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

    public <T> T bindNamedInstance(Class<T> cls, String name, T resource) {
        return state.bindNamedInstance(cls, name, resource);
    }

    public <T> T rebindNamedInstance(Class<T> cls, String name, T resource) {
        return state.rebindNamedInstance(cls, name, resource);
    }

    public <T> T getNamedInstance(Class<T> cls, String name) {
        return getModule().getBoundNamedInstance(cls, name);
    }


    public <T> T inject(T object) {
        return inject(object, new HashSet<>());
    }

    @SuppressWarnings("squid:S3776")
    private <T> T inject(T object, Set<Integer> seen) {
        if (object == null)
            return null;
        // the commented out line indicates that we cannot use seen.contains(object)
        // because it is being injected and its hashCode may not be ready to be computed
        // so that the object should not be added to seen.
        // commented out line -- if seen.contains(System.identityHashCode(object)) && seen.contains(object)
        // as a compromise, we use identifyHashCode
        if (seen.contains(System.identityHashCode(object)))
            return object;  // already been injected.
        T injected = state.inject(this, object);
        seen.add(System.identityHashCode(object));
        // seen.add(object);  object may not be initialized fully to compute hashCode.

        ResourceManager.ClassInjectionInfo info = resourceManager.getInjections().get(object.getClass());

        if (info == null) {
            info = new ResourceManager.ClassInjectionInfo();
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

                BeanInfo beanInfo = Reflection.getBeanInfo(object.getClass());
                for (PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
                    if (prop.getReadMethod() != null) {
                        boolean hasAnnotation = prop.getPropertyType().getAnnotation(Injectable.class) != null;
                        if (!hasAnnotation)
                            hasAnnotation = prop.getReadMethod() != null && prop.getReadMethod().getAnnotation(Injectable.class) != null;
                        if (!hasAnnotation)
                            hasAnnotation = prop.getWriteMethod() != null && prop.getWriteMethod().getAnnotation(Injectable.class) != null;

                        if (hasAnnotation)
                            info.addInjectableProperty(prop);
                    }
                }
            }
            resourceManager.getInjections().put(object.getClass(), info);
        }

        for (Field f : info.getInjectableFields()) {
            try {
                Object injectField = f.get(object);
                if (injectField != null) {
                    inject(injectField, seen);
                }
            } catch (IllegalAccessException e) {
                throw new SystemException(e);
            }
        }

        for (PropertyDescriptor d : info.getInjectableProperties()) {
            try {
                Object injectProp = d.getReadMethod().invoke(object);
                if (injectProp != null) {
                    inject(injectProp, seen);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new SystemException(e);
            }
        }
        return injected;
    }

    public boolean hasInstance(Class cls) {
        return state.hasInstance(this, cls);
    }

    public <T> T getInstance(Class<T> cls) {
        return state.getInstance(this, cls);
    }

    public <T> T getInstance(Class<T> cls, Supplier<T> call) {
        try {
            return state.getInstance(this, cls);
        } catch (InstanceNotFoundException ex) {
            Logger.suppress(ex);
            return call.get();
        }
    }

    public Configurator configurator() {
        return configurator;
    }

    // configurator can be changed by preOpen
    // whereas initialConfigurator records the initial configuration values when Resources is opeen
    // It is then used in replay.
    public void configure(Configurator configurator) {
        this.configurator.putAll(configurator);
        if (this.initialConfigurator == null) {
            this.initialConfigurator = new Configurator();
        }
        this.initialConfigurator.putAll(configurator);
    }

    public synchronized void onOpen() {
        // state.initModules(this); // MUST initialize injector first by calling initModules
        if (!isOpen()) {
            state.setState(ResourcesState.State.OPEN);
            // this loop can produce recursive onOpen call
            try {
                for (ResourceProvider resourceProvider : state.getResourceProviders()) {
                    resourceProvider.onOpen(this);
                }

                state.onOpen(this);

                for (ResourceProvider p : getExternalResourceProviders()) {
                    p.afterOpen(this);
                }

                for (ResourceProvider resourceProvider : state.getResourceProviders()) {
                    resourceProvider.afterOpen(this);
                }

            } catch (Exception ex) {
                abort();
                throw ex;
            }
        }
    }

    protected <T extends Resources, R, E extends Exception> R replay(Exception th, Replay<T, R, E> replay) {
        if (isAborted() || retry == null) {
            log(LogLevel.WARN, ABORT_DUE_TO_EXCEPTION, th);
            if (!isAborted())
                abort();
            if (th instanceof RuntimeException)
                throw (RuntimeException) th;
            throw new SystemException(th);
        }
        try {
            return retry.retry(th, () -> {
                StringBuilder builder = new StringBuilder();
                builder.append("Resources retrying due to error: ")
                        .append(ExceptionMapper.unwrap(th).getClass())
                        .append(", message: ")
                        .append(th.getMessage());
                Reflection.printStackTrace(builder, "    ", 2, 8);
                log(LogLevel.WARN, builder.toString(), null);

                try { abort(); } catch (Exception th2) { Logger.suppress(th2); }

                T retryResources = (T) resourceManager.open(initialConfigurator, preOpen);
                // copy retryResources to this.  retryResources is not used.  We only need to create a new ResourcesState.
                state = retryResources.state;
                Iterator<Replay<? extends Resources, ?, ? extends Exception>> iterator = replays.iterator();
                while (iterator.hasNext()) {
                    Object ret = ((Replay<T, R, E>) iterator.next()).replay((T) this);
                    if (!iterator.hasNext()) {
                        lastResult = ret;
                    }
                }
                return replay.replay((T) this);
            });
        } catch (RuntimeException th2) {
            lastException = th2;
            log(LogLevel.WARN, ABORT_DUE_TO_EXCEPTION, th2);
            abort();
            throw th2;
        } catch (Throwable th2) {
            lastException = th2;
            log(LogLevel.WARN, ABORT_DUE_TO_EXCEPTION, th2);
            abort();
            throw new SystemException(th2);
        }
    }

    // return null because we want this type of work to be stateless outside of
    // Resources.
    public synchronized <R extends Resources, E extends Exception> void submit(Transactional.ConsumerWithException<R, E> work) {
        play(new Replay<R, Object, E>(work));
    }

    public synchronized <T extends Resources, R, E extends Exception> R submit(Transactional.FunctionWithException<T, R, E> work) {
        return play(new Replay<T, R, E>(work));
    }

    public Throwable getLastException() {
        return lastException;
    }

    private <T extends Resources, R, E extends Exception> R play(Replay<T, R, E> replay) {
        R ret = null;
        boolean topLevel = !submitting;
        submitting = true;
        try {
            try {
                ret = replay.replay((T) this);
            } catch (Exception th) {
                lastException = th;
                ret = replay(th, replay);
            }
            lastResult = ret;
        } finally {
            if (topLevel) { // prevents nested submission to be added
                submitting = false;
                // replay can programmatically call abort
                if (!isAborted())
                    replays.add(replay);
            }
        }
        return ret;
    }

    private void log(LogLevel level, String msg, Throwable th) {
        Provision provision = resourceManager.getInstance(Provision.class);
        provision.log(logger, level, msg, th);
    }

    public synchronized <R> R commit() {
        R ret = null;
        try {
            ret = _commit();
        } catch (Exception th) {
            ret = replay(th, new Replay<Resources, R, Exception>(res -> {return _commit();}));
        } finally {
            if (isCommitted()) {
                // commit successful
                cleanup();
                state.setState(ResourcesState.State.COMMITTED);
            }
        }
        return ret;
    }

    private <R> R _commit() {
        R ret = null;
        if (resourceManager == null)
            return null;
        if (isAborted())
            return (R) lastResult;
        if (!isOpen())
            throw new IllegalStateException("Already closed");

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
            } catch (Exception th) {
                Logger.suppress(th);
            }
        }

        state.setState(ResourcesState.State.COMMITTED);
        ret = (R) lastResult;

        return ret;
    }

    public synchronized void abort() {
        try {
            if (resourceManager == null)
                return;

            if (!isAborted()) {
                for (int i = 0; i < state.getResourceProviders().size(); i++) {
                    ResourceProvider resourceProvider = state.getResourceProviders().get(i);
                    try {
                        resourceProvider.onAbort(this);
                    } catch (Exception th) {
                        Logger.suppress(th);
                    }
                }
                for (ResourceProvider p : getExternalResourceProviders()) {
                    try {
                        p.onAbort(this);
                    } catch (Exception th) {
                        Logger.suppress(th);
                    }
                }
            }
        } finally {
            cleanup();
            state.setState(ResourcesState.State.ABORTED);
        }
    }

    public void close() throws Exception {
        if (!isOpen())
            return;

        if (!isAborted()) {
            commit();
        } else {
            abort();
        }
    }

    protected void cleanup() {
        try {
            for (ResourceProvider resourceProvider : state.getResourceProviders()) {
                resourceProvider.onClosed(this);
            }
            for (ResourceProvider p : getExternalResourceProviders()) {
                p.onClosed(this);
            }
        } catch (Exception ex) {
            log(LogLevel.TRACE, ex.getMessage(), ex);
        }
        state.cleanup();
        configurator.clear();
        replays.clear();  // cannot be set to null because during replay abort may be called.
        lastResult = null;
        submitting = false;
        preOpen = null;
    }

    public <T extends Provision> T provision() {
        return (T) getInstance(Provision.class);
    }

    private static class Replay<T, R, E extends Exception> {

        Transactional.ConsumerWithException<T, E> consumer;
        Transactional.FunctionWithException<T, R, E> function;

        Replay(Transactional.ConsumerWithException<T, E> work) {
            consumer = work;
        }

        Replay(Transactional.FunctionWithException<T, R, E> work) {
            function = work;
        }

        R replay(T res) throws E {
            if (consumer != null) {
                consumer.accept(res);
                return null;
            } else {
                return function.apply(res);
            }
        }
    }
}
