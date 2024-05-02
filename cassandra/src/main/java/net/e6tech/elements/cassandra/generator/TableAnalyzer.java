/*
 * Copyright 2015-2020 Futeh Kao
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

import net.e6tech.elements.common.logging.Logger;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

public class TableAnalyzer {
    private static Logger logger = Logger.getLogger();

    private Map<String, ColumnInfo> columns = new LinkedHashMap<>();
    private List<KeyColumn> clusteringKeys = new ArrayList<>();
    private List<KeyColumn> partitionKeys = new ArrayList<>();
    private String tableName;
    private String tableKeyspace;

    public TableAnalyzer(Generator generator, Class entityClass) throws IntrospectionException {
        LinkedList<Class> classHierarchy = analyze(generator, entityClass);
        Set<String> transientNames = collectionTransient(generator, entityClass, classHierarchy);

        for (PropertyDescriptor desc : Introspector.getBeanInfo(entityClass).getPropertyDescriptors()) {
            analyzeProperty(generator, desc, transientNames);
        }

        for (Class cls : classHierarchy) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                analyzeField(generator, field, transientNames);
            }
        }

        partitionKeys.removeIf(keyColumn -> keyColumn.getPosition() < 0);
        clusteringKeys.removeIf(keyColumn -> keyColumn.getPosition() < 0);
        Collections.sort(partitionKeys, Comparator.comparingInt(KeyColumn::getPosition));
        Collections.sort(clusteringKeys, Comparator.comparingInt(KeyColumn::getPosition));
    }

    private LinkedList<Class> validate(Generator generator, Class entityClass) throws IntrospectionException {
        LinkedList<Class> classHierarchy = analyze(generator, entityClass);
        Map<String, Class> keys = new HashMap<>(64);
        Map<String, Class> current = new HashMap<>(64);
        Set<String> notKeys = new HashSet<>(64);
        Set<Method> seenProps = new HashSet<>(64);
        for (Class cls : classHierarchy) {
            for (PropertyDescriptor desc : Introspector.getBeanInfo(cls).getPropertyDescriptors()) {
                Method method = desc.getReadMethod();
                if (method == null && desc.getWriteMethod() != null) {
                    method = desc.getWriteMethod();
                }

                if (seenProps.contains(method))
                    continue;

                boolean isKey;
                isKey = generator.partitionKeyIndex(desc, idx -> current.put(generator.getColumnName(desc), cls));
                isKey = isKey || generator.clusteringColumnIndex(desc, idx -> current.put(generator.getColumnName(desc), cls));
                if (!isKey)
                    notKeys.add(generator.getColumnName(desc));
                else
                    notKeys.remove(generator.getColumnName(desc));

                seenProps.add(method);
            }
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                boolean isKey;
                isKey = generator.partitionKeyIndex(field, idx -> current.put(generator.getColumnName(field), cls));
                isKey = isKey || generator.clusteringColumnIndex(field, idx -> current.put(generator.getColumnName(field), cls));
                if (!isKey)
                    notKeys.add(generator.getColumnName(field));
                else
                    notKeys.remove(generator.getColumnName(field));
            }

            for (String notKeyColumn : notKeys) {
                if (keys.keySet().contains(notKeyColumn)) {
                    Class superClass = keys.get(notKeyColumn);
                    logger.warn("{} '{}' column attempts to override and nullify superclass {} PartitionKey or ClusteringKey declaration.",
                            entityClass.getName(), notKeyColumn, superClass);
                }
            }

            keys.putAll(current);
            current.clear();
            notKeys.clear();
        }
        return classHierarchy;
    }

    private Set<String> collectionTransient(Generator generator, Class entityClass, List<Class> classHierarchy) throws IntrospectionException {
        Set<String> transientNames = new HashSet<>(50);
        for (Class cls : classHierarchy) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (!Modifier.isStrict(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())
                        && generator.isTransient(field))
                    transientNames.add(generator.getColumnName(field));
            }
        }

        for (PropertyDescriptor desc : Introspector.getBeanInfo(entityClass).getPropertyDescriptors()) {
            if (generator.isTransient(desc)) {
                transientNames.add(generator.getColumnName(desc));
            }
        }
        return transientNames;
    }

    private void analyzeProperty(Generator generator, PropertyDescriptor desc, Set<String> transientNames) {
        Method method = null;
        Type type = null;
        if (desc.getReadMethod() != null) {
            method = desc.getReadMethod();
            type = method.getGenericReturnType();
        }
        if (method == null && desc.getWriteMethod() != null) {
            method = desc.getWriteMethod();
            type = method.getGenericParameterTypes()[0];
        }

        if (method != null && !desc.getName().equals("class")) {
            if (transientNames.contains(generator.getColumnName(desc)))
                return;

            generator.partitionKeyIndex(desc, pk -> addKey(partitionKeys, generator.getColumnName(desc), pk, desc, null));
            generator.clusteringColumnIndex(desc, cc -> addKey(clusteringKeys, generator.getColumnName(desc), cc, desc, null));

            ColumnInfo column = columns.get(generator.getColumnName(desc));
            if (column == null) {
                columns.put(generator.getColumnName(desc), new ColumnInfo(generator.getColumnName(desc), type, desc, null));
            } else {
                if (column.getPropertyDescriptor() == null)
                    column.setPropertyDescriptor(desc);
            }
        }
    }

    private void analyzeField(Generator generator, Field field, Set<String> transientNames) {
        if (Modifier.isStrict(field.getModifiers()) || Modifier.isStatic(field.getModifiers())
                || transientNames.contains(generator.getColumnName(field)))
            return;

        generator.partitionKeyIndex(field, pk -> addKey(partitionKeys, generator.getColumnName(field), pk, null, field));
        generator.clusteringColumnIndex(field, cc -> addKey(clusteringKeys, generator.getColumnName(field), cc, null, field));

        ColumnInfo column = columns.get(generator.getColumnName(field));
        if (column == null) {
            columns.put(generator.getColumnName(field), new ColumnInfo(generator.getColumnName(field), field.getGenericType(), null, field));
        } else {
            if (column.getField() == null)
                column.setField(field);
        }
    }

    private void addKey(List<KeyColumn> columns, String columnName, int index, PropertyDescriptor descriptor, Field field) {
        boolean exist = false;
        Iterator<KeyColumn> iterator = columns.iterator();
        while (iterator.hasNext()) {
            KeyColumn keyColumn = iterator.next();
            if (keyColumn.getName().equals(columnName) || (keyColumn.getPosition() >= 0 && keyColumn.getPosition() == index)) {
                exist = true;
                break;
            }
        }

        if (!exist) {
            columns.add(new KeyColumn(columnName, index, descriptor, field));
        }
    }

    protected LinkedList<Class> analyze(Generator generator, Class entityClass) {
        if (entityClass == null)
            return new LinkedList<>();
        Class tmp = entityClass;
        LinkedList<Class> classHierarchy = new LinkedList<>();
        while (tmp != null && tmp != Object.class) {
            if (generator.tableAnnotation(tmp) != null) {
                if (tableName == null)
                    tableName = generator.tableName(tmp);
                if (tableKeyspace == null)
                    tableKeyspace = generator.tableKeyspace(tmp);
            }
            classHierarchy.addFirst(tmp);
            tmp = tmp.getSuperclass();
        }
        return classHierarchy;
    }

    public List<KeyColumn> getPartitionKeys() {
        return partitionKeys;
    }

    public List<KeyColumn> getClusteringKeys() {
        return clusteringKeys;
    }

    public Map<String, ColumnInfo> getColumns() {
        return columns;
    }
}
