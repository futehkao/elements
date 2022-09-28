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
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.cassandra.etl.*;
import net.e6tech.elements.common.util.MapBuilder;
import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings("unchecked")
public abstract class Transmutator implements Strategy<PartitionContext> {

    private LinkedList<Descriptor> descriptors = new LinkedList<>();
    private Customizer customizer = null;
    private Map<String, ETLSettings> settings = new HashMap<>();

    public Map<String, ETLSettings> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, ETLSettings> settings) {
        this.settings = settings;
    }

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

            ResultSet resultSet = sibyl.execute("select " + partitionKey + ", count(*) from " + tableName +
                    " where " + checkpointColumn + " > :spk group by " + partitionKey + " " + filter,
                    MapBuilder.of("spk", value));
            for (Row row : resultSet.all()) {
                list.add(row.get(0, value.getClass()));
            }

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
                if (method.getAnnotation(Loader.class) != null)
                    setupLoader(method);
                if (method.getAnnotation(PartitionLoader.class) != null)
                    setupPartitionLoader(method);
            }
            cls = cls.getSuperclass();
        }

        Collections.sort(descriptors, Comparator.comparingInt(p -> p.order));
    }

    private void setupLoader(Method method) {
        Loader loader = method.getAnnotation(Loader.class);
        setupContext(loader.value(), method, null, RunType.EACH_ENTRY);
    }

    private void setupPartitionLoader(Method method) {
        PartitionLoader loader = method.getAnnotation(PartitionLoader.class);
        setupContext(loader.value(), method, loader.sourceClass(), RunType.PARTITION);
    }

    private void setupContext(int order, Method method, Class src, RunType runType) {

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
        Class sourceClass = extractorSourceClass(order, method, src);
        Class componentType = extractorComponentClass(order, method, src);
        try {
            if (!Partition.class.isAssignableFrom(sourceClass)) {
                throw new SystemException("Source class " + sourceClass + " does not implement Partition inteface");
            }

            PartitionContext context = PartitionContext.createContext(null, sourceClass);

            if (!method.getParameterTypes()[0].isAssignableFrom(context.getClass())) {
                throw new SystemException("Invalid loader (" + getClass() + ") argument type declaration for method " + method
                        + ": expecting " +  context.getClass() + " but declared as " + method.getParameterTypes()[0]);
            }

            PartitionStrategy strategy = context.createStrategy();
            context.setExtractorName(extractorName(sourceClass));
            context.setLoadDelegate(list -> {
                try {
                    return (Integer) method.invoke(Transmutator.this, context, list.toArray((Object[]) Array.newInstance(componentType, 0)));
                } catch (Exception e) {
                    throw new SystemException("Unable to invoke " + method, e);
                }
            });

            ETLSettings s = settings.get("" + order);

            Descriptor entry = new Descriptor(order, context, strategy, runType, s);

            descriptors.addLast(entry);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    protected Class extractorSourceClass(int order, Method method, Class src) {
        Class ptype = method.getParameterTypes()[1];
        return (src != null) ? src : ptype.getComponentType();
    }

    protected Class extractorComponentClass(int order, Method method, Class src) {
        Class ptype = method.getParameterTypes()[1];
        return ptype.getComponentType();
    }

    protected String extractorName(Class sourceClass) {
        return sourceClass.getName() + "_" + getClass().getSimpleName();
    }

    public List<Descriptor> describe() {
        return descriptors;
    }

    @SuppressWarnings({"squid:S3776", "squid:S1301"})
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
            if (entry.settings != null) {
                ETLSettings s = entry.settings;
                if (s.getStartTime() != null)
                    entry.context.setStartTime(s.getStartTime());
                if (s.getBatchSize() != null)
                    entry.context.setBatchSize(s.getBatchSize());
                if (s.getExtractAll() != null)
                    entry.context.setExtractAll(s.getExtractAll());
                if (s.getTimeLag() != null)
                    entry.context.setTimeLag(s.getTimeLag());
            }
        }

        if (customizer != null) {
            for (Descriptor entry : descriptors) {
                customizer.customize(context, entry);
            }
        }

        for (Descriptor entry : descriptors) {
            try {
                switch (entry.runType) {
                    case EACH_ENTRY:
                        count += entry.strategy.run(entry.context);
                        break;
                    case PARTITION:
                        count += entry.strategy.runPartitions(entry.context);
                        break;
                }
            } catch (Exception ex) {
                String info = "";
                if (entry.context != null) {
                    info = "extractor=" + entry.context.extractor() +
                            " sourceClass=" + entry.context.getSourceClass() +
                            " tableName=" + entry.context.tableName();
                }
                logger.warn("Cannot transmutate " + info, ex);
            }
        }
        return count;
    }

    public static class Descriptor {
        int order;
        PartitionContext context;
        PartitionStrategy strategy;
        RunType runType;
        ETLSettings settings;


        Descriptor(int order, PartitionContext context, PartitionStrategy strategy, RunType runType, ETLSettings settings) {
            this.order = order;
            this.context = context;
            this.strategy= strategy;
            this.runType = runType;
            this.settings = settings;
        }
    }

    public interface Customizer {
        void customize(PartitionContext context, Descriptor descriptor);
    }

    private enum RunType {
        EACH_ENTRY,
        PARTITION;
    }
}
