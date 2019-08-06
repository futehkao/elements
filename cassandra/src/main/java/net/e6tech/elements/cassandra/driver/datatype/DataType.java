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

package net.e6tech.elements.cassandra.driver.datatype;

import net.e6tech.elements.cassandra.generator.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class DataType {
    private boolean frozen;
    private Type type;
    private Generator generator;
    private boolean timeBased;

    public static DataType create(Generator generator, Type type, TypeDescriptor typeDescriptor) {
        Class cls;

        if (type instanceof ParameterizedType) {
            cls = (Class) ((ParameterizedType) type).getRawType();
        } else {
            cls = (Class) type;
        }

        if (Map.class.isAssignableFrom(cls)) {
            return new MapType(generator, type, typeDescriptor);
        } else if (List.class.isAssignableFrom(cls)) {
            return new ListType(generator, type, typeDescriptor);
        } else if (Set.class.isAssignableFrom(cls)) {
            return new SetType(generator, type, typeDescriptor);
        } else {
            return new DataType(generator, type, typeDescriptor);
        }
    }

    public DataType(Generator generator, Type type, TypeDescriptor typeDescriptor) {
        this.generator = generator;
        this.type = type;
        frozen = typeDescriptor.isFrozen();
        timeBased = typeDescriptor.isTimeBased();
    }

    public boolean getFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public Type getType() {
        return type;
    }

    public String getTypeString() {

        if (type instanceof Class && Enum.class.isAssignableFrom((Class) type)) {
            if (frozen)
                return "frozen<text>";
            else
                return "text";
        }

        String cqlType = generator.getDataType(type);
        if (timeBased && cqlType.equalsIgnoreCase("uuid")) {
            cqlType = "timeuuid";
        }
        if (frozen)
            return "frozen<" + cqlType + ">";
        else
            return cqlType;
    }
}
