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

import com.datastax.driver.mapping.annotations.Table;
import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.common.util.SystemException;

import java.beans.IntrospectionException;
import java.util.LinkedList;

public class AbstractGenerator {

    protected Generator generator;
    private Table table;
    private String keyspace;

    AbstractGenerator(Generator generator) {
        this.generator = generator;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public Table getTable() {
        return table;
    }

    public String getTableName() {
        return table.name();
    }

    public String getTableKeyspace() {
        return table.keyspace();
    }

    protected LinkedList<Class> analyze(Class entityClass) throws IntrospectionException {
        if (entityClass == null)
            return new LinkedList<>();
        Class tmp = entityClass;
        LinkedList<Class> classHierarchy = new LinkedList<>();
        while (tmp != null && tmp != Object.class) {
            if (table == null)
                table = (Table) tmp.getAnnotation(Table.class);
            classHierarchy.addFirst(tmp);
            tmp = tmp.getSuperclass();
        }

        if (table == null) {
            throw new IntrospectionException("Class " + entityClass.getName() + " is not annotated with @Table");
        }
        return classHierarchy;
    }

    protected String fullyQualifiedTableName() {
        StringBuilder builder = new StringBuilder();
        if (!StringUtil.isNullOrEmpty(getTableKeyspace())) {
            builder.append(getTableKeyspace()).append(".");
        } else if (!StringUtil.isNullOrEmpty(getKeyspace())) {
            builder.append(getKeyspace()).append(".");
        }
        builder.append(getTableName());
        return builder.toString();
    }
}
