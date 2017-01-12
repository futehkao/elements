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

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import net.e6tech.elements.common.reflection.Reflection;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by futeh.
 */
public class Interceptor {
    private Map<Class, WeakReference<Class>> proxyClasses = Collections.synchronizedMap(new WeakHashMap<>(199));

    private static Interceptor instance = new Interceptor();

    public static Interceptor getInstance() {
        return instance;
    }

    protected Class createClass(Class cls) {
        WeakReference<Class> ref = proxyClasses.get(cls);
        Class proxyClass = (ref == null) ? null : ref.get();
        if (proxyClass == null) {
            proxyClass = new ByteBuddy()
                    .subclass(cls)
                    .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))))
                    .intercept(MethodDelegation.toField("handler"))
                    .defineField("handler", Handler.class, Visibility.PRIVATE)
                    .implement(HandlerAccessor.class).intercept(FieldAccessor.ofBeanProperty())
                    .make()
                    .load(cls.getClassLoader())
                    .getLoaded();
            proxyClasses.put(cls, new WeakReference<Class>(proxyClass));
        }

        return proxyClass;
    }

    /**
     * Creates a prototype class.  When an instance is created, its bean properties are copied from the prototype.
     * Afterward, the instance functions independently from the prototype.
     * @param cls
     * @param prototype
     * @param <T>
     * @return
     */
    public static <T> Class<T> newPrototypeClass(Class<T> cls, T prototype) {
        Class proxyClass = new ByteBuddy()
                .subclass(cls)
                .constructor(ElementMatchers.any())
                    .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(new Constructor<T>(prototype))))
                .make()
                .load(cls.getClassLoader())
                .getLoaded();
        return proxyClass;
    }

    // must be public static
    public static class Constructor<T> {
        T prototype;

        public Constructor(T prototype) {
            this.prototype = prototype;
        }

        public void construct(@This Object instance) {
            if (prototype != null) Reflection.copyInstance(instance, prototype);
        }
    }

    /**
     * Create a class that returns a singleton.  When new instance of the class is created, all operations, except
     * for finalize, are delegated to the singleton.
     *
     * @param cls
     * @param singleton
     * @param <T>
     * @return
     */
    public static <T> Class<T> newSingletonClass(Class<T> cls, T singleton) {
        if (singleton == null) throw new IllegalArgumentException("target cannot be null");
        Class proxyClass = new ByteBuddy()
                .subclass(cls)
                .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.named("finalize").and(ElementMatchers.hasParameters(ElementMatchers.none())))))
                .intercept(MethodDelegation.toField("handler"))
                .defineField("handler", Handler.class, Visibility.PRIVATE, Ownership.STATIC)
                .make()
                .load(cls.getClassLoader())
                .getLoaded();
        try {
            Field field = proxyClass.getDeclaredField("handler");
            field.setAccessible(true);
            InterceptorHandlerWrapper wrapper = null;
            try {
                wrapper = new InterceptorHandlerWrapper(singleton,  (t,  thisMethod,  args)-> {
                    return thisMethod.invoke(singleton, args);
                } );
                field.set(null, wrapper);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return proxyClass;
    }

    /**
     * Creates an interceptor for an instance.  All calls for the interceptor, except for methods declared in Object, are
     * forwarded to the handler.
     *
     * @param instance
     * @param handler
     * @param <T>
     * @return
     */
    public <T> T newInterceptor(T instance, InterceptorHandler handler) {
        Class proxyClass = createClass(instance.getClass());
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(instance, handler);
        ((HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

    /**
     * Create an interceptor just like the newInterceptor method.  However, the instance is set to cls.newInstance().
     * @param cls
     * @param handler
     * @param <T>
     * @return
     */
    public <T> T newInstance(Class cls, InterceptorHandler handler) {
        Class proxyClass = createClass(cls);
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = null;
        try {
            wrapper = new InterceptorHandlerWrapper(cls.newInstance(), handler);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        wrapper.targetClass = cls;
        ((HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

    private <T> T newObject(Class proxyClass) {
        T proxyObject = null;
        try {
            proxyObject = (T) proxyClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return proxyObject;
    }

    public static boolean isProxyObject(Object proxyObject) {
        if (!(proxyObject instanceof HandlerAccessor)) {
            return false;
        }
        return true;
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

    public interface Handler {
        @RuntimeType
        Object handle(@Origin Method interceptorMethod, @AllArguments() Object[] arguments) throws Throwable;
    }

    public interface HandlerAccessor {
        Handler getHandler();
        void setHandler(Handler handler);
    }

    private static class InterceptorHandlerWrapper implements Handler {
        InterceptorHandler handler;
        Object target;
        Class targetClass;

        public InterceptorHandlerWrapper(Object target, InterceptorHandler handler) {
            this.handler = handler;
            this.target = target;
            if (target != null) targetClass = target.getClass();
        }

        public Object handle(Method interceptorMethod, @RuntimeType  Object[] arguments) throws Throwable {
            return handler.invoke(target, interceptorMethod, arguments);
        }
    }
}
