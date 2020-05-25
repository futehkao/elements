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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("squid:S2925")
public class ClusterNodeTest {

    public static ClusterNode create(int port) {
        Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port = " + port + "\n" +
                        "akka.remote.artery.canonical.port = " + port + "\n" +
                        "akka.cluster.seed-nodes = [\"akka://ClusterSystem@127.0.0.1:2551\", \"akka://ClusterSystem@127.0.0.1:2552\"]"
        );

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
    void basic() throws Exception {
        Thread thread = new Thread(() -> {
            try {
                vm1();
            } catch (InterruptedException e) {
                //
            }
        });
        thread.start();

        ClusterNode node = start(2552);
        while (node.getMembers().size() < 2)
            Thread.sleep(100);

        AtomicBoolean gotIt = new AtomicBoolean(false);
        node.getBroadcast().subscribe("test", notice -> {
            System.out.println(node + " " + notice);
            gotIt.set(true);
        });
        int tries = 0;
        while (!gotIt.get() && tries < 20) {
            Thread.sleep(100);
            node.getBroadcast().publish("test2", "Hello world 2!");
            tries ++;
        }
        thread.interrupt();
        assertTrue(gotIt.get());
    }

    @Test
    public void vm1() throws InterruptedException {
        ClusterNode node = start(2551);
        node.getBroadcast().subscribe("test2", notice -> {
            System.out.println(node + " " + notice);
        });

        while (true) {
            Thread.sleep(100L);
            node.getBroadcast().publish("test", "Hello world!");
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
        node.getBroadcast().publish("test2", "Hello world 2!");
        Thread.sleep(2000L);
    }
}
