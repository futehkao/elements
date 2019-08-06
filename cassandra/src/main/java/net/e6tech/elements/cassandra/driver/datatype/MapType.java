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

import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.cassandra.generator.TypeDescriptor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class MapType extends DataType {
    private DataType keyType;
    private DataType valueType;

    public MapType(Generator generator, Type type, TypeDescriptor typeDescriptor) {
        super(generator, type, typeDescriptor);
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            keyType = DataType.create(generator, pType.getActualTypeArguments()[0], typeDescriptor);
            keyType.setFrozen(typeDescriptor.isFrozenKey());
            valueType = DataType.create(generator, pType.getActualTypeArguments()[1], typeDescriptor);
            valueType.setFrozen(typeDescriptor.isFrozenValue());
        }
    }

    @Override
    public String getTypeString() {
        return "map<" + keyType.getTypeString() + ", " + valueType.getTypeString() + ">";
    }

    public DataType getKeyType() {
        return keyType;
    }

    public void setKeyType(DataType keyType) {
        this.keyType = keyType;
    }

    public DataType getValueType() {
        return valueType;
    }

    public void setValueType(DataType valueType) {
        this.valueType = valueType;
    }
}
