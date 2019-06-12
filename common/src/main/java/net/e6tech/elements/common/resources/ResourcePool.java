/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public interface ResourcePool {

    ResourceManager getResourceManager();

    default <T> T getBean(String name) {
        return getResourceManager().getBean(name);
    }

    default <T> T getBean(Class<T> cls) {
        return getResourceManager().getBean(cls);
    }

    default NotificationCenter getNotificationCenter() {
        return getResourceManager().getNotificationCenter();
    }

    <T> T bind(Class<T> cls, T resource) ;  // 1

    <T> T rebind(Class<T> cls, T resource); // 1

    <T> T unbind(Class<T> cls); //1

    void bindClass(Class cls, Class service);  // 1

    <T> T bindNamedInstance(Class<T> cls, String name, T resources); // 1

    <T> T rebindNamedInstance(Class<T> cls, String name, T resource);

    <T> T inject(T obj) ;

    default <T> T newInstance(Class<T> cls) {
        try {
            T instance = cls.getDeclaredConstructor().newInstance();
            inject(instance);
            return instance;
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new SystemException(e);
        } catch (InvocationTargetException e) {
            throw new SystemException(e.getTargetException());
        }
    }

    /**
     * This method should be implemented by a subclass that is capable of finding an object by id.  It is
     * typically used by database aware resources.
     * @param cls class of the object to be found.
     * @param id primary key
     * @param <T> type of instance
     * @return instance.
     */
    default <T> T findById(Class<T> cls, Object id) {
        return null;
    }

    /**
     * This method is used to map entity found by id into something else.
     * @param cls class of the entity to be mapped
     * @param id primary key
     * @param mapper mapper function to convert entity into desired output
     * @param <T> type of entity
     * @param <U> type of output object
     * @return output
     */
    default <T, U> U mapById(Class<T> cls, Object id, Function<T, U> mapper) {
        return Optional.ofNullable(findById(cls, id)).map(mapper).orElse(null);
    }
}
