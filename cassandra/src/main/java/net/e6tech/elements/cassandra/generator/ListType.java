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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ListType extends DataType {
    DataType componentType;

    public ListType(Generator generator, Type type, TypeDescriptor typeDescriptor) {
        super(generator, type, typeDescriptor);
        ParameterizedType pType = (ParameterizedType) type;
        componentType = DataType.create(generator, pType.getActualTypeArguments()[0], typeDescriptor);
    }

    public String getTypeString() {
        return "list<" + componentType.getTypeString() + ">";
    }
}
