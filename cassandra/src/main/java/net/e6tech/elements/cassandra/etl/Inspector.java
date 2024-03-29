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

package net.e6tech.elements.cassandra.etl;

import net.e6tech.elements.cassandra.annotations.Checkpoint;
import net.e6tech.elements.cassandra.annotations.PartitionUnit;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.cassandra.generator.TableAnalyzer;
import net.e6tech.elements.common.reflection.Accessor;
import net.e6tech.elements.common.util.SystemException;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"java:S2445", "java:S3740"})
public class Inspector {
    private Generator generator;
    private Class sourceClass;
    private volatile boolean initialized = false;
    private TimeUnit timeUnit;
    private List<ColumnAccessor> partitionKeys = new LinkedList<>();
    private List<ColumnAccessor> clusteringKeys = new LinkedList<>();
    private List<ColumnAccessor> checkpoints = new LinkedList<>();
    private List<ColumnAccessor> primaryKeyColumns = new LinkedList<>();
    private List<ColumnAccessor> columns;
    private Map<String, ColumnAccessor> columnMap;

    public Inspector(Class sourceClass, Generator generator) {
        this.sourceClass = sourceClass;
        this.generator = generator;
    }

    public Generator getGenerator() {
        return generator;
    }

    public Class getSourceClass() {
        return sourceClass;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    private List<ColumnAccessor> addKey(List<ColumnAccessor> keys, ColumnAccessor descriptor) {
        List<ColumnAccessor> copy = new LinkedList<>(keys);
        Iterator<ColumnAccessor> iterator = copy.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().position == descriptor.position)
                iterator.remove();
        }
        copy.add(descriptor);
        Collections.sort(copy, Comparator.comparingInt(p -> p.position));
        return copy;
    }

    /*
        This code makes a copy because in the method getPrimaryKey, it needs to iterate
        both the partitionKeys and clusterKeys.  Instead of synchronize getPrimaryKey for
        the entire duration of the method, that method only synchronizes for a shorter duration
        just to copy partitionKeys and clusterKeys to local variables and then iterate using
        the local variable.  If addKey and getPrimaryKey are called concurrently, it won't cause
        concurrent modification exception.  However, getPrimaryKey may be using an older image.
        This is true for other get methods related to partitionKeys.
     */
    public synchronized void addPartitionKey(ColumnAccessor descriptor) {
        partitionKeys = addKey(partitionKeys, descriptor);
    }

    /*
        See comments above.
     */
    public synchronized void addClusteringKey(ColumnAccessor descriptor) {
        clusteringKeys = addKey(clusteringKeys, descriptor);
    }

    public int getPartitionKeySize() {
        return partitionKeys.size();
    }

    public int getClusteringKeySize() {
        return clusteringKeys.size();
    }

    public String getKeyColumn(List<ColumnAccessor> keys, int n) {
        if (keys.size() <= n)
            return null;
        return keys.get(n).columnName;
    }

    public String getPartitionKeyColumn(int n) {
        return getKeyColumn(partitionKeys, n);
    }

    public String getClusteringKeyColumn(int n) {
        return getKeyColumn(clusteringKeys, n);
    }

    private Class getKeyClass(List<ColumnAccessor> keys, int n) {
        if (keys.size() <= n)
            return null;
        return keys.get(n).getType();
    }

    public Class getPartitionKeyClass(int n) {
        return getKeyClass(partitionKeys, n);
    }

    public Class getClusteringKeyClass(int n) {
        return getKeyClass(clusteringKeys, n);
    }

    private Object getKey(List<ColumnAccessor> keys, Object object, int n) {
        if (keys.size() <= n)
            return null;
        return keys.get(n).get(object);
    }

    public Object getPartitionKey(Object object, int n) {
        return getKey(partitionKeys, object, n);
    }

    public Object getClusteringKey(Object object, int n) {
        return getKey(clusteringKeys, object, n);
    }

    public String getCheckpointColumn(int n) {
        if (checkpoints.size() <= n)
            return null;
        return checkpoints.get(n).columnName;
    }

    public Comparable getCheckpoint(Object object, int n) {
        if (checkpoints.size() <= n)
            return null;
        return (Comparable) checkpoints.get(n).get(object);
    }

    public void setCheckpoint(Object object, int n, Comparable value) {
        if (checkpoints.size() <= n)
            return;
        checkpoints.get(n).set(object, value);
    }

    public int getCheckpointSize() {
        return checkpoints.size();
    }

    public String tableName() {
        return generator.tableName(sourceClass);
    }

    public String tableName(Class cls) {
        return generator.tableName(cls);
    }

    public void setPrimaryKey(PrimaryKey key, Object object) {
        try {
            List<ColumnAccessor> pkeys;
            List<ColumnAccessor> ckeys;
            synchronized (this) {
                pkeys = partitionKeys;
                ckeys = clusteringKeys;
            }

            int idx = 0;
            for (ColumnAccessor descriptor : pkeys) {
                if (key.length() > idx) {
                    descriptor.set(object, key.get(idx));
                    idx++;
                } else {
                    break;
                }
            }

            for (ColumnAccessor descriptor : ckeys) {
                if (key.length() > idx) {
                    descriptor.set(object, key.get(idx));
                    idx++;
                } else {
                    break;
                }
            }

        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public PrimaryKey getPrimaryKey(Object object) {
        try {
            List list = new ArrayList();
            List<ColumnAccessor> pkeys;
            List<ColumnAccessor> ckeys;
            synchronized (this) {
                pkeys = partitionKeys;
                ckeys = clusteringKeys;
            }

            for (ColumnAccessor descriptor : pkeys) {
                list.add(descriptor.get(object));
            }
            for (ColumnAccessor descriptor : ckeys) {
                list.add(descriptor.get(object));
            }
            return new PrimaryKey(list.toArray(new Object[0]));
        } catch (Exception e) {
           throw new SystemException(e);
        }
    }

    public List<ColumnAccessor> getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public List<ColumnAccessor> getColumns() {
        return columns;
    }

    public ColumnAccessor getColumn(String column) {
        return columnMap.get(column);
    }

    private ColumnAccessor alloc(int position, PropertyDescriptor desc, Field field) {
        Generator gen = getGenerator();
        ColumnAccessor descriptor;
        if (desc != null) {
            descriptor = new ColumnAccessor(position, gen.getColumnName(desc), desc.getName(), desc);
        } else {
            descriptor = new ColumnAccessor(position, gen.getColumnName(field), field.getName(), field);
            field.setAccessible(true);
        }
        return descriptor;
    }

    private ColumnAccessor alloc(int position, PropertyDescriptor desc, Field field, List<ColumnAccessor> list) {
        ColumnAccessor descriptor = alloc(position, desc, field);
        list.add(descriptor);
        return descriptor;
    }

    public synchronized void initialize() {
        if (initialized)
            return;
        initialized = true;
        Map<String, ColumnAccessor> chkMap = new HashMap<>(100);

        try {
            TableAnalyzer analyzer = new TableAnalyzer(getGenerator(), getSourceClass());
            analyzer.getPartitionKeys().forEach(pk -> {
                alloc(pk.getPosition(), pk.getPropertyDescriptor(), pk.getField(), partitionKeys);
                PartitionUnit unit = Accessor.getAnnotation(pk.getPropertyDescriptor(), pk.getField(), PartitionUnit.class);
                if (unit != null && pk.getPosition() == 0)
                    timeUnit = unit.value();
            });

            analyzer.getClusteringKeys().forEach(cc -> alloc(cc.getPosition(), cc.getPropertyDescriptor(), cc.getField(), clusteringKeys));

            AtomicInteger position = new AtomicInteger(0);
            columns = new ArrayList<>(analyzer.getColumns().size());
            analyzer.getColumns().forEach((columnName, column) -> {
                columns.add(alloc(position.getAndIncrement(), column.getPropertyDescriptor(), column.getField()));
                Checkpoint checkpoint = Accessor.getAnnotation(column.getPropertyDescriptor(), column.getField(), Checkpoint.class);
                if (checkpoint != null) {
                    chkMap.computeIfAbsent(columnName, key -> alloc(checkpoint.value(), column.getPropertyDescriptor(), column.getField(),
                            checkpoints));
                }
            });
        } catch (IntrospectionException e) {
            throw new SystemException(e);
        }
        Collections.sort(partitionKeys, Comparator.comparingInt(p -> p.position));
        Collections.sort(clusteringKeys, Comparator.comparingInt(p -> p.position));
        Collections.sort(checkpoints, Comparator.comparingInt(p -> p.position));

        primaryKeyColumns.addAll(partitionKeys);
        primaryKeyColumns.addAll(clusteringKeys);
        columnMap = new HashMap<>(columns.size(), 1);
        columns.forEach(column -> columnMap.put(column.getColumnName(), column));
    }

    public static class ColumnAccessor extends Accessor {
        int position;
        String columnName;
        String property;

        public ColumnAccessor(int pos, String columnName, String property, Field field) {
            super(field);
            this.position = pos;
            this.columnName = columnName;
            this.property = property;
        }

        public ColumnAccessor(int pos, String columnName, String property, PropertyDescriptor desc) {
            super(desc);
            this.position = pos;
            this.columnName = columnName;
            this.property = property;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getProperty() {
            return property;
        }

        public void setProperty(String property) {
            this.property = property;
        }
    }
}
