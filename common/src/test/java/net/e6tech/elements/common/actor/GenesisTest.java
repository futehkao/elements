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

package net.e6tech.elements.common.actor;

import net.e6tech.elements.common.resources.Resources;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by futeh.
 */
public class GenesisTest {

    @Test
    public void runnable() throws Exception {
        Genesis genesis = new Genesis();
        genesis.setName("Genesis");
        genesis.initialize((Resources) null);

        for (int i = 0; i < 100; i++){
            final int id = i;
            AtomicLong start = new AtomicLong(0L);
            genesis.async(() -> {
                System.out.println("Genesis " + id + " " + System.currentTimeMillis());
                System.out.println("Request: " + id + " " + Thread.currentThread());
                start.set(System.currentTimeMillis());
            }, 1000L).thenRunAsync(() -> {
                System.out.println("Response: " + id + " " + Thread.currentThread());
                System.out.println("Done Genesis " + id + " " + (System.currentTimeMillis() - start.get()) + "ms");
            });
        }
        System.out.println("finished sending");

        Thread.sleep(2000L);
    }

    @Test
    public void callable() throws Exception {
        Genesis genesis = new Genesis();
        genesis.setName("Genesis");
        genesis.initialize((Resources) null);

        for (int i = 0; i < 100; i++){
            final int id = i;
            genesis.async(() -> {
                System.out.println("Genesis " + id + " " + System.currentTimeMillis());
                System.out.println("Request: " + id + " " + Thread.currentThread());
                return System.currentTimeMillis();
            }, 1000L).thenAccept((start) -> {
                System.out.println("Response: " + id + " " + Thread.currentThread());
                System.out.println("Done Genesis " + id + " " + (System.currentTimeMillis() - start) + "ms");
            });
        }
        System.out.println("finished sending");

        Thread.sleep(2000L);
    }
}
