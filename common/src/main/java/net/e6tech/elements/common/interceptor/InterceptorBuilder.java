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

@SuppressWarnings("unchecked")
public class InterceptorBuilder<T> extends AbstractBuilder<T, InterceptorBuilder<T>>{
    private T instance;

    public InterceptorBuilder(Interceptor interceptor, T instance, InterceptorHandler handler) {
        super(interceptor, handler);
        this.instance = instance;
    }

    @Override
    public T build() {
        Class<T> proxyClass = interceptor.createInstanceClass(instance.getClass(), classLoader);
        T proxyObject = newObject.newObject(proxyClass);
        Interceptor.InterceptorHandlerWrapper wrapper =
                new Interceptor.InterceptorHandlerWrapper(interceptor, proxyClass, proxyObject, instance, handler, listener, newObject);
        ((Interceptor.HandlerAccessor) proxyObject).setHandler(wrapper);
        return proxyObject;
    }
}
