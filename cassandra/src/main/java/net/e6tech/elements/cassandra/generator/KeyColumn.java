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

package net.e6tech.elements.cassandra.generator;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;

public class KeyColumn {
    private int position;
    private String name;
    private Field field;
    private PropertyDescriptor propertyDescriptor;

    public KeyColumn(String name, int position, PropertyDescriptor descriptor, Field field) {
        this.name = name;
        this.position = position;
        this.propertyDescriptor = descriptor;
        this.field = field;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public Field getField() {
        return field;
    }

    public PropertyDescriptor getPropertyDescriptor() {
        return propertyDescriptor;
    }

    public Class<?> getType() {
        if (propertyDescriptor != null)
            return propertyDescriptor.getPropertyType();

        return field.getType();
    }
}
