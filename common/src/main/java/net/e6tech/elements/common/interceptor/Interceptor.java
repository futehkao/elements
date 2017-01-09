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
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Hashtable;
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

    public <T> T newInterceptor(T instance, InterceptorHandler handler) {
        Class proxyClass = createClass(instance.getClass());
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(instance, handler);
        ((HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

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
