/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.common.reflection;

import net.e6tech.elements.common.logging.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * The usage pattern is createProxy().annotate()
 *
 * This initial createProxy configures a handler associated with a cls and a list of
 * methods that the handler wishes to intercept.  Subsequent annotate calls can be
 * made to add more classes and handlers.
 *
 * The main purpose of this class is to intercept certain methods from a class and calls
 * on return values.  For example, take EntityManager from JPA, this class can be used
 * to intercept calls on EntityManager and calls on Query.  To be more concrete, a call
 * on EntityManager's createQuery produces a Query instance.  This class can be configured to
 * intercept calls on such instances.
 *
 * Created by futeh.
 */
public class MultiProxy {

    Map<Class, Map<String, InvocationHandler>> handlers = new HashMap<>();

    private MultiProxy() {}

    /**
     * Create a MultiProxy object to annotate InvocationHandlers.
     *
     * @param cls Root class to be proxied
     * @param handler an InvocationHandler to handle method calls
     * @param methods names of methods to be intercepted
     * @return a configured MultiProxy
     */
    public static MultiProxy createProxy(Class cls, InvocationHandler handler, String... methods) {
        MultiProxy proxy = new MultiProxy();
        return proxy.configure(cls, handler, methods);
    }

    public MultiProxy configure(Class cls, InvocationHandler handler, String... methods) {
        Map<String, InvocationHandler> methodMap = handlers.computeIfAbsent(cls, key -> new HashMap<>());
        if (methods != null) {
            for (String method : methods)
                methodMap.put(method, handler);
        } else {
            if (cls.isInterface()) {
                for (Method method : cls.getMethods())
                    methodMap.put(method.getName(), handler);
            }
        }
        return this;
    }

    public <T> T createInstance(Object target) {
        Class[] interfaces = target.getClass().getInterfaces();
        boolean found = false;
        for (Class intf : interfaces) {
            if (handlers.get(intf) != null) {
                found = true;
                break;
            }
        }
        if (!found && handlers.get(target.getClass()) == null) {
            throw new IllegalArgumentException("Target clas " + target.getClass() + " has no configuration entries");
        }

        MultiInvocationHandler delegateHandler  = new MultiInvocationHandler();
        delegateHandler.target = target;
        return (T) Proxy.newProxyInstance(target.getClass().getClassLoader(), interfaces, delegateHandler);
    }

    private class MultiInvocationHandler implements InvocationHandler {

        private Object target;

        Object getTarget() {
            return target;
        }

        @Override
        @SuppressWarnings({"squid:S134", "squid:MethodCyclomaticComplexity"})
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object ret = null;

            Class[] interfaces = target.getClass().getInterfaces();
            InvocationHandler handler = null;

            for (Class intf : interfaces) {
                if (intf.getMethod(method.getName(), method.getParameterTypes()) != null) {
                    Map<String, InvocationHandler> methods = handlers.get(intf);
                    if (methods != null) {
                        handler = methods.get(method.getName());
                        if (handler != null)
                            break;
                    }
                }
            }
            if (handler == null) {
                Map<String, InvocationHandler> methods = handlers.get(target.getClass());
                if (methods != null) {
                    handler = methods.get(method.getName());
                }
            }

            if (handler != null)
                ret = handler.invoke(target, method, args);
            else {
                try {
                    ret = method.invoke(target, args);
                } catch (InvocationTargetException ex) {
                    Logger.suppress(ex);
                    throw ex.getCause();
                }
            }

            if (ret != null) {
                interfaces = ret.getClass().getInterfaces();
                if (interfaces != null && interfaces.length > 0) {
                    boolean found = false;
                    for (Class intf : interfaces) {
                        if (handlers.get(intf) != null) {
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        MultiInvocationHandler delegateHandler  = new MultiInvocationHandler();
                        delegateHandler.target = ret;
                        ret = Proxy.newProxyInstance(ret.getClass().getClassLoader(), ret.getClass().getInterfaces(), delegateHandler);
                    }
                }
            }
            return ret;
        }
    }

}
