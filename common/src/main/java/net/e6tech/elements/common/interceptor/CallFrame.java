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

package net.e6tech.elements.common.interceptor;

import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class CallFrame {

    private static final Object[] EMPTY_ARGS = new Object[0];

    private Object target;
    private MethodHandle handle;
    private Object[] arguments;
    private Method method;
    private Invoke invoke;

    CallFrame() {
    }

    CallFrame initialize(Object target, MethodHandle handle, Method method, Object[] arguments) {
        this.target = target;
        this.handle = handle;
        this.method = method;
        this.arguments = (arguments == null) ? EMPTY_ARGS : arguments;

        if (Modifier.isPublic(getMethod().getModifiers())) {
            invoke = t -> handle.bindTo(t).invokeWithArguments(arguments);
        } else {
            invoke = t -> getMethod().invoke(t, arguments);
        }
        return this;
    }

    void clear() {
        this.target = null;
        this.handle = null;
        this.method = null;
        this.arguments = EMPTY_ARGS;
        invoke = null;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Object getTarget() {
        return target;
    }

    public Method getMethod() {
        return method;
    }

    public MethodHandle getMethodHandle() {
        return handle;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getMethod().getAnnotation(annotationClass);
    }

    public Object invoke() {
        return invoke(target);
    }

    public Object invoke(Object anotherTarget) {
        try {
            return invoke.apply(anotherTarget);
        } catch (InvocationTargetException th) {
            throw new SystemException(th.getTargetException());
        } catch (Throwable th) {
            throw new SystemException(th);
        }
    }

    @SuppressWarnings("squid:S00112")
    @FunctionalInterface
    interface Invoke {
        Object apply(Object t) throws Throwable;
    }
}
