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

import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.AsyncResultSetFutures;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.cassandra.etl.PartitionContext;
import net.e6tech.elements.cassandra.etl.PartitionStrategy;
import net.e6tech.elements.cassandra.etl.Strategy;
import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;


public abstract class Transmutator implements Strategy<PartitionContext> {

    private LinkedList<Descriptor> descriptors = new LinkedList<>();
    private Customizer customizer = null;

    public Customizer getCustomizer() {
        return customizer;
    }

    public void setCustomizer(Customizer customizer) {
        this.customizer = customizer;
    }

    protected void undo(PartitionContext context, Class tableClass) {
        context.open().accept(Sibyl.class, sibyl -> {
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
            AsyncResultSetFutures<?> result = sibyl.createAsync("select " + partitionKey + ", count(*) from " + tableName +
                    " where " + checkpointColumn + " > :spk group by " + partitionKey + " " + filter)
                    .execute(bound -> bound.set("spk", value, (Class) value.getClass()));
            result.inExecutionOrderRows(row -> {
                if (!row.isNull(0)) {
                    list.add(row.get(0, value.getClass()));
                }
            });

            sibyl.createAsync("delete from " + inspector.tableName() + " where " + partitionKey + " = :partitionKey")
                .execute(list, (p, bound) ->  bound.set("partitionKey", p, (Class) p.getClass()))
                    .inExecutionOrder();
        });
    }

    @SuppressWarnings("squid:S3776")
    protected void analyze() {
        descriptors.clear();
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
                    Descriptor entry = new Descriptor(loader.value(), context, strategy);
                    descriptors.addLast(entry);
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
            cls = cls.getSuperclass();
        }

        Collections.sort(descriptors, Comparator.comparingInt(p -> p.order));
    }

    public List<Descriptor> describe() {
        return descriptors;
    }

    @Override
    public int run(PartitionContext context) {
        context.setSourceClass(getClass());
        analyze();
        int count = 0;

        for (Descriptor entry : descriptors) {
            entry.context.setStartTime(context.getStartTime());
            entry.context.setProvision(context.getProvision());
            entry.context.setBatchSize(context.getBatchSize());
            entry.context.setExtractAll(context.isExtractAll());
            entry.context.setTimeLag(context.getTimeLag());
        }

        if (customizer != null) {
            for (Descriptor entry : descriptors) {
                customizer.customize(context, entry);
            }
        }

        for (Descriptor entry : descriptors) {
            count += entry.strategy.run(entry.context);
        }
        return count;
    }

    public static class Descriptor {
        int order;
        PartitionContext context;
        PartitionStrategy strategy;

        Descriptor(int order, PartitionContext context, PartitionStrategy strategy) {
            this.order = order;
            this.context = context;
            this.strategy= strategy;
        }
    }

    public interface Customizer {
        void customize(PartitionContext context, Descriptor descriptor);
    }
}
