/*
 * Copyright 2015 Futeh Kao
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

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by futeh.
 */
public class Where<T> extends Handler {

    Where parent;
    T template;
    Comparison comparison = Comparison.equal;
    List<Predicate> predicates = new ArrayList<>();
    List<Order> orderByList = new ArrayList<>();

    public Where(Where parent, Path path) {
        this(parent.getEntityManager(), parent.getBuilder(), parent.getQuery(), path);
        this.parent = parent;
        this.predicates = parent.predicates;
        this.comparison = parent.getComparison();
        this.orderByList = parent.getOrderByList();
    }

    public Where(EntityManager entityManager, CriteriaBuilder builder, CriteriaQuery query, Path path) {
        super(entityManager, builder, query, path);
        template = Handler.interceptor.newInstance(path.getJavaType(), this);
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
    public Object invoke(CallFrame frame) throws Throwable {
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
            return null;
        } else {
            // setter
            Path current = getPath().get(property);
            predicates.add(comparison.compare(builder, current, frame.getArguments()[0]));
            return null;
        }
    }
}
