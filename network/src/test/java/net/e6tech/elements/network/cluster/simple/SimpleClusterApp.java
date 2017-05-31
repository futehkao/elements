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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleClusterApp {

    public static void main(String[] args) {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/network/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=2552").withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        // Create an actor that handles cluster domain events
        final ActorRef job = system.actorOf(Props.create(SimpleJob.class), "simpleJob");
        final FiniteDuration interval = Duration.create(5, TimeUnit.SECONDS);
        final Timeout timeout = new Timeout(Duration.create(10, TimeUnit.SECONDS));
        final ExecutionContext ec = system.dispatcher();
        final AtomicInteger counter = new AtomicInteger();

        system.scheduler().schedule(interval, interval, new Runnable() {
            public void run() {
                Future<Object> future = Patterns.ask(job, new SimpleMessage("job"), timeout);
                CompletionStage<Object> stage = FutureConverters.toJava(future);
                stage.thenAccept(result -> {
                    System.out.println(result);
                }).exceptionally(error -> {
                    System.out.println("Failed: " + error.getMessage());
                    return null;
                });

                /*
                future.onSuccess(new OnSuccess<Object>() {
                    public void onSuccess(Object result) {
                        System.out.println(result);
                    }
                }, ec);
                future.onFailure(new OnFailure() {
                    @Override
                    public void onFailure(Throwable throwable) throws Throwable {
                        System.out.println("Failed: " + throwable.getMessage());
                    }
                }, ec); */
            }
        }, ec);
    }
}
