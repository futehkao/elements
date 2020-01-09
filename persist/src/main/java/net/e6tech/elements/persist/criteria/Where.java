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

import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.reflection.Primitives;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.datastructure.Pair;

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class Where<T> extends Handler {

    Where parent;
    T template;
    Comparison comparison = Comparison.equal;
    List<Predicate> predicates = new ArrayList<>();
    List<Order> orderByList = new ArrayList<>();
    Map<Pair, Function> converters = new HashMap<>();

    public Where(Where parent, Path path) {
        this(parent.getEntityManager(), parent.getBuilder(), parent.getQuery(), path);
        this.parent = parent;
        this.predicates = parent.predicates;
        this.comparison = parent.getComparison();
        this.orderByList = parent.getOrderByList();
        this.converters = parent.getConverters();
    }

    public Where(EntityManager entityManager, CriteriaBuilder builder, CriteriaQuery query, Path path) {
        super(entityManager, builder, query, path);
        template = Handler.interceptor.newInstance(path.getJavaType(), this);
    }

    public Map<Pair, Function> getConverters() {
        return converters;
    }

    public void setConverters(Map<Pair, Function> converters) {
        this.converters = converters;
    }

    @SuppressWarnings("unchecked")
    public <X,R> void addConverter(Class<X> fromType, Class<R> toType, Function<X, R> converter) {
        converters.put(new Pair(fromType, toType), converter);
        Class fromPrimitive = Primitives.getPrimitiveType(fromType);
        if (fromPrimitive != null) {
            converters.put(new Pair(fromPrimitive, toType), converter);
        }
        Class toPrimitive = Primitives.getPrimitiveType(toType);
        if (toPrimitive != null) {
            converters.put(new Pair(fromType, toPrimitive), converter);
        }

        if (fromPrimitive != null && toPrimitive != null) {
            converters.put(new Pair(fromPrimitive, toPrimitive), converter);
        }
    }

    public <X,R> void removeConverter(Class<X> fromType, Class<R> toType) {
        converters.remove(new Pair(fromType, toType));
        Class fromPrimitive = Primitives.getPrimitiveType(fromType);
        if (fromPrimitive != null) {
            converters.remove(new Pair(fromPrimitive, toType));
        }
        Class toPrimitive = Primitives.getPrimitiveType(toType);
        if (toPrimitive != null) {
            converters.remove(new Pair(fromType, toPrimitive));
        }

        if (fromPrimitive != null && toPrimitive != null) {
            converters.remove(new Pair(fromPrimitive, toPrimitive));
        }
    }

    public T getTemplate() {
        return template;
    }

    public void setTemplate(T template) {
        this.template = template;
    }

    public Comparison getComparison() {
        return comparison;
    }

    public void setComparison(Comparison comparison) {
        this.comparison = comparison;
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public void setPredicates(List<Predicate> predicates) {
        this.predicates = predicates;
    }

    public List<Order> getOrderByList() {
        return orderByList;
    }

    public void setOrderByList(List<Order> orderByList) {
        this.orderByList = orderByList;
    }

    @Override
    public void onQuery() {
        if (!getPredicates().isEmpty()) {
            getQuery().where(getPredicates().toArray(new Predicate[getPredicates().size()]));
        }
        if (!orderByList.isEmpty())
            getQuery().orderBy(orderByList);
    }

    @Override
    public Object invoke(CallFrame frame) {
        PropertyDescriptor desc = Reflection.propertyDescriptor(frame.getMethod());
        String property = desc.getName();
        CriteriaBuilder builder = getBuilder();
        if (frame.getMethod().equals(desc.getReadMethod())) {
            // getter
            Class cls = frame.getMethod().getReturnType();
            if (!Modifier.isFinal(cls.getModifiers())) {
                Where where = new Where(this, getPath().get(property));
                return where.getTemplate();
            }
            if (cls.isPrimitive()) {
                return Primitives.defaultValue(cls);
            }
            return Primitives.defaultValue(desc.getPropertyType());
        } else {
            // setter
            Path current = getPath().get(property);
            Class javaType = current.getJavaType();
            Object value = frame.getArguments()[0];
            if (value != null) {
                Function function = converters.get(new Pair(value.getClass(), javaType));
                if (function != null)
                    value = function.apply(value);
            }

            predicates.add(comparison.compare(builder, current, value));
            return null;
        }
    }
}
