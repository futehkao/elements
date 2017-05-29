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

package net.e6tech.elements.network.cluster.simple;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;

/**
 * Created by futeh.
 */
public class SimpleClusterApp2 {
    public static void main(String[] args) {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/network/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=2551").withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        // Create an actor that handles cluster domain events
        system.actorOf(Props.create(SimpleActor.class, () -> {
                    return new SimpleActor();
                }),
                "simpleActor");

        system.actorOf(Props.create(SimpleJob.class, () -> {
                    return new SimpleJob();
                }),
                "simpleJob2");

    }
}
