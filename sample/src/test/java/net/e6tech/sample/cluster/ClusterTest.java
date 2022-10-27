/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.sample.cluster;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.network.cluster.ClusterNode;
import net.e6tech.sample.BaseCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S2925")
public class ClusterTest extends BaseCase {
    @Test
    public void broadcast() throws Exception {
        ConfigFactory.defaultApplication();
        ClusterNode cluster = provision.getBean(ClusterNode.class);
        NotificationCenter center = provision.getResourceManager().getNotificationCenter();
        center.subscribe("test", (notice) -> {
            System.out.println(notice);
        });

        Thread.sleep(2000L);
        center.publish(new Notice<>("test", "Hello world!"));
        Config config = cluster.getGenesis().getConfig();
        assertEquals(config.getStringList("akka.cluster.seed-nodes").get(0), "akka://h3_cluster@127.0.0.1:2552");
        assertEquals(config.getString("akka.remote.artery.canonical.hostname"), "127.0.0.1");
    }
}
