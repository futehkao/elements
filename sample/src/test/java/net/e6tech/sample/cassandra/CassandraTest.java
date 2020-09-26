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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import net.e6tech.elements.cassandra.Schema;
import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.driver.v4.SessionV4;
import net.e6tech.elements.cassandra.etl.PartitionContext;
import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
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
        schema.createTables("elements", TimeTable.class);
        schema.createTables("elements", DerivedTable.class);
        schema.createTables("elements", ReduceTable.class);
        List<Long> ids = Arrays.asList(1L, 2L, 3L);

        ThreadPool pool = ThreadPool.fixedThreadPool("test", 50);

        int threads = 5;
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            long id = i;
            pool.execute(() -> {
                provision.open().accept(Resources.class, Sibyl.class, (resources, s) -> {
                    SessionV4 v4 = (SessionV4) resources.getInstance(Session.class);
                    CqlSession cql = v4.unwrap();
                    PreparedStatement pstmt = cql.prepare("select * from time_table where creation_time in :ids ");
                    BoundStatement bound = pstmt.bind();
                    bound = bound.setList("ids", ids, Long.class);
                    ResultSet rs = cql.execute(bound);

                    List<TimeTable> testTables = s.all(TimeTable.class, "select * from time_table where creation_time in :ids",
                            MapBuilder.of("ids", ids));
                    List<TimeTable> list = new ArrayList<>();
                    TimeTable test = new TimeTable();
                    test.setCreationTime(System.currentTimeMillis());
                    test.setId(id);
                    test.setName("test");
                    list.add(test);
                    s.save(list, TimeTable.class, null);
                    latch.countDown();
                });
            });
        }
        latch.await();

        PartitionContext context = provision.newInstance(PartitionContext.class);
        context.setStartTime(System.currentTimeMillis());
        context.setBatchSize(100);
        context.setExtractAll(true);
        context.setTimeLag(0);
        new TimeTransmutator().run(context);
    }
}
