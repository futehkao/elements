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

import net.e6tech.elements.cassandra.annotations.TimeBased;
import net.e6tech.elements.common.reflection.Accessor;

import java.beans.PropertyDescriptor;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

public class AnnotatedTypeDescriptor implements TypeDescriptor {

    private AnnotatedTypeDescriptor parent;
    private AccessibleObject accessibleObject;
    private PropertyDescriptor descriptor;
    private String columnName;
    private Generator generator;

    public AnnotatedTypeDescriptor(Generator generator, Field field) {
        this.generator = generator;
        this.accessibleObject = field;
        columnName = generator.getColumnName(field);
    }

    public AnnotatedTypeDescriptor(Generator generator, PropertyDescriptor descriptor) {
        this.generator = generator;
        this.descriptor = descriptor;
        columnName = generator.getColumnName(descriptor);
    }

    public AnnotatedTypeDescriptor getParent() {
        return parent;
    }

    public void setParent(AnnotatedTypeDescriptor parent) {
        this.parent = parent;
    }

    @Override
    public boolean isFrozen() {
        if (generator.isFrozen(descriptor)
            || generator.isFrozen(accessibleObject))
            return true;
        else if (parent != null) {
            parent.isFrozen();
        }
        return false;
    }

    @Override
    public boolean isFrozenKey() {
        if (generator.isFrozenKey(descriptor)
                || generator.isFrozenKey(accessibleObject))
            return true;
        else if (parent != null) {
            parent.isFrozenKey();
        }
        return false;
    }

    @Override
    public boolean isFrozenValue() {
        if (generator.isFrozenValue(descriptor)
                || generator.isFrozenValue(accessibleObject))
            return true;
        else if (parent != null) {
            parent.isFrozenValue();
        }
        return false;
    }

    @Override
    public boolean isTimeBased() {
        if (Accessor.getAnnotation(descriptor, accessibleObject, TimeBased.class) != null)
            return true;
        else if (parent != null) {
            parent.isTimeBased();
        }
        return false;
    }

    @Override
    public String getColumnName() {
        if (generator.hasColumnAnnotation(descriptor)
            || generator.hasColumnAnnotation(accessibleObject)) {
            return columnName;
        } else if (parent != null &&
                (generator.hasColumnAnnotation(parent.descriptor)
                || generator.hasColumnAnnotation(parent.accessibleObject))) {
            return parent.getColumnName();
        }
        return columnName;
    }
}
