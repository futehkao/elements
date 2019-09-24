/*
 * Copyright 2015-2019 Futeh Kao
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

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.inject.ModuleFactory;
import net.e6tech.elements.common.util.SystemException;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
class ResourcesState {

    private static final List<ResourceProvider> emptyResourceProviders = Collections.unmodifiableList(new ArrayList<>());

    enum State {
        INITIAL,
        OPEN,
        COMMITTED,
        ABORTED,
    }

    private static final String CLASS_MSG = "Class ";
    private static final String BOUND_TO_MSG = " is already bound to ";
    private ModuleFactory factory;
    private Module module;
    private Injector injector;
    private State state = State.INITIAL;
    private List<ResourceProvider> resourceProviders = new LinkedList<>();
    private LinkedList<Object> injectionList = new LinkedList<>();
    private List<ResourceProvider> externalResourceProviders;
    private Map<String, Object> variables;

    ResourcesState(Resources resources) {
        factory = resources.getResourceManager().getModule().getFactory();
        module = factory.create();
    }

    protected void cleanup() {
        module = factory.create();
        resourceProviders.clear();
        state = State.INITIAL;
        injectionList.clear();
        injector = null;
        externalResourceProviders = null;
    }

    public Module getModule() {
        return module;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public List<ResourceProvider> getResourceProviders() {
        return resourceProviders;
    }

    public void setResourceProviders(List<ResourceProvider> resourceProviders) {
        this.resourceProviders = resourceProviders;
    }

    List<ResourceProvider> getExternalResourceProviders() {
        if (externalResourceProviders == null)
            return emptyResourceProviders;
        return externalResourceProviders;
    }

    void setExternalResourceProviders(List<ResourceProvider> externalResourceProviders) {
        this.externalResourceProviders = externalResourceProviders;
    }

    public void addModule(Module module) {
        this.module.add(module);
    }

    protected void onOpen(Resources resources) {
        if (!injectionList.isEmpty()) {
            createInjector(resources);
        }
    }

    protected Injector createInjector(Resources resources) {
        if (injector == null || !injectionList.isEmpty()) {
            injector = (resources.getResourceManager() != null) ?
                    getModule().build(resources.getResourceManager().getModule())
                    : getModule().build();

            // we need to inject here for objects awaiting to be injected because
            // resourceProviders may depend on these objects.
            while (!injectionList.isEmpty()) {
                // need to remove item because it may make resources dirty again calling bind or rebind.  In such a case
                // onOpen will be call again.
                Object obj = injectionList.remove();
                privateInject(resources, injector, obj);
            }
        }
        return injector;
    }

    public <T> T inject(Resources resources, T object) {
        if (object == null)
            return object;

        if (state == State.INITIAL) {
            // to be inject when resources is opened.
            injectionList.add(object);
        } else {
            createInjector(resources);
            privateInject(resources, injector, object);
        }
        return object;
    }

    protected void privateInject(Resources resources, Injector injector, Object object) {
        if (object instanceof InjectionListener) {
            ((InjectionListener) object).preInject(resources);
        }
        injector.inject(object);
        if (object instanceof InjectionListener) {
            ((InjectionListener) object).injected(resources);
        }
    }

    public <T> T tryBind(Class<T> cls, Callable<T> callable) {
        T o = getModule().getBoundInstance(cls);
        if (o != null)
            return o;

        T instance = null;
        try {
            instance = (T) getModule().bindInstance(cls, callable.call());
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return instance;
    }

    public <T> T bind(Class<T> cls, T resource) {
        Object o = getModule().getBoundInstance(cls);
        if (o != null)
            throw new AlreadyBoundException(CLASS_MSG + cls + BOUND_TO_MSG + o);

        return (T) getModule().bindInstance(cls, resource);
    }

    public <T> T rebind(Class<T> cls, T resource) {
        T instance = null;

        try {
            instance = (T) getModule().rebindInstance(cls, resource);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return instance;
    }

    public <T> T unbind(Class<T> cls) {
        T instance = null;
        try {
            instance = (T) getModule().unbindInstance(cls);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return instance;
    }

    public void bindClass(Class cls, Class service) {
        Class c = getModule().getBoundClass(cls);
        if (c != null)
            throw new AlreadyBoundException(CLASS_MSG + cls + BOUND_TO_MSG + c);
        if (service != null)
            getModule().bindClass(cls, service);
        else getModule().bindInstance(cls, null);
    }

    public <T> T bindNamedInstance(Class<T> cls, String name, T resource) {
        Object o = getModule().getBoundNamedInstance(cls, name);
        if (o != null)
            throw new AlreadyBoundException(CLASS_MSG + cls + BOUND_TO_MSG + o);
        return rebindNamedInstance(cls, name, resource);
    }

    public <T> T rebindNamedInstance(Class<T> cls, String name, T resource) {
        return (T) getModule().rebindNamedInstance(cls, name, resource);
    }

    public <T> T getNamedInstance(Resources resources, Class<T> cls, String name) {
        T instance = null;
        if (state == State.INITIAL) {
            if (getModule().getBoundNamedInstance(cls, name) != null)
                instance = getModule().getBoundNamedInstance(cls, name);
            if (resources.getResourceManager().hasInstance(cls))
                instance = resources.getResourceManager().getModule().getBoundNamedInstance(cls, name);
        } else {
            instance = createInjector(resources).getNamedInstance(cls, name);
        }
        if (instance == null) {
            throw new InstanceNotFoundException("No instance for class " + cls.getName() +
                    ". Use newInstance if you meant to create an instance.");
        }
        return instance;
    }

    public boolean hasInstance(Resources resources, Class cls) {
        if (cls.isAssignableFrom(Resources.class) || cls.isAssignableFrom(ResourceManager.class))
            return true;
        return getModule().getBoundInstance(cls) != null || resources.getResourceManager().hasInstance(cls);
    }

    public <T> T getInstance(Resources resources, Class<T> cls) {
        if (cls.isAssignableFrom(Resources.class)) {
            return (T) resources;
        } else if (cls.isAssignableFrom(ResourceManager.class)) {
            return (T) resources.getResourceManager();
        }

        T instance = null;
        if (state == State.INITIAL) {
            if (getModule().getBoundInstance(cls) != null)
                instance = getModule().getBoundInstance(cls);
            if (resources.getResourceManager().hasInstance(cls))
                instance = resources.getResourceManager().getInstance(cls);
        } else {
             instance = createInjector(resources).getInstance(cls);
        }
        if (instance == null) {
            throw new InstanceNotFoundException("No instance for class " + cls.getName() +
                    ". Use newInstance if you meant to create an instance.");
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getVariable(String key) {
        if (variables == null)
            return Optional.empty();
        T t = (T) variables.get(key);
        return Optional.ofNullable(t);
    }

    public void setVariable(String key, Object val) {
        if (variables == null)
            variables = new HashMap<>();
        variables.put(key, val);
    }

    public <T> Map<String, T> computeMapIfAbsent(Class<T> key) {
        if (variables == null)
            variables = new HashMap<>();
        return (Map<String, T>) variables.computeIfAbsent(key.toString(), k -> new LinkedHashMap<>());
    }
}
