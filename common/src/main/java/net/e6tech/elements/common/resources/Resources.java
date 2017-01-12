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
import net.e6tech.elements.common.resources.plugin.Path;
import net.e6tech.elements.common.resources.plugin.Pluggable;
import net.e6tech.elements.common.resources.plugin.Plugin;
import net.e6tech.elements.common.util.ExceptionMapper;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A Resources instance is use to manage resource level injection and resources.
 * Rules for injection.  Only configure injection for instances that are configured
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

    private static final String TIMEOUT =  Resources.class.getName() + ".timeout";
    private static final String TIMEOUT_EXTENSION = Resources.class.getName() + ".timeout.extension";

    private static Logger logger = Logger.getLogger(Resources.class);

    @Inject
    private ResourceManager resourceManager;

    @Inject(optional = true)
    private Retry retry;

    protected ResourcesState state = new ResourcesState();
    private Consumer<? extends Resources> preOpen;
    private List<Replay<? extends Resources, ?>> unitOfWork = new LinkedList<>();
    Object lastResult;
    boolean submitting = false;

    protected Resources() {
        getModule().bindInstance(getClass(), this);
    }

    public long getTimeout() {
        return getConfiguration(TIMEOUT, 0L);
    }

    public void setTimeout(long timeout) {
        setConfiguration(TIMEOUT, timeout);
    }

    public long getTimeoutExtension() {
        return getConfiguration(TIMEOUT_EXTENSION, 0L);
    }

    public void setTimeoutExtension(long timeout) {
        setConfiguration(TIMEOUT_EXTENSION, timeout);
    }

    void setPreOpen(Consumer<? extends Resources> preOpen) {
        this.preOpen = preOpen;
    }

    public synchronized boolean isCommitted() {
        return state.isCommitted();
    }

    private void setCommitted(boolean committed) {
        state.setCommitted(committed);
    }

    public synchronized boolean isOpened() {
        return state.isOpened();
    }

    private void setOpened(boolean opened) {
        state.setOpened(opened);
    }

    public synchronized boolean isAborted() {
        return state.isAborted();
    }

    private void setAborted(boolean aborted) {
        state.setAborted(aborted);
    }

    public synchronized boolean isClosed() {
        return !isOpened();
    }

    public synchronized boolean isDiscarded() {
        return resourceManager == null;
    }

    private List<ResourceProvider> getResourceProviders() {
        return state.getResourceProviders();
    }

    public synchronized void addResourceProvider(ResourceProvider resourceProvider) {
        getResourceProviders().add(resourceProvider);
        if (isOpened() && !isAborted() && !isCommitted()) {
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

    public <T extends Pluggable> T getPlugin(Class c1, String n1, Class c2, Object ... args) {
        return (T) getPlugin(Path.of(c1, n1).and(c2), args);
    }

    public <T extends Pluggable> T getPlugin(Class c1, String n1, Class c2, String n2, Class c3, Object ... args) {
        return (T) getPlugin(Path.of(c1, n1).and(c2, n2).and(c3), args);
    }

    public <T extends Pluggable> T getPlugin(Path<T> path, Object ... args) {
        Plugin plugin = getInstance(Plugin.class);
        return (T) plugin.from(this).get(path, args);
    }

    private Map<String, Object> getContext() {
        return state.getContext();
    }

    // saving info with resources.  This should simple objects that
    // do not required injection of wiring
    public <T> Resources put(Class<T> cls, T obj) {
        getContext().put(cls.getName(), obj);
        return this;
    }

    public <T> Resources put(String name, T obj) {
        if (obj == null) return this;
        getContext().put(name, obj);
        return this;
    }

    public <T> Resources put(Enum value) {
        if (value == null) return this;
        getContext().put(value.getClass() + "::" + value.name(), value);
        return this;
    }

    public <T> T computeIfAbsent(String key, Function<String, T> mappingFunction) {
        return (T) getContext().computeIfAbsent(key, mappingFunction);
    }

    public <T> T computeIfAbsent(Class<T> key, Function<String, T> mappingFunction) {
        return (T) getContext().computeIfAbsent(key.getName(), mappingFunction);
    }

    public <T> T getVariable(String variable) {
        return resourceManager.getVariable(variable);
    }

    public <T> T get(Class<T> cls) {
        return (T) getContext().get(cls.getName());
    }

    public <T> T get(String name) {
        return (T) getContext().get(name);
    }

    public <T> T get(Enum value) {
        return (T) getContext().get(value.getClass() + "::" + value.name());
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
        return state.inject(this, object);
    }

    public boolean hasInstance(Class cls) {
        return state.hasInstance(this, cls);
    }

    public <T> T getInstance(Class<T> cls) throws InstanceNotFoundException {
        return state.getInstance(this, cls);
    }

    protected Map<String, Object> getConfiguration() {
        return state.getConfiguration();
    }

    public void setConfiguration(Map<String, Object> configuration) {
        state.setConfiguration(configuration);
    }

    public <T> T getConfiguration(String key) {
        return (T) getConfiguration().get(key);
    }

    public <T> T getConfiguration(String key, T defaultValue) {
        T value =  (T) getConfiguration().get(key);
        if (value == null) return defaultValue;
        return value;
    }

    public void setConfiguration(String key, Object object) {
        getConfiguration().put(key, object);
    }

    public synchronized void onOpen() {
        setAborted(false);
        setCommitted(false);

        long start = System.currentTimeMillis();
        state.initModules(this);

        if (!isOpened()) {
            setOpened(true);
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

                Res retryResources = (Res) resourceManager.open(preOpen);
                // copy retryResources to this
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
            if (!isAborted()) {
                cleanup();
            }
        }
        return ret;
    }

    private <R> R _commit() {
        R ret = null;
        if (resourceManager == null) return null;
        if (isAborted()) return (R) lastResult;
        if (!isOpened()) throw new IllegalStateException("Already closed");

        // use index because additional ResourceProviders may be added during the loop.
        for (int i = 0; i < state.getResourceProviders().size(); i++) {
            ResourceProvider resourceProvider = state.getResourceProviders().get(i);
            resourceProvider.onCommit(this);
            if (isAborted()) {
                return (R) lastResult;
            }
        }

        for (ResourceProvider p : resourceManager.getResourceProviders()) {
            p.onCommit(this);
        }

        for (int i = 0; i < state.getResourceProviders().size(); i++) {
            ResourceProvider resourceProvider = state.getResourceProviders().get(i);
            try {
                resourceProvider.afterCommit(this);
            } catch (Throwable th) {}
        }

        setCommitted(true);
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
                for (ResourceProvider p : resourceManager.getResourceProviders()) {
                    try {
                        p.onAbort(this);
                    } catch (Throwable th) {
                    }
                }
            }
        } finally {
            cleanup();
            setAborted(true);
        }
    }

    public void close() throws Exception {
        if (!isOpened()) return;

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
            for (ResourceProvider p : resourceManager.getResourceProviders()) {
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
}