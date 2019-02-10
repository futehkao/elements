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
import net.e6tech.elements.common.logging.LogLevel;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.ObjectConverter;
import net.e6tech.elements.common.resources.plugin.Plugin;
import net.e6tech.elements.common.resources.plugin.PluginManager;
import net.e6tech.elements.common.resources.plugin.PluginPath;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S1444", "squid:ClassVariableVisibilityCheck"})
public class Provision {

    public static final int JVM_VERSION;
    public static Integer cacheBuilderConcurrencyLevel = 32;

    private ResourceManager resourceManager;

    static {
        String version = System.getProperty("java.version");
        int firstIdx = version.indexOf('.');
        int verNumber = Integer.parseInt(version.substring(0, version.indexOf('.')));
        if (verNumber == 1) {
            int secondIdx = version.indexOf('.', firstIdx + 1);
            verNumber = Integer.parseInt(version.substring(firstIdx + 1, secondIdx));
        }
        JVM_VERSION = verNumber;
    }

    public Provision load(Map<String, Object> map) {
        Class cls = getClass();
        while (Provision.class.isAssignableFrom(cls)) {
            Field[] fields = cls.getDeclaredFields();
            for (Field f : fields) {
                setField(f, map);
            }
            cls = cls.getSuperclass();
        }
        return this;
    }

    private void setField(Field f, Map<String, Object> map) {
        ObjectConverter converter = new ObjectConverter();
        if (Modifier.isPublic(f.getModifiers())
                && !Modifier.isStatic(f.getModifiers())
                && map.get(f.getName()) != null) {
            Object from = map.get(f.getName());
            if (from != null) {
                try {
                    f.setAccessible(true);
                    f.set(this, converter.convert(from, f, null));
                    f.setAccessible(false);
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
        }
    }

    public void log(Logger logger, LogLevel level, String message, Throwable th) {
       logger.log(level, message, th);
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    @Inject
    public void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public <T> T getComponentResource(String componentName, String resourceName) {
        return getResourceManager().getAtomResource(componentName, resourceName);
    }

    public String getName() {
        return resourceManager.getName();
    }

    public <T> T nullableVar(String key) {
        return resourceManager.nullableVar(key);
    }

    public <T> Optional<T> getVariable(String key) {
        return resourceManager.getVariable(key);
    }

    public Properties getProperties() {
        return resourceManager.getProperties();
    }

    public Map<String, List<String>> getKnownEnvironments() {
        return resourceManager.getKnownEnvironments();
    }

    public <T> T getBean(String name) {
        return resourceManager.getBean(name);
    }

    public <T> T getBean(Class<T> cls) {
        return resourceManager.getBean(cls);
    }

    public Map<String, Object> getBeans() {
        return resourceManager.getBeans();
    }

    public <T> Map<String, T> getBeans(Class<T> cls) {
        return resourceManager.getBeans(cls);
    }

    public List listBeans() {
        return resourceManager.listBeans();
    }

    public <T> T getInstance(Class<T> cls) {
        return resourceManager.getInstance(cls);
    }

    public <T> T newInstance(Class<T> cls) {
        return resourceManager.newInstance(cls);
    }

    public <T> Optional<T> findInstance(Class<T> cls) {
        T t = resourceManager.getInstance(cls);
        return Optional.ofNullable(t);
    }

    public <T> T inject(T obj) {
        return resourceManager.inject(obj);
    }

    public Class<? extends Resources> getResourcesClass() {
        return Resources.class;
    }

    public <S, T extends Plugin> Optional<T> getPlugin(Class<S> c1, String n1, Class<T> c2, Object ... args) {
        return getPlugin(PluginPath.of(c1, n1).and(c2), args);
    }

    public <R,S,T extends Plugin> Optional<T> getPlugin(Class<R> c1, String n1, Class<S> c2, String n2, Class<T> c3, Object ... args) {
        return getPlugin(PluginPath.of(c1, n1).and(c2, n2).and(c3), args);
    }

    public <T extends Plugin> Optional<T> getPlugin(PluginPath<T> path, Object ... args) {
        return getInstance(PluginManager.class).get(path, args);
    }

    public UnitOfWork open() {
        UnitOfWork unitOfWork = new UnitOfWork(resourceManager);
        return unitOfWork.preOpen(null);
    }

    // used for configuring resourcesManager's resourceProviders before Resources is open
    public UnitOfWork preOpen(Consumer<Resources> consumer) {
        UnitOfWork unitOfWork = new UnitOfWork(resourceManager);
        return unitOfWork.preOpen(consumer);
    }

    public UnitOfWork onOpen(OnOpen onOpen) {
        UnitOfWork unitOfWork = new UnitOfWork(resourceManager);
        return unitOfWork.onOpen(onOpen);
    }

    public ResourcesFactory resourcesFactory() {
        ResourcesFactory factory = new ResourcesFactory();
        inject(factory);
        return factory;
    }

    public <T extends Annotation> ResourcesBuilder<T> resourceBuilder(Class<T> cls) {
        return new ResourcesBuilder<>(this, cls);
    }
}
