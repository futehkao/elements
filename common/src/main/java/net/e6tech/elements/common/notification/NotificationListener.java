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

package net.e6tech.elements.common.notification;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Consumer;

/**
 * Created by futeh on 1/21/16.
 */
public interface NotificationListener<T extends Notification> {

    static <R extends Notification> NotificationListener<R> create(String name, Consumer<R> consumer) {
        return new NotificationListener<R>() {
            @Override
            public void onEvent(R notification) {
                consumer.accept(notification);
            }

            @Override
            public String description() {
                return name;
            }
        };
    }

    static <R extends Notification> NotificationListener<R> create(String name, Class<? extends Notification>[] types,  Consumer<R> consumer) {
        return new NotificationListener<R>() {
            @Override
            public Class<? extends Notification>[] getNotificationTypes() {
                return types;
            }

            @Override
            public void onEvent(R notification) {
                consumer.accept(notification);
            }

            @Override
            public String description() {
                return name;
            }
        };
    }

    default Class<? extends Notification>[] getNotificationTypes() {
        Type[] genericInterfaces = getClass().getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType && ((ParameterizedType) genericInterface).getRawType().equals(NotificationListener.class)) {
                ParameterizedType parametrizedType = (ParameterizedType) genericInterface;
                Type[] typeArguments =  parametrizedType.getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                    return new Class[] { (Class) typeArguments[0] };
                }
            }
        }
        return new Class[0];
    }

    default String description() {
        return getClass().getName();
    }

    void onEvent(T notification);
}
