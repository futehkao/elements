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

import net.e6tech.elements.cassandra.etl.PartitionOrderByContext;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.cassandra.etl.Transformer;
import net.e6tech.elements.cassandra.transmutator.Loader;
import net.e6tech.elements.cassandra.transmutator.Transmutator;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Resources;


public class TimeTransmutator extends Transmutator {

    @Loader(0)
    public int load(PartitionOrderByContext context, TimeTable... entries) {
        return context.getProvision().open().apply(Resources.class, resources -> {
            Transformer<DerivedTable, TimeTable> t = new Transformer<>(resources, DerivedTable.class);
            t.transform(entries, (trans, e) ->
                    trans.addPrimaryKey(new PrimaryKey(e.getCreationTime() / 2), e));

            t.forEachCreateIfNotExist((e, a) -> {
                Reflection.copyInstance(a, e);
                a.setValue(a.getValue() + e.getId());
            });

            return t.save()
                    .size();
        });
    }
}
