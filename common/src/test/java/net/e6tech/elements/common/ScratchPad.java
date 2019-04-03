/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.common;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import net.e6tech.elements.common.inject.BindPropA;
import net.e6tech.elements.common.inject.BindPropX;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by futeh.
 */
public class ScratchPad {

    public static class LongEvent {
        private long value;

        public void set(long value) {
            this.value = value;
        }
    }

    @Test
    void disruptor() throws Exception {
        // ThreadPool
        ExecutorService pool = Executors.newCachedThreadPool();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 1024;

        // Construct the Disruptor
        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);

        // Connect the handler
        /* disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            pool.submit(() -> {
                System.out.println("Event: " + event + " Thread: " + Thread.currentThread());
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }); */


        // Start the Disruptor, starts all threads running
        disruptor.start();

        // Get the ring buffer from the Disruptor to be used for publishing.
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
        EventPoller<LongEvent> poller = ringBuffer.newPoller();

        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    poller.poll((event, sequence, endOfBatch) -> {
                        System.out.println("Event: " + event + " Thread: " + Thread.currentThread());
                        return true;
                    });
                    Thread.sleep(10L);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();

        System.out.println("Main thread: " + Thread.currentThread());
        ByteBuffer bb = ByteBuffer.allocate(8);
        for (long l = 0; l < 10; l++) {
            bb.putLong(0, l);
            ringBuffer.publishEvent((event, sequence, buffer) -> event.set(buffer.getLong(0)), bb);
        }

        synchronized (this) {
            this.wait();
        }
    }

    @Test
    void scratch() throws Exception {
        System.out.println(StandardCharsets.UTF_8.name());
        String settlementDate = "20150911";
        LocalDate localDate = LocalDate.parse(settlementDate, DateTimeFormatter.BASIC_ISO_DATE);
        ZoneId id = ZoneId.of("UTC").normalized();
        ZonedDateTime time = ZonedDateTime.of(localDate, LocalTime.of(0,0), id);
        System.out.println(time);
        System.out.println(time.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        System.out.println(time.toEpochSecond() * 1000);
    }

    @Test
    void methodHandle() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = BindPropX.class.getDeclaredMethod("setA", BindPropA.class);
        MethodHandle mh = lookup.unreflect(method);

        BindPropX x = new BindPropX();
        BindPropA a = new BindPropA();

        mh.invoke(x, a);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            mh.invoke(x, a);
        }
        System.out.println("method handle invoke " + (System.currentTimeMillis() - start));

        method.invoke(x, a);
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            method.invoke(x, a);
        }
        System.out.println("reflection " + (System.currentTimeMillis() - start));

        Field field = BindPropX.class.getDeclaredField("a");
        field.setAccessible(true);

        mh = lookup.unreflectSetter(field);
        mh.invoke(x, a);
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            mh.invoke(x, a);
        }
        System.out.println("method handle setter " + (System.currentTimeMillis() - start));

        field.set(x, a);
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            field.set(x, a);
        }
        System.out.println("reflection " + (System.currentTimeMillis() - start));
    }
}
