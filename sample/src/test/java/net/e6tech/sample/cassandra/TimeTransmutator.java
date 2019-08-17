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

package net.e6tech.sample.cassandra;

import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.AsyncPrepared;
import net.e6tech.elements.cassandra.async.AsyncResultSetFutures;
import net.e6tech.elements.cassandra.etl.PartitionContext;
import net.e6tech.elements.cassandra.etl.PartitionOrderByContext;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.cassandra.etl.Transformer;
import net.e6tech.elements.cassandra.transmutator.Loader;
import net.e6tech.elements.cassandra.transmutator.PartitionLoader;
import net.e6tech.elements.cassandra.transmutator.Transmutator;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.TextBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;


public class TimeTransmutator extends Transmutator {

    @Loader(0)
    public int load(PartitionOrderByContext context, TimeTable... entries) {
        return context.open().apply(Resources.class, resources -> {
            Transformer<DerivedTable, TimeTable> t = new Transformer<>(resources, DerivedTable.class);
            t.transform(entries, (trans, e) ->
                    trans.addPrimaryKey(new PrimaryKey(e.getCreationTime() / 2, e.getId()), e));

            t.forEachNewOrExisting((e, a) -> {
                    Reflection.copyInstance(a, e);
                    a.setValue(e.getId());
                    a.setId(e.getId());
                },
                    (e, a) -> a.setValue(a.getValue() + e.getId()));
            return t.save()
                    .size();
        });
    }

    @PartitionLoader(value = 1, sourceClass = DerivedTable.class)
    public int load(PartitionContext context, Long ... entries) {
        List<ReduceTable> list = new LinkedList<>();
        context.open().accept(Sibyl.class, sibyl -> {

            String aggregate = "sum(value)";
            String partitionKey = context.getInspector().getPartitionKeyColumn(0);
            String table = context.tableName();
            // select distinct only works when selecting partition keys
            String query = TextBuilder.using(
                    "select ${pk}, ${aggregate} from ${table} where ${pk} = :pk group by ${pk}")
                    .build("pk", partitionKey, "table", table, "sum", aggregate);

            sibyl.<Long>createAsync("select creation_time, sum(value) from derived_table where creation_time = :pk group by creation_time ")
                    .execute(entries, (p, bound) ->  bound.set("pk", p, Long.class))
            .inExecutionOrderRows(row -> {
                Long creationTime = row.get(0, Long.class);
                Long sum = row.get(1, Long.class);
                ReduceTable reduce = new ReduceTable();
                reduce.setCreationTime(creationTime);
                reduce.setSum(sum);
                list.add(reduce);
            });

            sibyl.save(list, ReduceTable.class);

        });
        return entries.length;
    }
}
