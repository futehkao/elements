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

package net.e6tech.elements.common.actor.pool;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.resources.Resources;
import org.junit.jupiter.api.Test;

/**
 * Created by futeh.
 */
@SuppressWarnings("all") // this is a test.
public class WorkerPoolTest {

    @Test
    public void workers() throws Exception {

        // Create an Akka system
        Genesis genesis = new Genesis();
        genesis.setName("Genesis");
        genesis.initialize((Resources) null);

        ActorRef pool = genesis.getWorkerPool();

        for (int i = 0; i < 5; i++) {
            final int id = i;
            Patterns.ask(pool, (Runnable) () -> {
                    System.out.println("message " + id);
                    try {
                        Thread.sleep(200L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }, 500L);
        }

        Thread.sleep(1000L);

        for (int i = 0; i < 5; i++) {
            final int id = i;
            Patterns.ask(pool, new Runnable() {
                @Override
                public void run() {
                    System.out.println("message " + id);
                    try {
                        Thread.sleep(200L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, 500L);
        }

        Thread.sleep(20000L);
    }
}
