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

package net.e6tech.elements.common.util.concurrent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("squid:S2925")
public class DisruptorPoolTest {

    @Test
    void basic() throws Exception {
        DisruptorPool pool = new DisruptorPool();
        pool.start();

        pool.run(() -> {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("run asynchronously done");
        });

        pool.runAsync(() -> {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("run asynchronously 2 done");
        });

        pool.run(() -> {
            System.out.println("run done");
        }).complete(1000L);

        int ret = pool.call(() -> {
            System.out.println("call done");
            return 1;
        }).complete(1000L);

        assertEquals(ret, 1);

        Assertions.assertThrows(TimeoutException.class,
                () -> {
                    pool.call(() -> {
                        Thread.sleep(200L);
                        return 1;
                    }).complete(50L);
        });


        Thread.sleep(2000L); // wait for runAsync
    }
}
