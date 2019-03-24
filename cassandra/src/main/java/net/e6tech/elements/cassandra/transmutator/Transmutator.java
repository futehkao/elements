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

package net.e6tech.elements.cassandra.transmutator;

import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncResultSet;
import net.e6tech.elements.cassandra.etl.*;
import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;


public abstract class Transmutator implements Strategy<PartitionContext> {

    private LinkedList<Entry> transmutators = new LinkedList<>();

    protected <T> Map<PrimaryKey, T> transform(PartitionContext context, Set<PrimaryKey> keys, Class<T> tableClass) {
        Map<PrimaryKey, T> map = new HashMap<>();
        context.get(keys, tableClass)
                .inCompletionOrder(item -> {
                    if (item != null)
                        map.put(new PrimaryKey(), item);
                });
        return map;
    }

    protected abstract void undo(PartitionContext context);

    protected void undo(PartitionContext context, Class tableClass) {
        Inspector inspector = context.getInspector(tableClass);
        String tableName = inspector.tableName();
        String partitionKey = inspector.getPartitionKeyColumn(0);
        String checkpointColumn = inspector.getCheckpointColumn(0);
        if (checkpointColumn == null)
            return;
        String filter = "";
        if (checkpointColumn.equals(partitionKey))
            filter = "allow filtering";
        Object value = context.getLastUpdateValue();

        Set list = new HashSet();
        AsyncResultSet<?> result = context.createAsync("select " + partitionKey + ", count(*) from " + tableName +
                " where " + checkpointColumn + " > :spk group by " + partitionKey + " " + filter)
                .execute(bound -> bound.set("spk", value, (Class) value.getClass()));
        result.inCompletionOrderRows(row -> {
            if (!row.isNull(0)) {
                list.add(row.get(0, value.getClass()));
            }
        });

        Async async = context.createAsync("delete from " + inspector.tableName() + " where " + partitionKey + " = :partitionKey");
        async.execute(list, (p, bound) ->  bound.set("partitionKey", p, (Class) p.getClass()))
                .inCompletionOrder();
    }

    private void analyze() {
        Class cls = getClass();
        while (cls != null && cls != Object.class) {
            Method[] methods = cls.getDeclaredMethods();
            for (Method method : methods) {
                Loader loader = method.getAnnotation(Loader.class);
                if (method.getAnnotation(Loader.class) == null)
                    continue;

                if (!method.getReturnType().equals(Integer.TYPE)) {
                    throw new SystemException("Invalid return type for method " + method + ", expecting int");
                }
                if (method.getParameterTypes().length != 2) {
                    throw new SystemException("Invalid number of parameters for method " + method + ", expecting 2");
                }

                Class ptype = method.getParameterTypes()[1];
                if (!ptype.isArray()) {
                    throw new SystemException("Invalid signature for method " + method + ", expecting array type for argument 2.");
                }
                Class componentType = ptype.getComponentType();
                try {
                    PartitionContext context = PartitionContext.createContext(null, componentType);
                    PartitionStrategy strategy = context.createStrategy();
                    context.setExtractorName(componentType.getName() + "_" + getClass().getSimpleName());
                    context.setLoadDelegate(list -> {
                        try {
                            return (Integer) method.invoke(Transmutator.this, context, list.toArray((Object[])Array.newInstance(componentType,0)));
                        } catch (Exception e) {
                            throw new SystemException(e);
                        }
                    });
                    Entry entry = new Entry(loader.value(), context, strategy);
                    transmutators.addLast(entry);
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
            cls = cls.getSuperclass();
        }

        Collections.sort(transmutators, Comparator.comparingInt(p -> p.order));
    }

    @Override
    public int run(PartitionContext context) {
        context.setSourceClass(getClass());
        analyze();
        int count = 0;

        for (Entry entry : transmutators) {
            entry.context.setStartTime(context.getStartTime());
            entry.context.setProvision(context.getProvision());
        }

        for (Entry entry : transmutators) {
            undo(entry.context);
        }

        for (Entry entry : transmutators) {
            try {
                count += entry.strategy.run(entry.context);
            } catch (Exception ex) {
                undo(entry.context);
                throw ex;
            }
        }
        return count;
    }

    private static class Entry {
        int order;
        PartitionContext context;
        PartitionStrategy strategy;

        Entry(int order, PartitionContext context, PartitionStrategy strategy) {
            this.order = order;
            this.context = context;
            this.strategy= strategy;
        }
    }
}