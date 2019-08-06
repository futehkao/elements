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

package net.e6tech.elements.cassandra.driver.v3;

import net.e6tech.elements.cassandra.driver.datatype.DataType;
import net.e6tech.elements.cassandra.driver.datatype.ListType;
import net.e6tech.elements.cassandra.driver.datatype.MapType;
import net.e6tech.elements.cassandra.driver.datatype.SetType;
import net.e6tech.elements.cassandra.driver.metadata.AbstractColumnMetadata;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.cassandra.generator.TypeDescriptorImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ColumnMetadataV3 extends AbstractColumnMetadata {

    public ColumnMetadataV3(Generator generator, com.datastax.driver.core.ColumnMetadata metadata) {
        setName(metadata.getName());
        com.datastax.driver.core.DataType dataType = metadata.getType();
        DataType d = createDataType(generator, dataType);
        setDataType(d);
    }

    protected static DataType createDataType(Generator generator, com.datastax.driver.core.DataType dataType) {
        TypeDescriptorImpl impl = new TypeDescriptorImpl();
        Class type = null;
        switch (dataType.getName()) {
            case ASCII: type = String.class; break;
            case BIGINT: type = Long.class; break;
            case BLOB: type = byte[].class; break;
            case BOOLEAN: type = Boolean.class; break;
            case COUNTER: type = Long.class; break;
            case DECIMAL: type = BigDecimal.class; break;
            case DOUBLE: type = Double.class; break;
            case FLOAT: type = Float.class; break;
            case INT: type = Integer.class; break;
            case TEXT: type = String.class; break;
            case TIMESTAMP: type = Instant.class; break;
            case UUID: type = UUID.class; break;
            case VARCHAR: type = String.class; break;
            case VARINT: type = BigInteger.class; break;
            case TIMEUUID: type = UUID.class; impl.setTimeBased(true); break;
            case INET: type = InetAddress.class; break;
            case DATE: type = LocalDate.class; break;
            case TIME: type = LocalTime.class; break;
            case SMALLINT: type = Short.class; break;
            case TINYINT: type = Byte.class; break;
            case LIST: type = List.class; break;
            case MAP: type = Map.class; break;
            case SET: type = Set.class; break;
        }
        if (type == null)
            return null;

        impl.setFrozen(dataType.isFrozen());
        DataType d = DataType.create(generator, type, impl);

        if (d instanceof ListType && dataType.getTypeArguments().size() > 0) {
            ((ListType) d).setComponentType(createDataType(generator, dataType.getTypeArguments().get(0)));
        } else if (d instanceof SetType && dataType.getTypeArguments().size() > 0) {
            ((SetType) d).setComponentType(createDataType(generator, dataType.getTypeArguments().get(0)));
        } else if (d instanceof MapType && dataType.getTypeArguments().size() > 1) {
            DataType keyType = createDataType(generator, dataType.getTypeArguments().get(0));
            DataType valueType = createDataType(generator, dataType.getTypeArguments().get(1));
            ((MapType) d).setKeyType(keyType);
            ((MapType) d).setValueType(valueType);
        }

        return d;
    }
}
