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

package net.e6tech.elements.cassandra.query;

import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.cassandra.generator.KeyColumn;
import net.e6tech.elements.cassandra.generator.TableGenerator;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.reflection.Primitives;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.datastructure.Triplet;

import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


@SuppressWarnings("unchecked")
public abstract class BaseQuery<T, Q extends BaseQuery<T, Q>> {
    protected static final String AND = " and ";

    protected Sibyl sibyl;
    protected T partitionTemplate;
    protected T clusteringTemplate;
    protected T orderByTemplate;
    protected RelationHandler partitionHandler;
    protected RelationHandler clusteringHandler;
    protected OrderByHandler orderByHandler;
    protected List<Relation> partitionRelations = new ArrayList<>();
    protected List<Relation> orderBy = new ArrayList<>();
    protected List<Relation> clusteringRelations = new ArrayList<>();
    protected int limit = -1;
    protected Class<T> entityClass;
    protected TableGenerator table;
    protected Inspector inspector;

    public BaseQuery(Sibyl sibyl, Class<T> entityClass) {
        this.sibyl = sibyl;
        this.entityClass = entityClass;
        this.partitionHandler = new RelationHandler(true);
        this.clusteringHandler = new RelationHandler(false);
        this.orderByHandler = new OrderByHandler();
        partitionTemplate = Interceptor.getInstance().newInstance(entityClass, partitionHandler );
        clusteringTemplate = Interceptor.getInstance().newInstance(entityClass, clusteringHandler);
        orderByTemplate = Interceptor.getInstance().newInstance(entityClass, orderByHandler);
        table = sibyl.getGenerator().getTable(null, entityClass);
        inspector = sibyl.getInspector(entityClass);
    }

    public Q limit(int limit) {
        this.limit = limit;
        return (Q) this;
    }

    public int limit() {
        return limit;
    }

    protected <R> Q newRelation(BiConsumer<T, R> consumer, R value, Comparison comparison, List<Relation> list, boolean partition) {
        T template = partition ? partitionTemplate : clusteringTemplate;
        RelationHandler relationHandler = partition ? partitionHandler : clusteringHandler;
        consumer.accept(template, value);
        Relation relation = new Relation(relationHandler.keyColumn, comparison, value);

        list.removeIf(r -> r.comparison == comparison && r.keyColumn.getPosition() == relation.keyColumn.getPosition());
        list.add(relation);
        return (Q) this;
    }

    public <R> Q partition(BiConsumer<T, R> consumer, R value) {
        return newRelation(consumer, value, Comparison.EQUAL, partitionRelations, true);
    }

    public <R> Q equalTo(BiConsumer<T, R> consumer, R value) {
        return newRelation(consumer, value, Comparison.EQUAL, clusteringRelations, false);
    }

    protected Q newOrderBy(Consumer<T> consumer, Comparison comparison) {
        orderByHandler.keyColumnValues.clear();
        consumer.accept(orderByTemplate);
        for (KeyColumnValue keyColumnValue : orderByHandler.keyColumnValues) {
            KeyColumn keyColumn = keyColumnValue.keyColumn;
            Relation relation = new Relation(keyColumn, comparison, null);
            orderBy.removeIf(o -> o.keyColumn.getPosition() == relation.keyColumn.getPosition());
            orderBy.add(relation);
        }
        orderByHandler.keyColumnValues.clear();
        orderBy.forEach(o -> o.comparison = comparison);
        return (Q) this;
    }

    protected <R> Q lessThan(BiConsumer<T, R> consumer, R value) {
        return newRelation(consumer, value, Comparison.LESS_THAN, clusteringRelations, false);
    }

    protected <R> Q lessThanOrEqualTo(BiConsumer<T, R> consumer, R value) {
        return newRelation(consumer, value, Comparison.LESS_THAN_OR_EQUAL, clusteringRelations, false);
    }

    protected <R> Q greaterThan(BiConsumer<T, R> consumer, R value) {
        return newRelation(consumer, value, Comparison.GREATER_THAN, clusteringRelations, false);
    }

    protected  <R> Q greaterThanOrEqualTo(BiConsumer<T, R> consumer, R value) {
        return newRelation(consumer, value, Comparison.GREATER_THAN_OR_EQUAL, clusteringRelations, false);
    }

    public <R extends Comparable<R>> Q ascending(BiConsumer<T, R> consumer, R from, R to) {
        if (from.compareTo(to) < 0) {
            greaterThanOrEqualTo(consumer, from);
            lessThan(consumer, to);
        } else {
            greaterThanOrEqualTo(consumer, to);
            lessThan(consumer, from);
        }
        return newOrderBy(t -> consumer.accept(t, from), Comparison.LESS_THAN);
    }

    public <R extends Comparable<R>> Q descending(BiConsumer<T, R> consumer, R from, R to) {
        if (from.compareTo(to) < 0) {
            lessThanOrEqualTo(consumer, to);
            greaterThan(consumer, from);
        } else {
            lessThanOrEqualTo(consumer, from);
            greaterThan(consumer, to);
        }
        return newOrderBy(t -> consumer.accept(t, from), Comparison.GREATER_THAN);
    }

    public Q ascending(Consumer<T> consumer) {
        return newOrderBy(consumer, Comparison.LESS_THAN);
    }

    public Q descending(Consumer<T> consumer) {
        return newOrderBy(consumer, Comparison.GREATER_THAN);
    }

    protected Q clearOrderBy() {
        return (Q) this;
    }

    protected void buildRelation(StringBuilder builder, Map<String, Object> map, Relation relation) {
        builder.append(relation.keyColumn.getName());
        switch (relation.comparison) {
            case EQUAL:
                builder.append(" = ");
                break;
            case LESS_THAN:
                builder.append(" < ");
                break;
            case LESS_THAN_OR_EQUAL:
                builder.append(" <= ");
                break;
            case GREATER_THAN:
                builder.append(" > ");
                break;
            case GREATER_THAN_OR_EQUAL:
                builder.append(" >= ");
                break;
        }

        if (relation.value == null) {
            throw new IllegalArgumentException("comparision value for " + relation.keyColumn.getName() + " cannot be null");
        } else {
            String argument = relation.keyColumn.getName() + "_" + (map.size() + 1);
            builder.append(":").append(argument);
            map.put(argument, relation.value);
        }
    }

    protected void buildPartitionKeys(StringBuilder builder, Map<String, Object> map) {
        boolean first = true;
        for (Relation relation : partitionRelations) {
            if (first) {
                first = false;
            } else {
                builder.append(AND);
            }
            buildRelation(builder, map, relation);
        }
    }

    protected void buildClusteringKeys(StringBuilder builder, Map<String, Object> map) {
        boolean first = true;
        for (Relation relation : clusteringRelations) {
            if (first) {
                first = false;
            } else {
                builder.append(AND);
            }
            buildRelation(builder, map, relation);
        }
    }

    protected void buildOrderBy(StringBuilder builder) {
        boolean first = true;
        for (Relation relation : orderBy) {
            if (first) {
                first = false;
                builder.append(" order by ");
            } else {
                builder.append(", ");
            }
            builder.append(relation.keyColumn.getName());
            if (relation.comparison == Comparison.LESS_THAN) {
                builder.append(" asc ");
            } else if (relation.comparison == Comparison.GREATER_THAN) {
                builder.append(" desc ");
            } else {
                throw new IllegalStateException();
            }
        }
    }

    protected void validate() {
        partitionRelations.sort(Comparator.comparingInt(c -> c.keyColumn.getPosition()));
        clusteringRelations.sort(Comparator.comparingInt(c -> c.keyColumn.getPosition()));
        orderBy.sort(Comparator.comparingInt(c -> c.keyColumn.getPosition()));

        validatePartitionKeys();
        validClusteringKeys();
    }

    protected List<T> select() {
        Map<String, Object> map = new HashMap<>();
        StringBuilder query = buildQuery(map);
        return sibyl.all(entityClass, query.toString(), map);
    }

    protected StringBuilder buildQuery(Map<String, Object> map) {
        StringBuilder query = new StringBuilder("select * from ");
        query.append(table.getTableName())
                .append(" where ");

        buildPartitionKeys(query, map);
        if (!partitionRelations.isEmpty() && !clusteringRelations.isEmpty())
            query.append(BaseQuery.AND);
        buildClusteringKeys(query, map);
        buildOrderBy(query);

        if (limit > 0) {
            query.append(" ").append("limit ").append(limit);
        }

        return query;
    }

    // make sure all partition keys are specified.
    protected void validatePartitionKeys() {
        for (KeyColumn key : table.getPartitionKeys()) {
            boolean found = false;
            for (Relation r : partitionRelations) {
                if (r.keyColumn.getName().equals(key.getName())) {
                    found = true;
                    break;
                }
            }
            if (!found)
                throw new IllegalArgumentException("Partition key " + key.getName() + " not specified.");
        }
    }

    // make sure clustering keys are specified based on some rules
    protected void validClusteringKeys() {
        Relation prev = null;
        for (Relation relation : clusteringRelations) {
            if (prev != null && prev.keyColumn.getPosition() != relation.keyColumn.getPosition() && prev.comparison != Comparison.EQUAL) {
                throw new IllegalArgumentException("Clustering key column " + prev.keyColumn.getName() + " comparison must be '='");
            }

            if (prev != null && prev.keyColumn.getPosition() != relation.keyColumn.getPosition() && relation.keyColumn.getPosition() - prev.keyColumn.getPosition() != 1) {
                throw new IllegalArgumentException("Missing relation for clustering key column " + (relation.keyColumn.getPosition() - 1));
            }
            prev = relation;
        }
    }

    protected enum Comparison {
        EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL;
    }

    protected class Relation {
        Comparison comparison;
        KeyColumn keyColumn;
        Object value;
        Inspector.ColumnAccessor accessor;

        public Relation(KeyColumn keyColumn, Comparison comparison, Object value) {
            this.keyColumn = keyColumn;
            this.comparison = comparison;
            this.value = value;
            this.accessor = inspector.getColumn(keyColumn.getName());
        }
    }

    protected class KeyColumnValue {
        KeyColumn keyColumn;
        Object value;

        public KeyColumnValue(KeyColumn keyColumn, Object value) {
            this.keyColumn = keyColumn;
            this.value = value;
        }
    }

    private Triplet<Object, KeyColumn, Object> invoke(CallFrame frame, boolean partitionKey) {
        KeyColumn keyColumn = null;
        PropertyDescriptor desc = Reflection.propertyDescriptor(frame.getMethod());
        List<KeyColumn> list = partitionKey ? table.getPartitionKeys() : table.getClusteringKeys();
        for (KeyColumn kc : list) {
            String name = kc.getPropertyDescriptor() != null ? kc.getPropertyDescriptor().getName() : kc.getField().getName();
            if (name.equals(desc.getName())) {
                keyColumn = kc;
                break;
            }
        }

        Object arg = null;
        Object ret = null;
        if (frame.getMethod().getReturnType() != void.class) {
            // getter
            ret = Primitives.defaultValue(frame.getMethod().getReturnType());
        } else if (frame.getArguments().length > 0) {
            arg = frame.getArguments()[0];
        }
        return new Triplet<>(ret, keyColumn, arg);
    }

    protected class RelationHandler implements InterceptorHandler {
        KeyColumn keyColumn;
        boolean partitionKey;

        RelationHandler(boolean partitionKey) {
            this.partitionKey = partitionKey;
        }

        @Override
        public Object invoke(CallFrame frame) {
            Triplet<Object, KeyColumn, Object> pair = BaseQuery.this.invoke(frame, partitionKey);
            keyColumn = pair.y();
            return pair.x();
        }
    }

    protected class OrderByHandler implements InterceptorHandler {
        List<KeyColumnValue> keyColumnValues = new ArrayList<>();

        @Override
        public Object invoke(CallFrame frame) {
            Triplet<Object, KeyColumn, Object> triplet = BaseQuery.this.invoke(frame, false);
            keyColumnValues.add(new KeyColumnValue(triplet.y(), triplet.z()));
            return triplet.x();
        }
    }
}
