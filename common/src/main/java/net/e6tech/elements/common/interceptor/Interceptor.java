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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

/**
 * Created by futeh.
 */
public class Interceptor {
    private LoadingCache<Class, Class> proxyClasses = CacheBuilder.newBuilder()
            .initialCapacity(100)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Class, Class>() {
        public Class load(Class cls) {
            return new ByteBuddy()
                    .subclass(cls)
                    .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))))
                    .intercept(MethodDelegation.toField(HANDLER_FIELD))
                    .defineField(HANDLER_FIELD, Handler.class, Visibility.PRIVATE)
                    .implement(HandlerAccessor.class).intercept(FieldAccessor.ofBeanProperty())
                    .make()
                    .load(cls.getClassLoader())
                    .getLoaded();
        }
    });
    private static final String HANDLER_FIELD = "handler";

    private static Interceptor instance = new Interceptor();

    public static Interceptor getInstance() {
        return instance;
    }

    /**
     * Creates a prototype class.  When an instance is created, its bean properties are copied from the prototype.
     * Afterward, the instance functions independently from the prototype.
     * @param cls prototype class
     * @param prototype prototype instance
     * @param <T> type of prototype
     * @return byte manipulated prototype class
     */
    public static <T> Class<T> newPrototypeClass(Class<T> cls, T prototype) {
        return (Class) new ByteBuddy()
                .subclass(cls)
                .constructor(ElementMatchers.any())
                    .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(new Constructor<T>(prototype))))
                .make()
                .load(cls.getClassLoader())
                .getLoaded();
    }

    // must be public static
    public static class Constructor<T> {
        T prototype;

        public Constructor(T prototype) {
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
    public static <T> Class<T> newSingletonClass(Class<T> cls, T singleton) {
        return newSingletonClass(cls, singleton, null);
    }

    public static <T> Class<T> newSingletonClass(Class<T> cls, T singleton, InterceptorListener listener) {
        if (singleton == null)
            throw new IllegalArgumentException("target cannot be null");
        Class proxyClass = new ByteBuddy()
                .subclass(cls)
                .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.named("finalize").and(ElementMatchers.hasParameters(ElementMatchers.none())))))
                .intercept(MethodDelegation.toField(HANDLER_FIELD))
                .defineField(HANDLER_FIELD, Handler.class, Visibility.PRIVATE, Ownership.STATIC)
                .make()
                .load(cls.getClassLoader())
                .getLoaded();
        try {
            Field field = proxyClass.getDeclaredField(HANDLER_FIELD);
            field.setAccessible(true);
            InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(getInstance(),
                        proxyClass,
                        singleton,
                        (t,  thisMethod,  args) -> thisMethod.invoke(singleton, args),
                        listener );
            field.set(null, wrapper);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return proxyClass;
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
        return newInterceptor(instance, handler, null);
    }

    public <T> T newInterceptor(T instance, InterceptorHandler handler, InterceptorListener listener) {
        Class proxyClass = createClass(instance.getClass());
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
        return newInstance(cls, handler, null);
    }

    public <T> T newInstance(Class cls, InterceptorHandler handler, InterceptorListener listener) {
        Class proxyClass = createClass(cls);
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = null;
        try {
            Object target = null;
            if (!cls.isInterface())
                target = cls.newInstance();
            wrapper = new InterceptorHandlerWrapper(this, proxyClass, target, handler, listener);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        wrapper.targetClass = cls;
        ((HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

    protected Class createClass(Class cls) {
        try {
            return proxyClasses.get(cls);
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    private <T> T newObject(Class proxyClass) {
        T proxyObject = null;
        try {
            proxyObject = (T) proxyClass.newInstance();
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

    public static Object getTarget(Object proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((HandlerAccessor) proxyObject).getHandler();
        return wrapper.target;
    }

    public static void setTarget(Object proxyObject, Object target) {
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
        Object handle(@Origin Method interceptorMethod, @AllArguments() Object[] arguments) throws Throwable;
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

        public InterceptorHandlerWrapper(Interceptor interceptor,
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

        public InterceptorHandlerWrapper(InterceptorHandlerWrapper copy) {
            this.interceptor = copy.interceptor;
            this.proxyClass = copy.proxyClass;
            this.handler = copy.handler;
            this.listener = copy.listener;
            this.target = copy.target;
            this.targetClass = copy.targetClass;
        }

        public Object handle(Method interceptorMethod, @RuntimeType  Object[] arguments) throws Throwable {
            if (listener != null)
                listener.preInvocation(target, interceptorMethod, arguments);
            Object ret = null;
            try {
                ret = handler.invoke(target, interceptorMethod, arguments);
            } catch (Throwable throwable) {
                if (listener != null)
                    return listener.onException(target, interceptorMethod, arguments, throwable);
                else throw throwable;
            }
            if (listener != null)
                ret = listener.postInvocation(target, interceptorMethod, arguments, ret);
            return ret;
        }
    }
}
