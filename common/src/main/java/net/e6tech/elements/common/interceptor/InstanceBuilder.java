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

package net.e6tech.elements.common.interceptor;

import net.e6tech.elements.common.util.SystemException;

@SuppressWarnings("unchecked")
public class InstanceBuilder<T> extends AbstractBuilder<T, InstanceBuilder<T>> {
    private Class<T> cls;

    public InstanceBuilder(Interceptor interceptor, Class<T> cls, InterceptorHandler handler) {
        super(interceptor, handler);
        this.cls = cls;
    }

    @Override
    public T build() {
        Class<T> proxyClass = interceptor.createInstanceClass(cls, classLoader);
        T proxyObject = newObject.newObject(proxyClass);
        Interceptor.InterceptorHandlerWrapper wrapper = null;
        try {
            Object target = null;
            if (!cls.isInterface())
                target = newObject.newObject(cls);
            wrapper = new Interceptor.InterceptorHandlerWrapper(interceptor, proxyClass, proxyObject, target, handler, listener, newObject);
        } catch (Exception e) {
            throw new SystemException(e);
        }
        wrapper.targetClass = cls;
        ((Interceptor.HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }

}
