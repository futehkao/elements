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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Created by futeh.
 */
public enum Comparison {
    equal {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.equal(expression, object);
        }
    },
    not_equal {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.notEqual(expression, object);
        }
    },
    less_than {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.lessThan(expression, (Comparable) object);
        }
    },
    less_than_or_equal {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.lessThanOrEqualTo(expression, (Comparable) object);
        }
    },
    greater_than {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.greaterThan(expression, (Comparable) object);
        }
    },
    greater_than_or_equal {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.greaterThanOrEqualTo(expression, (Comparable) object);
        }
    },
    like {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.like(expression, (String) object);
        }
    },
    in {
        Predicate compare(CriteriaBuilder builder, Expression expression, Object object) {
            return builder.in(expression).value(object);
        }
    };
    abstract Predicate compare(CriteriaBuilder builder, Expression expression, Object object);
}
