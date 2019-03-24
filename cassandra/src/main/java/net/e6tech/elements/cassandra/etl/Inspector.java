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

import com.datastax.driver.mapping.annotations.*;
import net.e6tech.elements.cassandra.generator.Checkpoint;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.common.util.SystemException;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Inspector {
    private Generator generator;
    private Class sourceClass;
    private boolean initialized = false;
    private TimeUnit timeUnit;
    private List<Descriptor> partitionKeys = new ArrayList<>();
    private List<Descriptor> clusteringKeys = new ArrayList<>();
    private List<Descriptor> checkpoints = new ArrayList<>();

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

    public void addPartitionKey(Descriptor descriptor) {
        partitionKeys.add(descriptor);
        Collections.sort(partitionKeys, Comparator.comparingInt(p -> p.position));
    }

    public void addClusteringKey(Descriptor descriptor) {
        clusteringKeys.add(descriptor);
        Collections.sort(clusteringKeys, Comparator.comparingInt(p -> p.position));
    }

    public String getPartitionKeyColumn(int n) {
        if (partitionKeys.size() <= n)
            return null;
        return partitionKeys.get(n).columnName;
    }

    public Class getPartitionKeyClass(int n) {
        return getKeyClass(partitionKeys, n);
    }

    private Class getKeyClass(List<Descriptor> keys, int n) {
        if (keys.size() <= n)
            return null;
        Descriptor descriptor =  keys.get(n);
        if (descriptor.getPropertyDescriptor() != null) {
            return descriptor.getPropertyDescriptor().getReadMethod().getReturnType();
        } else {
            return descriptor.getField().getType();
        }
    }

    public Object getPartitionKey(Object object, int n) {
        return getKey(partitionKeys, object, n);
    }

    private Object getKey(List<Descriptor> keys, Object object, int n) {
        if (keys.size() <= n)
            return null;
        return get(keys.get(n), object);
    }

    public String getCheckpointColumn(int n) {
        if (checkpoints.size() <= n)
            return null;
        return checkpoints.get(n).columnName;
    }

    public void setCheckpoint(Object object, int n, Object value) {
        if (checkpoints.size() <= n)
            return;
        set(checkpoints.get(n), object, value);
    }

    public String getClusteringKeyColumn(int n) {
        if (clusteringKeys.size() <= n)
            return null;
        return clusteringKeys.get(n).columnName;
    }

    public Class getClusteringKeyClass(int n) {
        return getKeyClass(clusteringKeys, n);
    }

    public Object getClusteringKey(Object object, int n) {
        return getKey(clusteringKeys, object, n);
    }

    public String tableName() {
        Table table = (Table) sourceClass.getAnnotation(Table.class);
        if (table == null)
            throw new IllegalArgumentException("Class " + sourceClass + " is not annotated with @Table");
        return table.name();
    }

    private Object get(Descriptor descriptor, Object object) {
        try {
            if (descriptor.getPropertyDescriptor() != null && descriptor.getPropertyDescriptor().getReadMethod() != null) {
                return descriptor.getPropertyDescriptor().getReadMethod().invoke(object);
            } else {
                return descriptor.field.get(object);
            }
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
    }

    private void set(Descriptor descriptor, Object object, Object value) {
        try {
            if (descriptor.getPropertyDescriptor() != null && descriptor.getPropertyDescriptor().getWriteMethod() != null) {
                descriptor.getPropertyDescriptor().getWriteMethod().invoke(object, value);
            } else {
                descriptor.field.set(object, value);
            }
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
    }

    public void setPrimaryKey(PrimaryKey key, Object object) {
        try {
            int idx = 0;
            for (Descriptor descriptor : partitionKeys) {
                if (key.length() > idx) {
                    set(descriptor, object, key.get(idx));
                    idx++;
                } else {
                    break;
                }
            }
            for (Descriptor descriptor : clusteringKeys) {
                if (key.length() > idx) {
                    set(descriptor, object, key.get(idx));
                    idx++;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new SystemException(e);
        }

    }

    public PrimaryKey getPrimaryKey(Object object) {
        try {
            List list = new ArrayList();
            for (Descriptor descriptor : partitionKeys) {
                if (descriptor.getPropertyDescriptor() != null && descriptor.getPropertyDescriptor().getReadMethod() != null)
                    list.add(descriptor.getPropertyDescriptor().getReadMethod().invoke(object));
                else
                    list.add(descriptor.field.get(object));
            }
            for (Descriptor descriptor : clusteringKeys) {
                if (descriptor.getPropertyDescriptor() != null && descriptor.getPropertyDescriptor().getReadMethod() != null)
                    list.add(descriptor.getPropertyDescriptor().getReadMethod().invoke(object));
                else
                    list.add(descriptor.field.get(object));
            }
            return new PrimaryKey(list.toArray(new Object[0]));
        } catch (Exception e) {
           throw new SystemException(e);
        }
    }

    public void initialize() {
        if (initialized)
            return;
        initialized = true;
        Map<String, Descriptor> propertyMap = new HashMap<>();
        Map<String, Descriptor> spkMap = new HashMap<>();

        Generator generator = getGenerator();
        Class cls = getSourceClass();
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStrict(field.getModifiers()))
                    continue;
                Transient trans = field.getAnnotation(Transient.class);
                if (trans != null)
                    continue;
                ClusteringColumn cc = field.getAnnotation(ClusteringColumn.class);
                PartitionKey pk = field.getAnnotation(PartitionKey.class);
                PartitionUnit unit = field.getAnnotation(PartitionUnit.class);
                Checkpoint spk = field.getAnnotation(Checkpoint.class);
                Column column = field.getAnnotation(Column.class);
                if (cc != null) {
                    Descriptor descriptor = new Descriptor(cc.value(), generator.getColumnName(column, field), field.getName());
                    descriptor.field = field;
                    clusteringKeys.add(descriptor);
                    field.setAccessible(true);
                    propertyMap.put(field.getName(), descriptor);
                    propertyMap.put(descriptor.columnName, descriptor);
                }

                if (pk != null) {
                    Descriptor descriptor = new Descriptor(pk.value(), generator.getColumnName(column, field), field.getName());
                    descriptor.field = field;
                    partitionKeys.add(descriptor);
                    field.setAccessible(true);
                    propertyMap.put(field.getName(), descriptor);
                    propertyMap.put(descriptor.columnName, descriptor);
                    if (unit != null && pk.value() == 0)
                        timeUnit = unit.value();
                }

                if (spk != null) {
                    Descriptor descriptor = new Descriptor(spk.value(), generator.getColumnName(column, field), field.getName());
                    descriptor.field = field;
                    field.setAccessible(true);
                    checkpoints.add(descriptor);
                    spkMap.put(field.getName(), descriptor);
                    spkMap.put(descriptor.columnName, descriptor);
                }
            }
            cls = cls.getSuperclass();
        }

        try {
            for (PropertyDescriptor desc : Introspector.getBeanInfo(getSourceClass()).getPropertyDescriptors()) {
                Method m = null;
                if (desc.getReadMethod() != null) {
                    m = desc.getReadMethod();
                }

                if (m != null && !m.getName().equals("getClass")) {
                    Transient trans = m.getAnnotation(Transient.class);
                    if (trans != null)
                        continue;
                    Column column = m.getAnnotation(Column.class);
                    Descriptor descriptor = propertyMap.get(desc.getName());
                    String columnName = generator.getColumnName(column, m);
                    if (descriptor == null) {
                        descriptor = propertyMap.get(columnName);
                    }

                    ClusteringColumn cc = m.getAnnotation(ClusteringColumn.class);
                    PartitionKey pk = m.getAnnotation(PartitionKey.class);
                    Checkpoint spk = m.getAnnotation(Checkpoint.class);
                    PartitionUnit unit = m.getAnnotation(PartitionUnit.class);
                    if (cc != null && descriptor == null) {
                        descriptor = new Descriptor(cc.value(),generator.getColumnName(column, m), desc.getName());
                        clusteringKeys.add(descriptor);
                        propertyMap.put(desc.getName(), descriptor);
                        propertyMap.put(descriptor.columnName, descriptor);
                    }

                    if (pk != null && descriptor == null) {
                        descriptor = new Descriptor(pk.value(),generator.getColumnName(column, m), desc.getName());
                        partitionKeys.add(descriptor);
                        propertyMap.put(desc.getName(), descriptor);
                        propertyMap.put(descriptor.columnName, descriptor);
                        if (unit != null && pk.value() == 0)
                            timeUnit = unit.value();
                    }

                    if (descriptor != null) {
                        descriptor.setPropertyDescriptor(desc);
                    }

                    Descriptor spkDescriptor = spkMap.get(desc.getName());
                    if (spkDescriptor == null)
                        spkDescriptor = spkMap.get(columnName);
                    if (spk != null && spkDescriptor == null) {
                        spkDescriptor = new Descriptor(spk.value(), generator.getColumnName(column, m), desc.getName());
                        checkpoints.add(descriptor);
                        spkMap.put(desc.getName(), descriptor);
                        spkMap.put(spkDescriptor.columnName, descriptor);
                    }
                    if (spkDescriptor != null) {
                        spkDescriptor.setPropertyDescriptor(desc);
                    }
                }
            }
        } catch (IntrospectionException e) {
            throw new SystemException(e);
        }

        Collections.sort(partitionKeys, Comparator.comparingInt(p -> p.position));
        Collections.sort(clusteringKeys, Comparator.comparingInt(p -> p.position));
        Collections.sort(checkpoints, Comparator.comparingInt(p -> p.position));
    }

    public static class Descriptor {
        int position;
        Field field;
        String columnName;
        String property;
        PropertyDescriptor propertyDescriptor;

        public Descriptor(int pos, String columnName, String property) {
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

        public PropertyDescriptor getPropertyDescriptor() {
            return propertyDescriptor;
        }

        public void setPropertyDescriptor(PropertyDescriptor propertyDescriptor) {
            this.propertyDescriptor = propertyDescriptor;
        }

        public Field getField() {
            return field;
        }

        public void setField(Field field) {
            this.field = field;
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
