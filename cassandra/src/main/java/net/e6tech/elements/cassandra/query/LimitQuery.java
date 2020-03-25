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

import java.util.*;

public class LimitQuery<T> extends BaseQuery<T, LimitQuery<T>> {
    private boolean subQuery;
    protected List<Relation> lastOrderBy = new ArrayList<>();

    public LimitQuery(Sibyl sibyl, Class<T> entityClass) {
        super(sibyl, entityClass);
    }

    public LimitQuery<T> previous(T last) {
        for (Relation r : orderBy) {
            Relation relation = new Relation(r.keyColumn, r.comparison, r.accessor.get(last)); // ascending
            lastOrderBy.removeIf(o -> o.keyColumn.getPosition() == relation.keyColumn.getPosition());
            lastOrderBy.add(relation);

            for (Relation r2 : clusteringRelations) {
                if (r.keyColumn.getPosition() != r2.keyColumn.getPosition())
                    continue;

                if ((r.comparison == Comparison.LESS_THAN && r2.comparison == Comparison.GREATER_THAN_OR_EQUAL) // ascending
                        || (r.comparison == Comparison.GREATER_THAN && r2.comparison == Comparison.LESS_THAN_OR_EQUAL)) { // descending
                    r2.value = relation.value;
                }
            }
        }
        return this;
    }

    public List<T> query() {
        validate();
        lastOrderBy.sort(Comparator.comparingInt(c -> c.keyColumn.getPosition()));
        validateLastOrderBy();

        List<T> duplicates = Collections.emptyList();
        if (!subQuery && !lastOrderBy.isEmpty() && limit > 0) {
            duplicates = subQuery().query();
        }

        int tmp = limit;
        if (limit > 0) {
            limit += duplicates.size();
        }

        List<T> list = select();
        removeDuplicate(list, duplicates);

        limit = tmp;
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    private void validateLastOrderBy() {
        if (lastOrderBy.isEmpty())
            return;
        if (lastOrderBy.size() != orderBy.size())
            throw new IllegalStateException("Last order by size " + lastOrderBy.size() + " does not match order by size " + orderBy.size());

        for (Relation r : lastOrderBy) {
            boolean found = false;
            for (Relation r2 : orderBy) {
                if (r.keyColumn.getPosition() == r2.keyColumn.getPosition()) {
                    r.comparison = r2.comparison;
                    r2.value = r.value;
                    if (r2.value == null)
                        throw new IllegalStateException("The value for last order by column " + r.keyColumn.getName() + " is null.");
                    found = true;
                    break;
                }
            }
            if (!found)
                throw new IllegalStateException("Last order by column " + r.keyColumn.getName() + " not part of order by");

        }
    }

    private LimitQuery<T> subQuery() {
        LimitQuery<T> sub = new LimitQuery<>(sibyl, entityClass);
        sub.subQuery = true;
        sub.partitionRelations = new ArrayList<>(partitionRelations);

        // get a list of order by columns.  use them to filter remove last few potentially same entries
        Set<String> set = new HashSet<>();

        for (int i = 0 ; i < orderBy.size(); i++) {
            Relation o = orderBy.get(i);
            if (set.contains(o.keyColumn.getName()))
                continue;
            Relation r;
            if (i == orderBy.size() - 1) {
                if (o.comparison == Comparison.LESS_THAN) { // ascending
                    r = new Relation(o.keyColumn, Comparison.LESS_THAN_OR_EQUAL, o.value);
                } else {
                    r = new Relation(o.keyColumn, Comparison.GREATER_THAN_OR_EQUAL, o.value);
                }
            } else {
                r = new Relation(o.keyColumn, Comparison.EQUAL, o.value);
            }

            sub.clusteringRelations.add(r);
            set.add(o.keyColumn.getName());
        }

        for (Relation c : clusteringRelations) {
            if (set.contains(c.keyColumn.getName()))
                continue;
            Relation r = new Relation(c.keyColumn, Comparison.EQUAL, c.value);
            sub.clusteringRelations.add(r);
            set.add(c.keyColumn.getName());
        }

        return sub;
    }

    private void removeDuplicate(List<T> list, List<T> duplicates) {
        for (T t : duplicates) {
            Iterator<T> iterator = list.iterator();
            while (iterator.hasNext()) {
                T t2 = iterator.next();
                boolean same = true;
                for (Relation relation : orderBy) {
                    if (!relation.isRelated(t, t2)) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    iterator.remove();
                    break;
                }
            }
        }
    }
}
