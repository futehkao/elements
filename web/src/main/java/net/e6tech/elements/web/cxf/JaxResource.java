/*
 * Copyright 2015-2020 Futeh Kao
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

package net.e6tech.elements.web.cxf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.common.reflection.ClassSignature;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.reflection.Signature;
import net.e6tech.elements.common.resources.Configuration;
import net.e6tech.elements.common.serialization.ObjectMapperFactory;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JaxResource {
    private static final ObjectMapper mapper = ObjectMapperFactory.newInstance();
    private static final String CLASS_LOADER = "classLoader";
    private static final String PROTOTYPE = "prototype";

    private Class resourceClass;
    private String resourceClassName;
    private String classLoaderResolver;
    private ClassLoader classLoaderDelegate;
    private boolean singleton;
    private String registerBean;
    private String name;
    private String prototypeResolver;
    private boolean bindHeaderObserver = true;
    private Object prototypeInstance;

    public JaxResource() {
    }

    public JaxResource(Class resourceClass) {
        this.resourceClass = resourceClass;
    }

    public static JaxResource from(Map<String, Object> map) {
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.putAll(map);
            copy.remove(CLASS_LOADER);
            copy.remove(PROTOTYPE);
            String value = mapper.writeValueAsString(copy);
            JaxResource resource = mapper.readValue(value, JaxResource.class);
            if (map.get(CLASS_LOADER) != null) {
                if (map.get(CLASS_LOADER) instanceof String)
                    resource.setClassLoaderResolver(map.get(CLASS_LOADER).toString());
                else
                    resource.setClassLoaderDelegate((ClassLoader) map.get(CLASS_LOADER));
            }
            if (map.get(PROTOTYPE) != null) {
                if (map.get(PROTOTYPE) instanceof String)
                    resource.setPrototypeResolver(map.get(PROTOTYPE).toString());
                else
                    resource.setPrototypeInstance(map.get(PROTOTYPE));
            }
            return resource;
        } catch (JsonProcessingException e) {
            throw new SystemException(e);
        }
    }

    public JaxResource resourceClass(String resourceClass) {
        setResourceClassName(resourceClass);
        return this;
    }

    public JaxResource resourceClass(Class resourceClass) {
        setResourceClass(resourceClass);
        return this;
    }

    public JaxResource classLoader(String classLoaderExpression) {
        setClassLoaderResolver(classLoaderExpression);
        return this;
    }

    public JaxResource classLoader(ClassLoader classLoaderDelegate) {
        setClassLoaderDelegate(classLoaderDelegate);
        return this;
    }

    public JaxResource singleton() {
        setSingleton(true);
        return this;
    }

    public JaxResource singleton(boolean singleton) {
        setSingleton(singleton);
        return this;
    }

    public JaxResource prototype(String prototype) {
        setPrototypeResolver(prototype);
        return this;
    }

    public JaxResource prototypeInstance(Object prototype) {
        setPrototypeInstance(prototype);
        return this;
    }

    public JaxResource bindHeaderObserver(boolean b) {
        setBindHeaderObserver(b);
        return this;
    }

    public JaxResource name(String name) {
        setName(name);
        return this;
    }

    public JaxResource bean(String name) {
        setRegisterBean(name);
        return this;
    }

    public Class getResourceClass() {
        return resourceClass;
    }

    public void setResourceClass(Class resourceClass) {
        this.resourceClass = resourceClass;
    }

    @JsonProperty("class")
    public String getResourceClassName() {
        return resourceClassName;
    }

    @JsonProperty("class")
    public void setResourceClassName(String resourceClassName) {
        this.resourceClassName = resourceClassName;
    }

    public String getClassLoaderResolver() {
        return classLoaderResolver;
    }

    public void setClassLoaderResolver(String classLoaderResolver) {
        this.classLoaderResolver = classLoaderResolver;
    }

    @JsonProperty("classLoader")
    public ClassLoader getClassLoaderDelegate() {
        return classLoaderDelegate;
    }

    @JsonProperty("classLoader")
    public void setClassLoaderDelegate(ClassLoader classLoaderDelegate) {
        this.classLoaderDelegate = classLoaderDelegate;
    }

    public boolean isSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public String getRegisterBean() {
        return registerBean;
    }

    public void setRegisterBean(String registerBean) {
        this.registerBean = registerBean;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrototypeResolver() {
        return prototypeResolver;
    }

    public void setPrototypeResolver(String prototypeResolver) {
        this.prototypeResolver = prototypeResolver;
    }

    @JsonProperty("prototype") // for compatibility when creating from a Map as in JaxResource.from
    public Object getPrototypeInstance() {
        return prototypeInstance;
    }

    @JsonProperty("prototype") // for compatibility when creating from a Map as in JaxResource.from
    public void setPrototypeInstance(Object prototypeInstance) {
        this.prototypeInstance = prototypeInstance;
    }

    public boolean isBindHeaderObserver() {
        return bindHeaderObserver;
    }

    public void setBindHeaderObserver(boolean bindHeaderObserver) {
        this.bindHeaderObserver = bindHeaderObserver;
    }

    public Class resolveResourceClass(ClassLoader externalLoader, Configuration.Resolver resolver) {
        Class cls = getResourceClass();
        if (cls != null) {
            resolveSingleton();
            return cls;
        }

        if (getResourceClassName() == null) {
            Object prototype = getPrototypeInstance();
            if (prototype == null && prototypeResolver != null && resolver != null) {
                prototype = resolver.resolve(prototypeResolver);
            }
            if (prototype != null) {
                setPrototypeInstance(prototype);
                setResourceClass(prototype.getClass());
                resolveSingleton();
                return prototype.getClass();
            } else
                throw new SystemException("Missing resource class in resources map");
        }

        try {
            ClassLoader loader = getClassLoaderDelegate();
            String classLoaderExpression = getClassLoaderResolver();

            // try to load class using resolver
            if (loader == null && classLoaderExpression != null && resolver != null) {
                loader = (ClassLoader) resolver.resolve(classLoaderExpression);
            }

            // load using external loader
            if (loader == null) {
                loader = (externalLoader == null) ? getClass().getClassLoader() : externalLoader;
            }

            cls = loader.loadClass(getResourceClassName());
            setResourceClass(cls);
            resolveSingleton();
        } catch (ClassNotFoundException e) {
            throw new SystemException(e);
        }
        return getResourceClass();
    }

    private void resolveSingleton() {
        if (!singleton) {
            Map<Signature, Map<Class<? extends Annotation>, Annotation>> annotations = Reflection.getAnnotations(resourceClass);
            if (annotations.getOrDefault(new ClassSignature(resourceClass), new HashMap<>()).get(Singleton.class) != null) {
                singleton = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Object resolvePrototype(ClassLoader externalLoader, Configuration.Resolver resolver) {
        Object prototype = getPrototypeInstance();
        if (prototype == null && prototypeResolver != null && resolver != null) {
            prototype = resolver.resolve(prototypeResolver);
        }
        if (prototype != null)
            return prototype;

        if (prototype == null && isSingleton()) {
            if (resourceClass == null)
                resolveResourceClass(externalLoader, resolver);
            try {
                prototype = resourceClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return prototype;
    }
}
