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
public abstract class AbstractBuilder<T, B extends AbstractBuilder> {

    static final NewObject defaultNewObject = proxyClass -> {
            Object proxyObject;
            try {
                proxyObject = proxyClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SystemException(e);
            }
            return proxyObject;
        };

    Interceptor interceptor;
    InterceptorHandler handler;
    InterceptorListener listener;
    ClassLoader classLoader;
    NewObject<T> newObject = defaultNewObject;

    public AbstractBuilder(Interceptor interceptor, InterceptorHandler handler) {
        this.interceptor = interceptor;
        this.handler = handler;
    }

    public B handler(InterceptorHandler handler) {
        this.handler = handler;
        return (B) this;
    }

    public B listener(InterceptorListener listener) {
        this.listener = listener;
        return (B) this;
    }

    public B classLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return (B) this;
    }

    public B newObject(NewObject<T> newObject) {
        this.newObject = newObject;
        return (B) this;
    }

    public <U> U newObject(Class<U> proxyClass) {
        U proxyObject;
        try {
            proxyObject = proxyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
        return proxyObject;
    }

    public abstract T build();
}
