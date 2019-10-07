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

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by futeh.
 */
public class OrderBy<T> extends Handler {

    boolean desc = true;
    List<Order> orderByList = new ArrayList<>();
    T template;

    public OrderBy(EntityManager entityManager, CriteriaBuilder builder, CriteriaQuery query, Path path) {
        super(entityManager, builder, query, path);
        template = interceptor.newInstance(path.getJavaType(), this);
    }

    public T getTemplate() {
        return template;
    }

    public void setTemplate(T template) {
        this.template = template;
    }

    @Override
    public Object invoke(CallFrame frame) throws Throwable {
        PropertyDescriptor descriptor = Reflection.propertyDescriptor(frame.getMethod());
        String property = descriptor.getName();
        CriteriaBuilder builder = getBuilder();
        if (frame.getMethod().equals(descriptor.getReadMethod())) {
            // getter
            Class cls = frame.getMethod().getReturnType();
            Order order = (this.desc) ? builder.desc(getPath().get(property))
                    : builder.asc(getPath().get(property));
            orderByList.add(order);
            if (!Modifier.isFinal(cls.getModifiers())) {
                OrderBy orderBy = new OrderBy(getEntityManager(), getBuilder(), getQuery(), getPath());
                orderBy.orderByList = orderByList;
                orderBy.desc = this.desc;
                return orderBy.getTemplate();
            }
            if (cls.isPrimitive()) {
                return Primitives.defaultValue(cls);
            }
            return Primitives.defaultValue(descriptor.getPropertyType());
        } else {
            throw new UnsupportedOperationException("Only accepts getter");
        }
    }

    @Override
    public void onQuery() {
        // do nothing
    }
}
