/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.interceptor.builder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.util.SystemException;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Builder<T> implements InterceptorHandler {

    private static Interceptor interceptor = new Interceptor();
    protected static Cache<Class, Map<String, String>> propertyNames = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .expireAfterWrite(120 * 60 * 1000L, TimeUnit.MILLISECONDS)
            .initialCapacity(50)
            .build();
    protected static Cache<Class, Map<String, PropertyDescriptor>> descriptors = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .expireAfterWrite(120 * 60 * 1000L, TimeUnit.MILLISECONDS)
            .initialCapacity(50)
            .build();

    private T target;
    private T proxy;
    private Map<String, Builder> children = new HashMap<>();
    private BuiltInTypes builtInTypes = new DefaultBuiltInTypes();

    public static XMLGregorianCalendar buildXMLGregorianCalendar(ZonedDateTime zonedDateTime) {
        try {
            return DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(zonedDateTime.getYear(), zonedDateTime.getMonthValue(), zonedDateTime.getDayOfMonth(),
                            zonedDateTime.getHour(), zonedDateTime.getMinute(), zonedDateTime.getSecond(), zonedDateTime.getNano() / 1000000,
                            zonedDateTime.getOffset().getTotalSeconds() / 60);
        } catch (DatatypeConfigurationException e) {
            throw new SystemException(e);
        }
    }

    public Builder(Class<T> jaxbClass) {
        try {
            target = jaxbClass.getDeclaredConstructor().newInstance();
            proxy = interceptor.newInterceptor(target, this);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public Builder(T target) {
        T t = Interceptor.isProxyObject(target) ? Interceptor.getTarget(target) : target;
        try {
            this.target = t;
            if (t != null)
                proxy = interceptor.newInterceptor(t, this);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public T getTarget() {
        return target;
    }

    protected void setTarget(T target) {
        this.target = target;
        if (target != null)
            proxy = interceptor.newInterceptor(target, this);
    }

    protected T getProxy() {
        return proxy;
    }

    public BuiltInTypes getBuiltInTypes() {
        return builtInTypes;
    }

    public void setBuiltInTypes(BuiltInTypes builtInTypes) {
        this.builtInTypes = builtInTypes;
    }

    public T accept(Consumer<T> consumer) {
        consumer.accept(proxy);
        return target;
    }

    @Override
    public Object invoke(CallFrame frame) throws Throwable {
        String methodName = frame.getMethod().getName();
        String propertyName = getPropertyName(methodName);
        if (propertyName == null) {
            return frame.invoke();
        } else if (frame.getMethod().getParameterCount() == 0
                && (methodName.startsWith("get") || methodName.startsWith("is"))) {
            return get(propertyName, frame);
        } else if (frame.getMethod().getParameterCount() == 1
                && methodName.length() > 3
                && methodName.startsWith("set")
                && Character.isUpperCase(methodName.charAt(3))) {
            set(propertyName, frame);
            return null;
        }
        return frame.invoke();
    }

    protected boolean isBuiltInClass(Class type) {
        return builtInTypes.isBuiltInClass(type);
    }

    protected PropertyDescriptor getDescriptor(String propertyName) {
        if (target != null) {
            Map<String, PropertyDescriptor> descMap = descriptors.getIfPresent(target.getClass());
            if (descMap == null) {
                introspect();
                descMap = descriptors.getIfPresent(target.getClass());
            }
            return descMap.get(propertyName);
        }
        return null;
    }

    protected String getPropertyName(String methodName) {
        if (target != null) {
            Map<String, String> properties = propertyNames.getIfPresent(target.getClass());
            if (properties == null) {
                introspect();
                properties = propertyNames.getIfPresent(target.getClass());
            }
            return properties.get(methodName);
        }
        return null;
    }

    protected void introspect() {
        try {
            Map<String, PropertyDescriptor> descMap = new HashMap<>();
            Map<String, String> properties = new HashMap<>();
            BeanInfo beanInfo = Introspector.getBeanInfo(target.getClass());
            for (PropertyDescriptor desc : beanInfo.getPropertyDescriptors()) {
                if (desc.getName().equals("class")) // skip getClass
                    continue;

                if (desc.getReadMethod() != null)
                    properties.put(desc.getReadMethod().getName(), desc.getName());
                if (desc.getWriteMethod() != null)
                    properties.put(desc.getWriteMethod().getName(), desc.getName());
                descMap.put(desc.getName(), desc);
            }

            descriptors.put(target.getClass(), descMap);
            propertyNames.put(target.getClass(), properties);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected Object get(String propertyName, CallFrame frame) {
        Class returnType = frame.getMethod().getReturnType();
        if (isBuiltInClass(returnType)) {
            return frame.invoke();
        }
        PropertyDescriptor descriptor = getDescriptor(propertyName);
        Method setter = (descriptor == null) ? null : descriptor.getWriteMethod();
        if (setter == null)
            return frame.invoke();

        Builder child = children.computeIfAbsent(propertyName, key -> {
            try {
                Object existing = frame.invoke();
                Builder c;
                if (existing != null) {
                    c = new Builder(existing);
                } else {
                    c = new Builder(returnType);
                    setter.invoke(target, c.target);
                }
                return c;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        });
        return child.proxy;
    }

    @SuppressWarnings("unchecked")
    protected void set(String propertyName, CallFrame frame) {
        Class type = frame.getMethod().getParameterTypes()[0];
        if (isBuiltInClass(type)) {
            frame.invoke();
            return;
        }

        Object value = frame.getArguments()[0];
        if (Interceptor.isProxyObject(value) && Interceptor.getInterceptorHandler(value) instanceof Builder) {
            Builder child = Interceptor.getInterceptorHandler(value);
            children.put(propertyName, child);
            value = child.target;
        } else {
            Builder child = new Builder(value);
            children.put(propertyName, child);
        }

        frame.getArguments()[0] = value;
        frame.invoke();
    }
}

