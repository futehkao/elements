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

import java.lang.reflect.Field;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("unchecked")
public class SingletonClassBuilder<T> extends AbstractBuilder<Class<T>, SingletonClassBuilder<T>>{

    private Class<T> cls;
    private T singleton;

    public SingletonClassBuilder(Interceptor interceptor, Class<T> cls, T singleton) {
        super(interceptor, ctx -> ctx.invoke(singleton));
        this.cls = cls;
        this.singleton = singleton;
    }

    @Override
    public Class<T> build() {
        if (singleton == null)
            throw new IllegalArgumentException("target cannot be null");
        try {
            Class proxyClass = interceptor.singletonClasses.get(cls,
                    () -> interceptor.loadClass(interceptor.newSingletonBuilder(cls).make(), cls, classLoader));
            Field field = proxyClass.getDeclaredField(Interceptor.HANDLER_FIELD);
            field.setAccessible(true);
            Interceptor.InterceptorHandlerWrapper wrapper = new Interceptor.InterceptorHandlerWrapper(interceptor,
                    proxyClass,
                    null,
                    singleton,
                    handler,
                    listener,
                    newObject);
            field.set(null, wrapper);
            return proxyClass;
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }
}
