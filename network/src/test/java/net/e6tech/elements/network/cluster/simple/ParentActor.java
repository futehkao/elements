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

import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class demonstrate how to process using child actor.  Assuming the task is blocking, the
 * proper way to create an child actor to process the request and then kill the child.
 *
 * Created by futeh.
 */
public class ParentActor extends AbstractActor {

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
        .match(SimpleMessage.class, message -> {
            ActorRef child = getContext().actorOf(Props.create(ChildActor.class));
            getContext().watch(child);
            child.forward(message, getContext());
            child.tell(akka.actor.PoisonPill.getInstance(), ActorRef.noSender());
        })
        .match(Terminated.class, terminated -> {
            System.out.println("Child " + terminated.getActor() + " terminated");
        })
        .build();
    }

    public static void main(String[] args) {
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + "/network/src/test/resources/akka.conf");
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=2552").withFallback(ConfigFactory.parseFile(file));

        // Create an Akka system
        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        // Create an actor that handles cluster domain events
        final ActorRef parent = system.actorOf(Props.create(ParentActor.class), "parent");
        final FiniteDuration interval = Duration.create(5, TimeUnit.SECONDS);
        final Timeout timeout = new Timeout(Duration.create(10, TimeUnit.SECONDS));
        final AtomicInteger counter = new AtomicInteger(0);

        system.scheduler().schedule(interval, interval, new Runnable() {
            public void run() {
                int no =  counter.incrementAndGet();
                Future<Object> future = Patterns.ask(parent, new SimpleMessage("message" + no), timeout);
                CompletionStage<Object> stage = FutureConverters.toJava(future);
                stage.thenAccept(result -> {
                    System.out.println(result);
                }).exceptionally(error -> {
                    System.out.println("Failed: " + error.getMessage());
                    return null;
                });
            }
        }, system.dispatcher());
    }

    public static class ChildActor extends AbstractActor {
        @Override
        public AbstractActor.Receive createReceive() {
            return receiveBuilder()
                    .match(SimpleMessage.class, message -> {
                        getSender().tell(message, getSelf());
                    }).build();
        }
    }
}
