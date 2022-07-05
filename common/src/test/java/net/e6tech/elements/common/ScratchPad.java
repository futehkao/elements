/*
Copyright 2015-2019 Futeh Kao

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
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import net.e6tech.elements.common.inject.BindPropA;
import net.e6tech.elements.common.inject.BindPropX;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.xml.bind.DatatypeConverter;
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
@SuppressWarnings("all")
public class ScratchPad {

    public static class LongEvent {
        private long value;

        public void set(long value) {
            this.value = value;
        }
    }


    @Test
    @SuppressWarnings("unchecked")
    void disruptor() throws Exception {
        // ThreadPool
        ExecutorService pool = Executors.newCachedThreadPool();

        // Specify the size of the ring buffer, must be power of 2.
        // This will create 1024 LongEvents at the very beginning.
        int bufferSize = 1024;

        // Construct the Disruptor
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2);
        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new YieldingWaitStrategy());

        // Connect the handler
        WorkHandler<LongEvent> handler = event -> {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Event: " + event + " Thread: " + Thread.currentThread());
        };
        WorkHandler<LongEvent>[] workers = new WorkHandler[Runtime.getRuntime().availableProcessors() / 2];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = handler;
        }

        disruptor.handleEventsWithWorkerPool(workers)
                .then((event, sequence, endOfBatch) -> {
                    event.set(0);
                });

        // Start the Disruptor, starts all threads running
        disruptor.start();

        // Get the ring buffer from the Disruptor to be used for publishing.
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        System.out.println("Main thread: " + Thread.currentThread());
        for (long l = 0; l < 10; l++) {
            final long x = l;
            ringBuffer.publishEvent((event, sequence, buffer) -> event.set(x));
        }

        synchronized (this) {
            this.wait();
        }
    }

    @Test
    void disruptorPoll() throws Exception {

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 1024;

        // Construct the Disruptor
        Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);

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

    @Test
    void keyManager() throws Exception{
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(null, null);
        KeyManager[] managers = factory.getKeyManagers();
    }

    @Test
    void bcd() {
        byte[] bytes = new byte[] { (byte) 0x99, 0x34};
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append((b & 0x00f0) >>> 4)
                    .append(b & 0x000f);
        }
        System.out.println(builder.toString());
        System.out.println(DatatypeConverter.printHexBinary(bytes));
    }

}
