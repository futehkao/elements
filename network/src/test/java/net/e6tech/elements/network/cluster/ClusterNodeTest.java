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

package net.e6tech.elements.network.cluster;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.network.cluster.catalyst.Reactor;
import net.e6tech.elements.network.cluster.invocation.Registry;
import org.junit.jupiter.api.Test;

import java.io.File;

public class ClusterNodeTest {

    public static ClusterNode create(int port) {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString(
                "akka { remote { \n " +
                        "netty.tcp.port=" + port + "\n" +
                        "artery.canonical.port=" + port + "\n" +
                        "} }"
        )
                .withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        Genesis genesis = new Genesis();
        genesis.setName("ClusterSystem");
        genesis.initialize(config);
        ClusterNode clusterNode = new ClusterNode();
        clusterNode.initialize(genesis);
        return clusterNode;
    }

    private void register(ClusterNode clusterNode) {
        Registry registry = clusterNode.getRegistry();
        registry.register("blah", Reactor.class, new Reactor() {
        });
    }

    private ClusterNode start(int port) {
        ClusterNode node = create(port);
        Registry registry = node.getRegistry();
        registry.register("blah", Reactor.class, new Reactor() {
        });
        return node;
    }


    @Test
    public void vm1() throws Exception {
        ClusterNode node = start(2551);
        node.getBroadcast().subscribe("test", notice -> {
            System.out.println(node + " " + notice);
        });
        Thread.sleep(2000L);
        node.getBroadcast().publish("test", "Hello world!");

        synchronized (this) {
            wait();
        }
    }

    @Test
    void messaging() throws InterruptedException {
        ClusterNode node = start(2552);
         while (node.getMembers().size() < 2)
             Thread.sleep(100);
        node.getBroadcast().subscribe("test", notice -> {
            System.out.println(node + " " + notice);
        });

        Thread.sleep(2000L);
        node.getBroadcast().publish("test", "Hello world!");
        Thread.sleep(2000L);
    }
}
