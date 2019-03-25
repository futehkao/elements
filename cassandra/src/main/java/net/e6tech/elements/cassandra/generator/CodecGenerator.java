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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CodecGenerator extends AbstractGenerator {

    private List<ColumnGenerator> columnGenerators = new ArrayList<>();
    private String userType;

    CodecGenerator(Generator generator, String userType, Class<? extends Codec> codecClass) {
        super(generator);
        this.userType = userType;
        for (Codec.MappingDescriptor descriptor : Codec.analyze(codecClass)) {
            Type type = null;
            if (descriptor.getPropertyDescriptor().getReadMethod() != null) {
                type = descriptor.getPropertyDescriptor().getReadMethod().getGenericReturnType();
            } else if (descriptor.getPropertyDescriptor().getWriteMethod() != null) {
                type = descriptor.getPropertyDescriptor().getWriteMethod().getGenericParameterTypes()[0];
            }
            if (type == null)
                continue;;
            columnGenerators.add(new ColumnGenerator(generator, type, descriptor));
        }
    }

    public String getTableName() {
        return userType;
    }

    public String getTableKeyspace() {
        return "";
    }

    public String generate() {
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TYPE IF NOT EXISTS ");
        builder.append(fullyQualifiedTableName());
        builder.append(" (\n");
        boolean first = true;
        for (ColumnGenerator gen : columnGenerators) {
            if (first) {
                first = false;
            } else {
                builder.append(",\n");
            }
            builder.append(gen.generate());
        }
        builder.append(")");
        return builder.toString();
    }
}
