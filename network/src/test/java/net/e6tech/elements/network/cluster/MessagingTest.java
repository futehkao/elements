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

package net.e6tech.elements.network.cluster;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * Created by futeh.
 */
public class MessagingTest {

    @Test
    public void simple1() throws Exception {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=2552").withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        new TestKit(system) {{

            Messaging messaging = new Messaging(system);
            messaging.start();

            messaging.subscribe("conversation",(notice)-> {
                    System.out.println(notice);
            });

            messaging.destination("x",(notice)-> {
                    System.out.println(notice);
            });

            Thread.sleep(1000L);

            messaging.publish("conversation","Hello world.");

            messaging.send("x","New world.");

            Thread.sleep(5000L);

            messaging.publish("conversation","Hello world.");

            messaging.shutdown();

            Thread.sleep(5000L);
        }};

    }

    @Test
    public void simple2() throws Exception {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=2551").withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        ActorSystem system = ActorSystem.create("ClusterSystem", config);
        Messaging messaging = new Messaging(system);
        messaging.system = system;
        messaging.start();

        messaging.subscribe("conversation", (notice) -> {
            System.out.println(notice);
        });

        Thread.sleep(30000L);
    }
}
