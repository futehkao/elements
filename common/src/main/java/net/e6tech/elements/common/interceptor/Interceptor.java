/*
 * Copyright 2016 Futeh Kao
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

package net.e6tech.elements.common.interceptor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by futeh.
 */
public class Interceptor {
    private static final int JVM_VERSION;

    private static final String HANDLER_FIELD = "handler";

    private static Interceptor instance = new Interceptor();

    private static ThreadLocal<Object> anonymousThreadLocal = new ThreadLocal<>();

    private int initialCapacity = 100;

    private Cache<Class, Class> proxyClasses = CacheBuilder.newBuilder()
            .initialCapacity(initialCapacity)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build();

    private Cache<Class, Class> singletonClasses = CacheBuilder.newBuilder()
            .initialCapacity(initialCapacity)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build();

    private Cache<Class, AnonymousDescriptor> anonymousClasses = CacheBuilder.newBuilder()
            .initialCapacity(initialCapacity)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build();

    private Cache<Class, Class> prototypeClasses = CacheBuilder.newBuilder()
            .initialCapacity(initialCapacity)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build();

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

    public int getInitialCapacity() {
        return initialCapacity;
    }

    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public static Interceptor getInstance() {
        return instance;
    }

    private <T> Class<? extends T> loadClass(DynamicType.Unloaded<T> unloaded, Class<T> cls, ClassLoader classLoader) {
        DynamicType.Loaded<T> loaded;
        try {
            if (classLoader != null) {
                loaded = unloaded.load(classLoader);
            } else if (cls.getClassLoader() == null) {
                loaded = unloaded.load(getClass().getClassLoader());
            } else {
                loaded = unloaded.load(cls.getClassLoader());
            }
        } catch (NoClassDefFoundError ex) {
            ClassLoader delegateLoader = cls.getClassLoader();
            if (delegateLoader == null) {
                delegateLoader = ClassLoader.getSystemClassLoader();
            }
            loaded = unloaded.load(new JoinClassLoader(getClass().getClassLoader(), delegateLoader));
        }
        return loaded.getLoaded();
    }

    public <T> Class<T> newPrototypeClass(Class<T> cls, T prototype) {
        return newPrototypeClass(cls, prototype, null);
    }

    /**
     * Creates a prototype class.  When an instance is created, its bean properties are copied from the prototype.
     * Afterward, the instance functions independently from the prototype.
     * @param cls prototype class
     * @param prototype prototype instance
     * @param <T> type of prototype
     * @return byte manipulated prototype class
     */
    @SuppressWarnings("unchecked")
    public <T> Class<T> newPrototypeClass(Class<T> cls, T prototype, ClassLoader classLoader) {
        try {
            return prototypeClasses.get(cls, () -> {
                DynamicType.Unloaded<T> unloaded = new ByteBuddy()
                        .subclass(cls)
                        .constructor(ElementMatchers.any())
                        .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(new PrototypeConstructor<>(prototype))))
                        .make();
                return loadClass(unloaded, cls, classLoader);
            });
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    // must be public static
    public static class PrototypeConstructor<T> {
        T prototype;

        public PrototypeConstructor(T prototype) {
            this.prototype = prototype;
        }

        public void construct(@This Object instance) {
            if (prototype != null)
                Reflection.copyInstance(instance, prototype);
        }
    }

    /*
     * Create a class that returns a singleton.  When new instance of the class is created, all operations, except
     * for finalize, are delegated to the singleton.
     */
    public <T> Class<T> newSingletonClass(Class<T> cls, T singleton) {
        return newSingletonClass(cls, singleton, null, null);
    }

    public <T> Class<T> newSingletonClass(Class<T> cls, T singleton, InterceptorListener listener, ClassLoader classLoader) {
        if (singleton == null)
            throw new IllegalArgumentException("target cannot be null");
        try {
            Class proxyClass = singletonClasses.get(cls,
                    () -> loadClass(newSingletonBuilder(cls).make(), cls, classLoader));
            Field field = proxyClass.getDeclaredField(HANDLER_FIELD);
            field.setAccessible(true);
            InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(this,
                    proxyClass,
                    singleton,
                    ctx -> ctx.invoke(singleton),
                    listener);
            field.set(null, wrapper);
            return proxyClass;
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private <T> DynamicType.Builder<T> newSingletonBuilder(Class<T> cls) {
        return new ByteBuddy()
                .subclass(cls)
                .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.named("finalize").and(ElementMatchers.hasParameters(ElementMatchers.none())))))
                .intercept(MethodDelegation.toField(HANDLER_FIELD))
                .defineField(HANDLER_FIELD, Handler.class, Visibility.PRIVATE, Ownership.STATIC);
    }

    /**
     * Trying to emulate Groovy's with block
     * X x = ...;
     * runAnonymous(x, new X() {{
     *     setName(...);
     *     setInt(...);
     * }}
     *
     * setName and setInt would be called on x.
     */
    public <T> void runAnonymous(T target, T anonymous) {
        Class anonymousClass = anonymous.getClass();
        try {
            AnonymousDescriptor descriptor = anonymousClasses.get(anonymousClass, () -> {
                DynamicType.Builder builder = newSingletonBuilder((Class<T>) anonymousClass);
                Class p;
                if (JVM_VERSION > 8) {
                    p = builder.make()
                            .load(anonymousClass.getClassLoader(), ClassLoadingStrategy.Default.UsingLookup.of(MethodHandles.lookup()))
                            .getLoaded();
                } else {
                    p = builder.make()
                            .load(anonymousClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                            .getLoaded();
                }
                Field field = p.getDeclaredField(HANDLER_FIELD);
                field.setAccessible(true);
                InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(this,
                        p,
                        null,
                        ctx -> { // the use of anonymousThreadLocal is necessary because the wrapper is only created once and cached.
                            Object t = anonymousThreadLocal.get();
                            return ctx.invoke(t);},
                        null);
                field.set(null, wrapper);

                Field[] fields = anonymousClass.getDeclaredFields();
                AnonymousDescriptor desc = new AnonymousDescriptor();
                Field[] copy = new Field[fields.length];
                copy[0] = fields[fields.length - 1];
                System.arraycopy(fields, 0, copy, 1,fields.length - 1);
                desc.fields = copy;
                desc.classes = new Class[copy.length];
                for (int i = 0; i < copy.length; i++)
                    desc.classes[i] = copy[i].getType();
                desc.constructor = p.getDeclaredConstructor(desc.classes);
                return desc;
            });

            anonymousThreadLocal.set(target);
            descriptor.construct(anonymous);
        } catch (Exception e) {
            throw new SystemException(e);
        } finally {
            anonymousThreadLocal.remove();
        }
    }

    private static final class AnonymousDescriptor {
        Field[] fields;
        Class[] classes;
        Constructor constructor;

        final void construct(final Object anonymous) throws IllegalAccessException, InvocationTargetException, InstantiationException {
            Object[] values = new Object[fields.length];
            for (int i = 0; i < values.length; i++)
                values[i] = fields[i].get(anonymous);
            constructor.newInstance(values);
        }
    }

    /**
     * Creates an interceptor for an instance.  All calls for the interceptor, except for methods declared in Object, are
     * forwarded to the handler.
     * @param instance the instance of which calls are to be intercepted
     * @param handler handler of intercepted calls
     * @param <T> type of instance
     * @return an instance of interceptor
     */
    public <T> T newInterceptor(T instance, InterceptorHandler handler) {
        return newInterceptor(instance, handler, null, null);
    }

    public <T> T newInterceptor(T instance, InterceptorHandler handler, ClassLoader classLoader) {
        return newInterceptor(instance, handler, null, classLoader);
    }

    public <T> T newInterceptor(T instance, InterceptorHandler handler, InterceptorListener listener, ClassLoader classLoader) {
        Class proxyClass = createInstanceClass(instance.getClass(), classLoader);
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(this, proxyClass, instance, handler, listener);
        ((HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

    /**
     * Create an interceptor just like the newInterceptor method.  However, the instance is set to cls.newInstance().
     * @param cls instance class
     * @param handler handler
     * @param <T> type of instance
     * @return byte enhanced instance.
     */
    public <T> T newInstance(Class cls, InterceptorHandler handler) {
        return newInstance(cls, handler, null, null);
    }

    public <T> T newInstance(Class cls, InterceptorHandler handler, InterceptorListener listener) {
        return newInstance(cls, handler, listener, null);
    }

    public <T> T newInstance(Class cls, InterceptorHandler handler, InterceptorListener listener, ClassLoader classLoader) {
        Class proxyClass = createInstanceClass(cls, classLoader);
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = null;
        try {
            Object target = null;
            if (!cls.isInterface())
                target = cls.getDeclaredConstructor().newInstance();
            wrapper = new InterceptorHandlerWrapper(this, proxyClass, target, handler, listener);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        wrapper.targetClass = cls;
        ((HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

    private Class createInstanceClass(Class cls, ClassLoader classLoader) {
        try {
            return proxyClasses.get(cls, () -> {
                DynamicType.Unloaded unloaded =
                new ByteBuddy()
                        .subclass(cls)
                        .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))))
                        .intercept(MethodDelegation.toField(HANDLER_FIELD))
                        .defineField(HANDLER_FIELD, Handler.class, Visibility.PRIVATE)
                        .implement(HandlerAccessor.class).intercept(FieldAccessor.ofBeanProperty())
                        .make();
                return loadClass(unloaded, cls, classLoader);
            });
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    private <T> T newObject(Class proxyClass) {
        T proxyObject;
        try {
            proxyObject = (T) proxyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return proxyObject;
    }

    public static boolean isProxyObject(Object proxyObject) {
        return proxyObject instanceof HandlerAccessor;
    }

    public static <T> T cloneProxyObject(T proxyObject) {
        if (!(proxyObject instanceof HandlerAccessor)) {
            throw new IllegalArgumentException("argument is not a proxy object");
        }
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        wrapper = new InterceptorHandlerWrapper(wrapper); // make a copy
        Interceptor interceptor = wrapper.interceptor;
        T cloneProxy = interceptor.newObject(wrapper.proxyClass);
        ((HandlerAccessor) cloneProxy).setHandler(wrapper);
        return cloneProxy;
    }

    public static <T> T getTarget(T proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        return (T) wrapper.target;
    }

    public static <T> void setTarget(T proxyObject, T target) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        if (target != null && !target.getClass().isAssignableFrom(wrapper.targetClass)) {
            throw new IllegalArgumentException("Target class " + target.getClass() + " is not assignable from " + wrapper.targetClass);
        }
        wrapper.target = target;
    }

    public static Class getTargetClass(Object proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        return wrapper.targetClass;
    }

    public static <T extends InterceptorHandler> T getInterceptorHandler(Object proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        return (T) wrapper.handler;
    }

    public static  <T extends InterceptorHandler> void setInterceptorHandler(Object proxyObject, T handler) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        wrapper.handler = handler;
    }

    public static <T extends InterceptorListener> T getInterceptorListener(Object proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        return (T) wrapper.listener;
    }

    public static  <T extends InterceptorListener> void setInterceptorListener(Object proxyObject, T handler) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        wrapper.listener = handler;
    }

    @SuppressWarnings("squid:S00112")
    @FunctionalInterface
    public interface Handler {
        @RuntimeType
        Object handle(@Origin MethodHandle methodHandler, @Origin Method method, @AllArguments() Object[] arguments) throws Throwable;
    }

    /*
     * Implemented by an interceptor class so that handler can be set.
     */
    public interface HandlerAccessor {
        Handler getHandler();
        void setHandler(Handler handler);
    }

    private static class InterceptorHandlerWrapper implements Handler {
        InterceptorHandler handler;
        InterceptorListener listener;
        Object target;
        Class targetClass;
        Class proxyClass;
        Interceptor interceptor;

        InterceptorHandlerWrapper(Interceptor interceptor,
                                         Class proxyClass,
                                         Object target,
                                         InterceptorHandler handler,
                                         InterceptorListener listener) {
            this.interceptor = interceptor;
            this.proxyClass = proxyClass;
            this.handler = handler;
            this.listener = listener;
            this.target = target;
            if (target != null)
                targetClass = target.getClass();
        }

        InterceptorHandlerWrapper(InterceptorHandlerWrapper copy) {
            this.interceptor = copy.interceptor;
            this.proxyClass = copy.proxyClass;
            this.handler = copy.handler;
            this.listener = copy.listener;
            this.target = copy.target;
            this.targetClass = copy.targetClass;
        }

        public Object handle(MethodHandle methodHandle, Method method, @RuntimeType  Object[] arguments) throws Throwable {
            CallFrame frame = new CallFrame(target, methodHandle, method, arguments);
            if (listener != null)
                listener.preInvocation(frame);
            Object ret = null;
            try {
                ret = handler.invoke(frame);
            } catch (Throwable throwable) {
                if (listener != null)
                    return listener.onException(frame, throwable);
                else throw throwable;
            }
            if (listener != null)
                ret = listener.postInvocation(frame, ret);
            return ret;
        }
    }
}
