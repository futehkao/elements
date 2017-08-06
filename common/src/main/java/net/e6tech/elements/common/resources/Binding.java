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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.logging.Logger;

/**
 * Created by futeh.
 */
public class Binding<T> {
    private Resources resources;
    private String name; // if it's a named binding
    private boolean oldValueExists;
    private Class<T> boundClass;
    private T oldValue;
    private T currentValue;

    public Binding(Class<T> boundClass, T value) {
        this.boundClass = boundClass;
        this.currentValue = value;
        this.oldValue = value;
    }

    public Binding(String name, Class<T> boundClass, T value) {
        this.name = name;
        this.boundClass = boundClass;
        this.currentValue = value;
        this.oldValue = value;
    }

    public Binding(Resources resources, Class<T> boundClass) {
        this.resources = resources;
        this.boundClass = boundClass;
        try {
            oldValue = resources.getInstance(boundClass);
            oldValueExists = true;
        } catch (InstanceNotFoundException ex) {
            Logger.suppress(ex);
            oldValueExists = false;
        }
        currentValue = oldValue;
    }

    public boolean isNotNull() {
        return currentValue != null;
    }

    public T get() {
        return currentValue;
    }

    public T rebind(T newValue) {
        currentValue = resources.rebind(boundClass, newValue);
        return currentValue;
    }

    public T restore() {
        if (oldValueExists) {
            return resources.rebind(boundClass, oldValue);
        } else {
            return resources.unbind(boundClass);
        }
    }

    public String getName() {
        return name;
    }

    public Class getBoundClass() {
        return boundClass;
    }
}
