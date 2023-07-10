/*
 * Copyright 2015-2022 Futeh Kao
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

import net.e6tech.elements.common.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

@Tags.Common
public class ThreadLocalMapTest {

    ThreadLocalMap<String, String> map;

    @BeforeEach
    void setup() {
        map = new ThreadLocalMap<>();
        map.put("A", "a");
        map.put("B", "b");
        map.put("C", "c");
    }

    @Test
    void basic() throws InterruptedException {
        int size = map.size();
        assertEquals(map.mapThreadLocal().size(), size);

        Thread thread = new Thread(() -> {
            assertNotEquals(map.mapThreadLocal().size(), size);
            map.put("A", "a1");
            assertEquals("a1", map.get("A"));
            map.putThreadLocal("B", "b1");
            assertEquals("b1", map.get("B"));
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().equals("C"))
                    e.setValue("c1");
            }
            map.put("D", "d1");
        });
        thread.start();
        thread.join();

        assertEquals("a1", map.get("A"));
        assertEquals("b", map.get("B"));
        assertEquals("c1", map.get("C"));
        assertEquals(map.size(), size + 1);
    }

    @Test
    void merge() throws InterruptedException {
        System.out.println(map.lastUpdate());
        Thread thread = new Thread(() -> {
            System.out.println(map.lastUpdate());
            map.put("A", "a1");
            int size = map.size();
            System.out.println(size);
        });
        thread.start();
        thread.join();
    }

    @Test
    void putThreadLocal() throws InterruptedException {
        Semaphore lock = new Semaphore(1);
        Semaphore lock1 = new Semaphore(1);
        Semaphore lock2 = new Semaphore(1);
        Semaphore end = new Semaphore(2);
        lock.acquire();
        lock1.acquire();
        lock2.acquire();
        end.acquire(2);

        Thread thread = new Thread(() -> {
            map.putThreadLocal("A", "a1");
            map.putThreadLocal("D", "d1");
            try {
                lock2.release();
                lock1.acquire();
                lock.release();
                assertEquals("a1", map.get("A"));
                assertEquals("d1", map.get("D"));
                end.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread thread2 = new Thread(() -> {
            map.putThreadLocal("A", "a2");
            map.putThreadLocal("D", "d2");
            try {
                lock2.acquire();
                lock1.release();
                lock.release();
                assertEquals("a2", map.get("A"));
                assertEquals("d2", map.get("D"));
                end.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        thread.start();
        thread2.start();
        lock.acquire();
        assertEquals("a", map.get("A"));
        end.release(2);
        assertFalse(map.containsKey("D"));
        thread.join();
        thread2.join();
    }

    @Test
    void clearThreadLocal() throws InterruptedException {
        int size = map.size();
        Thread thread = new Thread(() -> {
            map.putThreadLocal("D", "d");
            assertNotEquals(size, map.size());
            map.clearThreadLocal();
            assertEquals(size, map.size());
        });
        thread.start();
        thread.join();

        assertEquals(size, map.size());
    }
}
