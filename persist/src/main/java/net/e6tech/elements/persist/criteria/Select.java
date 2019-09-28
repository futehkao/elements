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

package net.e6tech.elements.persist.criteria;

import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.reflection.Reflection;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.EntityType;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public class Select<T> extends Statement<T> {

    private static final String GETTER_MSG = "Only accepts getter";

    private Select parent;
    private int maxResults = -1;
    private int firstResult = -1;
    private List<Selection<?>> selections = new ArrayList<>();
    private Map<String, Object> hints = new HashMap<>();

    public Select(Where where, Root<T> root) {
        super(where, root);
    }

    protected Select(Select parent, Where where, From<T, T> root) {
        super(where, root);
        this.parent = parent;
        this.selections = parent.selections;
    }

    public static <T> Select<T> create(EntityManager entityManager, Class<T> cls) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery query = builder.createQuery();
        Root<T> root = query.from(cls);
        Where<T> where = new Where(entityManager, builder, query, root);
        return new Select<>(where, root);
    }

    public Select<T> hint(String hint, Object value) {
        hints.put(hint, value);
        return this;
    }

    public Select<T> removeHint(String hint) {
        hints.remove(hint);
        return this;
    }

    public <X, R> void addConverter(Class<X> from, Class<R> to, Function<X, R> function) {
        where.addConverter(from, to, function);
    }

    public <X, R> void removeConverter(Class<X> from, Class<R> to) {
        where.removeConverter(from, to);
    }

    public <X, R> T withConverter(Class<X> from, Class<R> to, Function<X, R> function) {
        addConverter(from, to, function);
        return where.getTemplate();
    }

    public Select<T> where(Consumer<T> consumer) {
        consumer.accept(where.getTemplate());
        where.onQuery();
        return this;
    }

    public Select<T> where(T template) {
        Where.interceptor.runAnonymous(null, where.getTemplate(), template);
        where.onQuery();
        return this;
    }

    public Select<T> where(BiConsumer<Select<T>, T> consumer) {
        consumer.accept(this, where.getTemplate());
        where.onQuery();
        return this;
    }

    public <R> R getSingleResult() {
        where.onQuery();
        if (selections.size() == 1) {
            getQuery().select((Selection<? extends T>) selections.get(0));
        } else if (!selections.isEmpty()) {
            getQuery().multiselect(selections);
        } else {
            getQuery().select(getFrom());
        }

        Query query = where.getEntityManager().createQuery(getQuery());
        if (maxResults >= 0)
            query.setMaxResults(maxResults);

        if (firstResult >= 0)
            query.setFirstResult(firstResult);

        setHints(query);
        return (R) query.getSingleResult();
    }

    public <R> List<R> getResultList() {
        where.onQuery();
        if (selections.size() == 1) {
            getQuery().select((Selection<? extends T>) selections.get(0));
        } else if (!selections.isEmpty()) {
            getQuery().multiselect(selections.toArray(new Selection[selections.size()]));
        } else {
            getQuery().select(getFrom());
        }

        Query query = where.getEntityManager().createQuery(getQuery());
        if (maxResults >= 0)
            query.setMaxResults(maxResults);

        if (firstResult >= 0)
            query.setFirstResult(firstResult);

        setHints(query);
        return query.getResultList();
    }

    private void setHints(Query query) {
        for (Map.Entry<String, Object> entry : hints.entrySet())
            query.setHint(entry.getKey(), entry.getValue());
    }

    public Path path(Consumer<T> consumer) {
        Class<T> entityClass = Interceptor.getTargetClass(where.getTemplate());
        AtomicReference<Path> ref = new AtomicReference<>();
        T t = applyGetter(entityClass, ref::set);
        consumer.accept(t);
        return ref.get();
    }

    public Select<T> selectEntity() {
        selections.add(getFrom());
        return this;
    }

    public Select<T> select(Function<CriteriaBuilder, Function<Expression, Expression>> builder, Consumer<T> consumer) {
        Path path = path(consumer);
        select(builder.apply(getBuilder()).apply(path));
        return this;
    }

    public <R> Select<T> select(Expression<R> expression) {
        selections.add(expression);
        return this;
    }

    public Select<T> select(Runnable runnable) {
        Interceptor.setInterceptorHandler(where.getTemplate(), getter(path -> selections.add(path)));
        runnable.run();
        Interceptor.setInterceptorHandler(where.getTemplate(), where);
        return this;
    }

    public Select<T> select(Consumer<T> consumer) {
        Class<T> entityClass = Interceptor.getTargetClass(where.getTemplate());
        T t = applyGetter(entityClass, path -> selections.add(path));
        consumer.accept(t);
        return this;
    }

    public <R> Select<T> crossJoinManyToOneWhere(Class<R> entityClass, Consumer<T> joinCondition, Consumer<R> consumer) {
        return crossJoinManyToOne(entityClass, joinCondition, nestedSelect -> nestedSelect.where(consumer));
    }

    public <R> Select<T> crossJoinManyToOne(Class<R> entityClass, Consumer<T> joinCondition, BiConsumer<Select<R>, R> consumer) {
        return crossJoinManyToOne(entityClass, joinCondition, nestedSelect -> nestedSelect.where(consumer));
    }

    @SuppressWarnings("squid:S1188")
    public <R> Select<T> crossJoinManyToOne(Class<R> entityClass, Consumer<T> joinCondition, Consumer<Select<R>> consumer) {
        From<R, R> jointRoot = getQuery().from(entityClass);
        Interceptor.setInterceptorHandler(where.getTemplate(), frame -> {
            PropertyDescriptor desc = Reflection.propertyDescriptor(frame.getMethod());
            String property = desc.getName();
            if (frame.getMethod().equals(desc.getReadMethod())) {
                Predicate joinPredicate;
                if (getFrom().get(property).getJavaType().equals(jointRoot.getJavaType())) {
                    joinPredicate = getBuilder().equal(getFrom().get(property), jointRoot);
                } else {
                    EntityType type = (EntityType) jointRoot.getModel();
                    String parentIdAttribute = type.getId(type.getIdType().getJavaType()).getName();
                    if (!jointRoot.get(parentIdAttribute).getJavaType().equals(getFrom().get(property).getJavaType())) {
                        throw new IllegalArgumentException("Type mismatch: cannot join " + type.getName() + "." + parentIdAttribute + " to " +
                                getFrom().get(property));
                    }
                    joinPredicate = getBuilder().equal(jointRoot.get(parentIdAttribute), getFrom().get(property));
                }
                this.where.getPredicates().add(joinPredicate);
            } else {
                throw new UnsupportedOperationException(GETTER_MSG);
            }
            return null;
        });
        joinCondition.accept(getTemplate());
        Interceptor.setInterceptorHandler(where.getTemplate(), where);

        Where<R> where = new Where<>(this.where, jointRoot);
        Select<R> joinSelect = new Select<>(this, where, jointRoot);
        consumer.accept(joinSelect);
        return this;
    }

    public <R> Select<T> crossJoinOneToManyWhere(Class<R> entityClass, Consumer<R> joinCondition, Consumer<R> consumer) {
        return crossJoinOneToMany(entityClass, joinCondition, nestedSelect -> nestedSelect.where(consumer));
    }

    public <R> Select<T> crossJoinOneToMany(Class<R> entityClass, Consumer<R> joinCondition, BiConsumer<Select<R>, R> consumer) {
        return crossJoinOneToMany(entityClass, joinCondition, nestedSelect -> nestedSelect.where(consumer));
    }

    @SuppressWarnings("squid:S1188")
    public <R> Select<T> crossJoinOneToMany(Class<R> entityClass, Consumer<R> joinCondition, Consumer<Select<R>> consumer) {
        From<R, R> joinRoot = getQuery().from(entityClass);
        R joinTemplate = Handler.interceptor.newInstance(entityClass,  frame -> {
            PropertyDescriptor desc = Reflection.propertyDescriptor(frame.getMethod());
            String property = desc.getName();
            if (frame.getMethod().equals(desc.getReadMethod())) {
                Predicate joinPredicate;
                if (joinRoot.get(property).getJavaType().equals(getFrom().getJavaType())) {
                    joinPredicate = getBuilder().equal(getFrom(), joinRoot.get(property));
                } else {
                    EntityType type = (EntityType) getFrom().getModel();
                    String parentIdAttribute = type.getId(type.getIdType().getJavaType()).getName();
                    if (!getFrom().get(parentIdAttribute).getJavaType().equals(joinRoot.get(property).getJavaType())) {
                        throw new IllegalArgumentException("Type mismatch: cannot join " + type.getName() + "." + parentIdAttribute + " to " +
                        joinRoot.get(property));
                    }
                    joinPredicate = getBuilder().equal(getFrom().get(parentIdAttribute), joinRoot.get(property));
                }
                this.where.getPredicates().add(joinPredicate);
            } else {
                throw new UnsupportedOperationException(GETTER_MSG);
            }
            return null;
        });
        joinCondition.accept(joinTemplate);

        Where<R> where = new Where<>(this.where, joinRoot);
        Select<R> joinSelect = new Select<>(this, where, joinRoot);
        consumer.accept(joinSelect);
        return this;
    }

    public <R> Select<T> leftJoin(Runnable joinCondition, BiConsumer<Select<R>, R> consumer) {
        return join(JoinType.LEFT, joinCondition, consumer);
    }

    public <R> Select<T> leftJoin(Runnable joinCondition, Consumer<Select<R>> consumer ) {
        return this.<R>join(JoinType.LEFT, joinCondition, (sel, r) -> consumer.accept(sel));
    }

    public Select<T> leftJoin(Runnable joinCondition) {
        return join(JoinType.LEFT, joinCondition, (sel, r) -> { /* no-op */});
    }

    public <R> Select<T> join(Runnable joinCondition, BiConsumer<Select<R>, R> consumer) {
        return join(JoinType.INNER, joinCondition, consumer);
    }

    public <R> Select<T> join(Runnable joinCondition, Consumer<Select<R>> consumer ) {
        return this.<R>join(JoinType.INNER, joinCondition, (sel, r) -> consumer.accept(sel));
    }

    public Select<T> join(Runnable joinCondition) {
        return join(JoinType.INNER, joinCondition, (sel, r) -> { /* no-op */});
    }

    protected <R> Select<T> join(JoinType type, Runnable joinCondition, BiConsumer<Select<R>, R> consumer) {
        Interceptor.setInterceptorHandler(where.getTemplate(), frame -> {
            PropertyDescriptor desc = Reflection.propertyDescriptor(frame.getMethod());
            String property = desc.getName();
            if (frame.getMethod().equals(desc.getReadMethod())) {
                Join join = getFrom().join(property, type);
                Where<R> where = new Where<>(this.where, join);
                Select<R> joinSelect = new Select<>(this, where, join);
                consumer.accept(joinSelect, where.getTemplate());
            } else {
                throw new UnsupportedOperationException(GETTER_MSG);
            }
            return null;
        });
        joinCondition.run();
        Interceptor.setInterceptorHandler(where.getTemplate(), where);
        return this;
    }

    /**
     * fetch is used to load detail objects.  In most case, you probably want to use leftFetch because if
     * the master instance does not have detail instances, it will not be returned whereas for leftFetch the master
     * instance is returned.
     *
     * @param joinCondition runnable to specify the join condition
     * @return Select instance
     */
    public Select<T> fetch(Runnable joinCondition) {
        return fetch(JoinType.INNER, joinCondition);
    }

    /**
     * See description on fetch.
     *
     * @param joinCondition runnable to specify the join condition
     * @return Select instance
     */
    public Select<T> leftFetch(Runnable joinCondition) {
        return fetch(JoinType.LEFT, joinCondition);
    }

    protected Select<T> fetch(JoinType type, Runnable joinCondition) {
        Interceptor.setInterceptorHandler(where.getTemplate(), frame -> {
            PropertyDescriptor desc = Reflection.propertyDescriptor(frame.getMethod());
            String property = desc.getName();
            if (frame.getMethod().equals(desc.getReadMethod())) {
                getFrom().fetch(property, type);
            } else {
                throw new UnsupportedOperationException(GETTER_MSG);
            }
            return null;
        });
        joinCondition.run();
        Interceptor.setInterceptorHandler(where.getTemplate(), where);
        return this;
    }

    public Select<T> setMaxResults(int maxResults) {
        this.maxResults = maxResults;
        if (parent != null)
            parent.setMaxResults(maxResults);
        return this;
    }

    public Select<T> setFirstResult(int firstResult) {
        this.firstResult = firstResult;
        if (parent != null)
            parent.setFirstResult(firstResult);
        return this;
    }

    public void count() {
        selections.add(getBuilder().count(getFrom()));
    }

    public Select<T> asc(Runnable runnable) {
        OrderBy<T> orderBy = new OrderBy(where.getEntityManager(), where.getBuilder(), where.getQuery(), getFrom());
        Interceptor.setInterceptorHandler(where.getTemplate(), orderBy);
        orderBy.desc = false;
        orderBy.orderByList = where.orderByList;
        runnable.run();
        Interceptor.setInterceptorHandler(where.getTemplate(), where);
        return this;
    }

    public Select<T> desc(Runnable runnable) {
        OrderBy<T> orderBy = new OrderBy(where.getEntityManager(), where.getBuilder(), where.getQuery(), getFrom());
        Interceptor.setInterceptorHandler(where.getTemplate(), orderBy);
        orderBy.desc = true;
        orderBy.orderByList = where.orderByList;
        runnable.run();
        Interceptor.setInterceptorHandler(where.getTemplate(), where);
        return this;
    }

    public <N extends Number> Expression<N> sum(String attribute) {
        return getBuilder().sum(where.getPath().get(attribute));
    }

    public <N> Expression coalesce(Expression<N> expression, N value) {
        CriteriaBuilder.Coalesce coalesce = getBuilder().coalesce();
        coalesce.value(expression);
        coalesce.value(value);
        return coalesce;
    }
}


