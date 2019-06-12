/*
Copyright 2015-2019 Futeh Kao

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
package net.e6tech.elements.common.resources;

import java.lang.reflect.Proxy;

/**
 * Created by futeh.
 */
public interface ResourceProvider {

    static ResourceProvider wrap(String description, ResourceProvider resourceProvider) {
        return (ResourceProvider) Proxy.newProxyInstance(resourceProvider.getClass().getClassLoader(), new Class[] { ResourceProvider.class},
        (proxy, method, args) -> {
            if ("getDescription".equals(method.getName()) && (args == null || args.length == 0)) {
                return description;
            } else {
                return method.invoke(resourceProvider, args);
            }
        });
    }

    default void onOpen(Resources resources) {}
    default void afterOpen(Resources resources) {}
    default void onCommit(Resources resources) {}
    default void afterCommit(Resources resources) {}
    default void afterAbort(Resources resources) {}
    default void onAbort(Resources resources) {}
    default void onClosed(Resources resources) {}
    default void onShutdown() {}
    default String getDescription() { return getClass().getName(); }
}
