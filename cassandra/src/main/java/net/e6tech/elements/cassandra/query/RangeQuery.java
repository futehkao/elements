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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Range query is used to query a range, e.g. creationTime &gt;= x and createTime &lt; y order by creationTime asc.  Keep in
 * mind the inclusion of end points depends on the order by.  Using the previous example, if we were to change it to descending
 * order, the query would look like creationTime &gt; x and createTime &lt;= y order by creationTime desc.
 *
 * The query does its best to limit the result set to the limit size.  In some cases, it may return a list that is slightly larger
 * than the limit size.  This could happen for example when multiple records of the same creationTime span the page boundary.
 *
 * <pre>
 * <code>
 * RangeQuery&lt;X&gt; query = new RangeQuery&lt;&gt;(sibyl, X.class);
 * query.partition(X::setPartitionKey, 1L)
 *      .descending(X::setCreationTime, 0L, 9999999999999L)
 *      .limit(50);
 * </code>
 * </pre>
 *
 * @param <T> entity table type
 */
public class RangeQuery<T> extends BaseQuery<T, RangeQuery<T>> {

    private boolean subQuery;

    public RangeQuery(Sibyl sibyl, Class<T> entityClass) {
        super(sibyl, entityClass);
    }

    public List<T> query() {
        validate();
        List<T> list = select();
        return patchBoundary(list);
    }

    private List<T> patchBoundary(List<T> list) {
        if (!subQuery && !orderBy.isEmpty() && list.size() == limit && limit > 0) {
            T last = list.get(list.size() - 1);
            RangeQuery<T> sub = subQuery(last);
            return merge(list, sub.query(), last);
        }
        return list;
    }

    private RangeQuery<T> subQuery(T last) {
        RangeQuery<T> sub = new RangeQuery<>(sibyl, entityClass);
        sub.subQuery = true;
        sub.partitionRelations = new ArrayList<>(partitionRelations);

        // get a list of order by columns.  use them to filter remove last few potentially same entries
        Set<String> set = new HashSet<>();
        Consumer<Relation> consumer = r -> {
            if (set.contains(r.keyColumn.getName()))
                return;
            Relation subR = new Relation(r.keyColumn, Comparison.EQUAL, r.value);
            sub.clusteringRelations.add(subR);
            set.add(r.keyColumn.getName());
        };
        clusteringRelations.forEach(consumer);
        orderBy.forEach(consumer);

        for (Relation r : sub.clusteringRelations) {
            r.value = r.accessor.get(last);
        }

        return sub;
    }

    protected boolean isRelated(Relation relation, T t, T t2) {
        Object v1 = relation.accessor.get(t);
        Object v2 = relation.accessor.get(t2);
        if (!v1.equals(v2)) {
            return false;
        }
        return true;
    }

    private List<T> merge(List<T> list, List<T> list2, T last) {
        int trimLast = list.size();
        for (int i = list.size() - 1; i >= 0; i --) {
            T t = list.get(i);
            boolean same = true;
            for (Relation relation : orderBy) {
                if (!relation.isRelated(last, t)) {
                    same = false;
                    break;
                }
            }
            if (!same)
                break;
            trimLast = i;
        }
        list = list.subList(0, trimLast);
        list.addAll(list2);
        return list;
    }
}
