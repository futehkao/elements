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

import net.e6tech.elements.common.actor.GenesisActor;
import net.e6tech.elements.common.actor.typed.worker.WorkEvents;
import net.e6tech.elements.common.resources.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
@SuppressWarnings("all") // this is a test.
public class WorkerPoolTest {

    @Test
    public void workers() throws Exception {

        // Create an Akka system
        GenesisActor genesis = new GenesisActor();
        genesis.setProfile("local");
        genesis.setName("Genesis");
        genesis.getWorkPoolConfig().setInitialCapacity(2);
        genesis.getWorkPoolConfig().setMaxCapacity(50);
        genesis.getWorkPoolConfig().setIdleTimeout(2000L);

        genesis.initialize((Resources) null);

        for (int i = 0; i < 100; i++) {
            final int id = i;
            genesis.async(() -> {
                System.out.println("message " + id);
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, 500L);

            if (i == 80) {
                WorkEvents.StatusResponse response = genesis.getGuardian().getWorkerPool().status(new WorkEvents.Status());
                assertEquals(response.getWorkerCount(), genesis.getWorkPoolConfig().getMaxCapacity());
            }
        }

        WorkEvents.StatusResponse response = genesis.getGuardian().getWorkerPool().status(new WorkEvents.Status());

        Thread.sleep(5000L);

        response = genesis.getGuardian().getWorkerPool().status(new WorkEvents.Status());
        assertEquals(response.getIdleCount(), genesis.getWorkPoolConfig().getInitialCapacity());
        assertEquals(response.getWorkerCount(), genesis.getWorkPoolConfig().getInitialCapacity());

        for (int i = 0; i < 5; i++) {
            final int id = i;
            genesis.async(() -> {
                System.out.println("message " + id);
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, 500L);
        }

        Thread.sleep(2100L);
    }
}
