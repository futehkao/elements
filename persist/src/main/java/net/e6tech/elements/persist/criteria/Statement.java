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

import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.reflection.Reflection;

import javax.persistence.criteria.*;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
public class Statement<T> {
    Where<T> where;
    From<?, T> from;

    public Statement(Where where, From<?, T> from) {
        this.where = where;
        this.from = from;
    }

    public CriteriaBuilder getBuilder() {
        return where.getBuilder();
    }

    public CriteriaQuery<T> getQuery() {
        return where.getQuery();
    }

    public T getTemplate() {
        return where.getTemplate();
    }

    public From<?, T> getFrom() {
        return from;
    }

    public void or(Runnable runnable) {
        Where where = new Where(this.where, this.where.getPath());
        Interceptor.setInterceptorHandler(where.getTemplate(), where);
        where.predicates = new ArrayList<>();
        runnable.run();
        Interceptor.setInterceptorHandler(where.getTemplate(), this.where);

        List<Predicate> predicates = where.getPredicates();
        if (predicates.size() > 0) {
            CriteriaBuilder builder = this.where.getBuilder();
            Predicate predicate = builder.or(predicates.toArray(new Predicate[predicates.size()]));
            this.where.getPredicates().add(predicate);
        }
    }

    public T equalTo() {
        return compare(where.getTemplate(), Comparison.equal);
    }

    public T notEqual() {
        return compare(where.getTemplate(), Comparison.not_equal);
    }

    public T like() {
        return compare(where.getTemplate(), Comparison.like);
    }

    public T in(List list) {
        Class<T> entityClass = Interceptor.getTargetClass(where.getTemplate());
        T t = applyGetter(entityClass, path ->  {
            Predicate predicate = Comparison.in.compare(getBuilder(), path, list);
            where.getPredicates().add(predicate);
        });
        return t;
    }

    public T lessThan() {
        return compare(where.getTemplate(), Comparison.less_than);
    }

    public T lessThanOrEqualTo() {
        return compare(where.getTemplate(), Comparison.less_than_or_equal);
    }

    public T greaterThan() {
        return compare(where.getTemplate(), Comparison.greater_than);
    }

    public T greaterThanOrEqualTo() {
        return compare(where.getTemplate(), Comparison.greater_than_or_equal);
    }

    protected T compare(T template, Comparison comparison) {
        Class<T> entityClass = Interceptor.getTargetClass(template);
        T t = applySetter(entityClass, (path, args) ->  {
            Predicate predicate = comparison.compare(getBuilder(), path, args[0]);
            where.getPredicates().add(predicate);
        });
        return t;
    }

    protected  <R> R applySetter(Class<R> entityClass, BiConsumer<Path, Object[]> consumer) {
        R r = Handler.interceptor.newInstance(entityClass, setter(consumer));
        return r;
    }

    protected <R> R applyGetter(Class<R> entityClass,  Consumer<Path> consumer) {
        R r = Handler.interceptor.newInstance(entityClass, getter(consumer));
        return r;
    }

    protected InterceptorHandler getter(Consumer<Path> consumer) {
        return (target, thisMethod, args) -> {
            PropertyDescriptor desc = Reflection.propertyDescriptor(thisMethod);
            String property = desc.getName();
            if (thisMethod.equals(desc.getReadMethod())) {
                consumer.accept(where.getPath().get(property));
            } else {
                throw new UnsupportedOperationException("Only accepts getter");
            }
            return null;
        };
    }

    private InterceptorHandler setter(BiConsumer<Path, Object[]> consumer) {
        return (target, thisMethod, args) -> {
            PropertyDescriptor desc = Reflection.propertyDescriptor(thisMethod);
            String property = desc.getName();
            if (!thisMethod.equals(desc.getReadMethod())) {
                consumer.accept(where.getPath().get(property), args);
            } else {
                throw new UnsupportedOperationException("Only accepts setter");
            }
            return null;
        };
    }
}

