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

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.mapping.Result;
import net.e6tech.elements.cassandra.Schema;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.MapBuilder;
import net.e6tech.elements.common.util.concurrent.ThreadPool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
    void basic() throws InterruptedException {
        Schema schema = provision.newInstance(Schema.class);
        schema.createTables("elements", TestTable.class);
        List<Long> ids = Arrays.asList(1L, 2L, 3L);

        ThreadPool pool = ThreadPool.fixedThreadPool("test", 50);
        long start = System.currentTimeMillis() + 2000L;

        CountDownLatch latch = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            long id = i;
            pool.execute(() -> {
                provision.open().accept(Sibyl.class, s -> {
                    Thread.currentThread().sleep(start - System.currentTimeMillis());
                    List<TestTable> testTables = s.all(TestTable.class, "select * from test_table where id in :ids",
                           MapBuilder.of("ids", ids));
                    List<TestTable> list = new ArrayList<>();
                    TestTable test = new TestTable();
                    test.setId(id);
                    test.setName("test");
                    list.add(test);
                    s.save(list, TestTable.class).inCompletionOrder();
                    latch.countDown();
                });
            });
        }
        latch.await();
    }
}
