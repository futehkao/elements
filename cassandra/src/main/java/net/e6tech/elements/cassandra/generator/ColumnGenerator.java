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

import net.e6tech.elements.cassandra.driver.datatype.DataType;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

public class ColumnGenerator {
    private DataType dataType;
    private TypeDescriptor typeDescriptor;
    private PropertyDescriptor propertyDescriptor;
    private Field field;

    public ColumnGenerator(Generator generator, PropertyDescriptor descriptor, Type type, TypeDescriptor typeDescriptor) {
        dataType = DataType.create(generator, type, typeDescriptor);
        this.typeDescriptor = typeDescriptor;
        this.propertyDescriptor = descriptor;
    }

    public ColumnGenerator(Generator generator, Field field, Type type, TypeDescriptor typeDescriptor) {
        dataType = DataType.create(generator, type, typeDescriptor);
        this.typeDescriptor = typeDescriptor;
        this.field = field;
    }

    public String generate() {
        return "\"" + typeDescriptor.getColumnName() + "\" " + dataType.getTypeString();
    }

    public TypeDescriptor getTypeDescriptor() {
        return typeDescriptor;
    }

    public DataType getDataType() {
        return dataType;
    }

    public PropertyDescriptor getPropertyDescriptor() {
        return propertyDescriptor;
    }

    public Field getField() {
        return field;
    }
}
