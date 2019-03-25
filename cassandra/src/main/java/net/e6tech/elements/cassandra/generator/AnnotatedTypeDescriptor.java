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

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.Frozen;
import com.datastax.driver.mapping.annotations.FrozenKey;
import com.datastax.driver.mapping.annotations.FrozenValue;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AnnotatedTypeDescriptor implements TypeDescriptor {

    private AnnotatedTypeDescriptor parent;
    private AccessibleObject annotated;
    private String columnName;

    public AnnotatedTypeDescriptor(Generator generator, Field field) {
        this.annotated = field;
        columnName = generator.getColumnName(annotated.getAnnotation(Column.class), field);
    }

    public AnnotatedTypeDescriptor(Generator generator, Method method, AnnotatedTypeDescriptor parent) {
        this.annotated = method;
        columnName = generator.getColumnName(annotated.getAnnotation(Column.class), method);
        this.parent = parent;
    }

    @Override
    public boolean isFrozen() {
        if (annotated.getAnnotation(Frozen.class) != null) {
            return annotated.getAnnotation(Frozen.class) != null;
        } else if (parent != null) {
            return parent.isFrozen();
        }
        return false;
    }

    @Override
    public boolean isFrozenKey() {
        if (annotated.getAnnotation(FrozenKey.class) != null) {
            return annotated.getAnnotation(FrozenKey.class) != null;
        } else if (parent != null) {
            return parent.isFrozenKey();
        }
        return false;
    }

    @Override
    public boolean isFrozenValue() {
        if (annotated.getAnnotation(FrozenValue.class) != null) {
            return annotated.getAnnotation(FrozenValue.class) != null;
        } else if (parent != null) {
            return parent.isFrozenValue();
        }
        return false;
    }

    @Override
    public boolean isTimeBased() {
        if (annotated.getAnnotation(TimeBased.class) != null) {
            return annotated.getAnnotation(TimeBased.class) != null;
        } else if (parent != null) {
            return parent.isTimeBased();
        }
        return false;
    }

    @Override
    public String getColumnName() {
        if (annotated.getAnnotation(Column.class) != null) {
            return columnName;
        } else if (parent != null && parent.annotated.getAnnotation(Column.class) != null) {
            return parent.getColumnName();
        }
        return columnName;
    }
}
