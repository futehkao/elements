/*
 * Copyright 2015-2020 Futeh Kao
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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectPoolTest {

    static ObjectPool<X> objectPool = new ObjectPool<X>(){}.idleTimeout(1000).build();

    @SuppressWarnings("squid:S2925")
    @Test
    void basic() throws InterruptedException {
        List<X> list = new ArrayList<>();
        for (int i = 0 ; i < 10; i++) {
            list.add(objectPool.checkOut());
        }
        list.forEach(x -> objectPool.checkIn(x));
        list.clear();
        Thread.sleep(900);
        for (int i = 0 ; i < 4; i++) {
            list.add(objectPool.checkOut());
        }
        Thread.sleep(100);
        list.forEach(x -> objectPool.checkIn(x));
        assertEquals(4, objectPool.size());

    }

    static class X {
    }
}
