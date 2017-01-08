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
package net.e6tech.elements.common.interceptor;

/*
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;
*/

/**
 * Created by futeh on 1/20/16.
 */
public class InterceptorAssist {
    /*
    Map<Class, Class> proxyClasses = new Hashtable<>(199);

    private static InterceptorAssist instance = new InterceptorAssist();

    public static InterceptorAssist getInstance() {
        return instance;
    }

    private boolean useWriteReplace = false;

    public boolean isUseWriteReplace() {
        return useWriteReplace;
    }

    public void setUseWriteReplace(boolean useWriteReplace) {
        this.useWriteReplace = useWriteReplace;
    }

    public Class createClass(Class cls) {
        Class proxyClass = proxyClasses.get(cls);
        if (proxyClass != null) return proxyClass;
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(cls);
        factory.setUseWriteReplace(false);  // VERY IMPORTANT, or the proxy gets serialized.
        factory.setFilter((m) -> {
            if (m.getName().equals("finalize")) return false;
            return true;
        });
        proxyClass = factory.createClass();
        proxyClasses.put(cls, proxyClass);
        return proxyClass;
    }

    public <T> T newInterceptor(T target, InterceptorHandler handler) {
        Class proxyClass = createClass(target.getClass());
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(target, handler);
        ((ProxyObject) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

    public <T> T newInstance(Class cls, InterceptorHandler handler) {
        Class proxyClass = createClass(cls);
        T proxyObject = newObject(proxyClass);
        InterceptorHandlerWrapper wrapper = new InterceptorHandlerWrapper(null, handler);
        wrapper.targetClass = cls;
        ((ProxyObject) proxyObject).setHandler(wrapper);
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

    public static <T extends InterceptorHandler> T getHandler(Object proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((ProxyObject) proxyObject).getHandler();
        return (T) wrapper.handler;
    }

    public static  <T extends InterceptorHandler> void setHandler(Object proxyObject, T handler) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((ProxyObject) proxyObject).getHandler();
        wrapper.handler = handler;
    }

    public static Class getTargetClass(Object proxyObject) {
        InterceptorHandlerWrapper wrapper = (InterceptorHandlerWrapper) ((ProxyObject) proxyObject).getHandler();
        return wrapper.targetClass;
    }

    private static class InterceptorHandlerWrapper implements MethodHandler {
        InterceptorHandler handler;
        Object target;
        Class targetClass;

        public InterceptorHandlerWrapper(Object target, InterceptorHandler handler) {
            this.handler = handler;
            this.target = target;
            if (target != null) targetClass = target.getClass();
        }

        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
            return handler.invoke(target, thisMethod, args);
        }
    }
    */
}
