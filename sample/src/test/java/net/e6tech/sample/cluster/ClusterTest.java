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

import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.network.cluster.ClusterNode;
import net.e6tech.sample.BaseCase;
import org.junit.jupiter.api.Test;

/**
 * Created by futeh.
 */
public class ClusterTest extends BaseCase {
    @Test
    public void broadcast() throws Exception {
        ClusterNode cluster = provision.getBean(ClusterNode.class);
        NotificationCenter center = provision.getResourceManager().getNotificationCenter();
        center.subscribe("test", (notice) -> {
            System.out.println(notice);
        });

        Thread.sleep(2000L);
        center.publish("test", "Hello world!");
        Thread.sleep(2000L);
    }
}
