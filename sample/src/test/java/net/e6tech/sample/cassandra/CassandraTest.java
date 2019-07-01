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

import net.e6tech.elements.cassandra.Schema;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.MapBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CassandraTest {
    public static Provision provision;

    @BeforeAll
    public static void launch() {
        LaunchController controller = new LaunchController();
        controller.launchScript("conf/provisioning/cassandra/boostrap2.groovy")
                .addLaunchListener(p -> provision = p.getInstance(Provision.class))
                .launch();
    }

    @Test
    void basic() {
        Schema schema = provision.newInstance(Schema.class);
        schema.createTables("elements", TestTable.class);
        List<Long> ids = Arrays.asList(1L, 2L);
        List<TestTable> testTables = provision.getInstance(Sibyl.class).all(TestTable.class, "select * from test_table where id in :ids",
                MapBuilder.of("ids", ids));

        Sibyl s = provision.getInstance(Sibyl.class);
        List<TestTable> list = new ArrayList<>();
        TestTable test = new TestTable();
        test.setId(2L);
        test.setName("test");
        list.add(test);
        s.save(list, TestTable.class).inCompletionOrder();
    }
}
