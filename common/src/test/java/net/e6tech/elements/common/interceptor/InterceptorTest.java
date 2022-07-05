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

package net.e6tech.elements.common.interceptor;

import net.e6tech.elements.common.Tags;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by futeh.
 */
@Tags.Common
public class InterceptorTest {

    private int a = 0;

    @Test
    public void basic() throws Exception {
        Interceptor interceptor2 = new Interceptor();
        AtomicReference<Boolean> atomic = new AtomicReference<>();
        TestClass testClass = interceptor2.newInstance(TestClass.class, frame -> {
            System.out.print("Intercepted " + frame.getMethod().getName() + " ... ");
            Nonnull nonnull = frame.getMethod().getAnnotation(Nonnull.class);
            if (frame.getMethod().getName().equals("methodA")) {
                assertTrue(nonnull != null);
            }
            atomic.set(true);
            return frame.invoke();
        });

        atomic.set(false);
        testClass.methodA();
        assertTrue(atomic.get());

        atomic.set(false);
        testClass.methodB("Hello World!");
        assertTrue(atomic.get());

        atomic.set(false);
        testClass.methodC("Hello World!", 10);
        assertTrue(atomic.get());

        atomic.set(false);
        testClass.hashCode();
        assertFalse(atomic.get());

        // testing setting a different handler.
        AtomicReference<Boolean> atomic2 = new AtomicReference<>();
        Interceptor.getInterceptorHandler(testClass);
        Interceptor.setInterceptorHandler(testClass, frame -> {
            System.out.print("New Handler - Intercepted " + frame.getMethod().getName() + " ... ");
            Nonnull nonnull = frame.getMethod().getAnnotation(Nonnull.class);
            if (frame.getMethod().getName().equals("methodA")) {
                assertTrue(nonnull != null);
            }
            atomic2.set(true);
            return frame.invoke();
        });
        atomic2.set(false);
        testClass.methodA();
        assertTrue(atomic2.get());

        atomic2.set(false);
        testClass.methodB("Hello World!");
        assertTrue(atomic2.get());

        atomic2.set(false);
        testClass.methodC("Hello World1", 10);
        assertTrue(atomic2.get());

        // testing getting target class
        Class targetClass = Interceptor.getTargetClass(testClass);
        assertTrue(targetClass.equals(TestClass.class));

        // testing proxy class caching
        Class proxyClass = testClass.getClass();
        testClass = interceptor2.newInstance(TestClass.class, CallFrame::invoke);

        assertTrue(proxyClass == testClass.getClass());

        // test proxy object cloning
        TestClass clone = Interceptor.cloneProxyObject(testClass);
        assertTrue(Interceptor.getTarget(clone) == Interceptor.getTarget(testClass));
        assertTrue(Interceptor.getInterceptorHandler(clone) == Interceptor.getInterceptorHandler(testClass));
        Interceptor.setInterceptorListener(clone, (frame, th) -> { return null; });
        assertTrue(Interceptor.getInterceptorListener(clone) != null);
        assertTrue(Interceptor.getInterceptorListener(clone) != Interceptor.getInterceptorListener(testClass));
    }

    @Test
    public void testPrototype() throws Exception {
        Interceptor interceptor = new Interceptor();
        TestClass prototype = new TestClass();
        prototype.setValue(10);
        Class cls = interceptor.newPrototypeClass(TestClass.class, prototype, null);
        TestClass test = (TestClass) cls.getDeclaredConstructor().newInstance();
        assertTrue(test.getValue() == 10);
        test.setValue(11);
        assertTrue(prototype.getValue() == 10);
        assertTrue(test.getValue() == 11);
    }

    @Test
    public void testSingleton() throws Exception {
        Interceptor interceptor = new Interceptor();
        TestClass singleton = new TestClass();
        singleton.setValue(10);
        Class cls = interceptor.newSingletonClass(TestClass.class, singleton);
        TestClass test = (TestClass) cls.getDeclaredConstructor().newInstance();
        assertTrue(test.getValue() == 10);

        test.setValue(11);
        assertTrue(singleton.getValue() == 11);
        assertTrue(test.getValue() == 11);

        singleton.setValue(12);
        assertTrue(singleton.getValue() == 12);
        assertTrue(test.getValue() == 12);
    }

    @Test
    void testPrivateMethod() throws Exception {
        Interceptor interceptor = new Interceptor();
        TestClass proxy = interceptor.newInstance(TestClass.class, frame -> {
            System.out.println("Intercepted method " + frame.getMethod().getName());
            return frame.invoke();
        });

        proxy.methodD("calling method D");
        proxy.protectedMethod("calling protected method");
    }

    @Test
    void testBootstrapClass() throws Exception {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        XMLGregorianCalendar calendar = DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(zonedDateTime.getYear(), zonedDateTime.getMonthValue(), zonedDateTime.getDayOfMonth(),
                        zonedDateTime.getHour(), zonedDateTime.getMinute(), zonedDateTime.getSecond(), zonedDateTime.getNano() / 1000000,
                        zonedDateTime.getOffset().getTotalSeconds() / 60);
        Interceptor.getInstance().newInterceptor(calendar, frame -> null );
    }

    @Test
    void anonymousClassThreads() throws Exception{
        long start = System.currentTimeMillis();
        anonymousClass();
        System.out.println("" + (System.currentTimeMillis() - start) + "ms");

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            threads.add(new Thread(this::anonymousClass));
        }
        start = System.currentTimeMillis();
        for (Thread thread : threads)
            thread.start();

        for (Thread thread : threads)
            thread.join();
        System.out.println("" + (System.currentTimeMillis() - start) + "ms");
    }

    @Test
    void anonymousClass() {
        X target = new X();
        target.setN(1);
        Random random = new Random();
        int n = random.nextInt();
        Interceptor.getInstance().runAnonymous(target, new X() {{
            setX(n);
            setY(n);
            setZ(n);
            a = getX();
        }});

        assertTrue(target.getX() == n);
       // assertTrue(a == target.getX());
        assertTrue(target.getN() == 1);
    }

    private static class X extends Y {
        private int x;
        private int y;
        private int z;

        public X() {
            super();
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getZ() {
            return z;
        }

        public void setZ(int z) {
            this.z = z;
        }
    }

    private static class Y {
        private int n;

        public Y() {
            setN(10);
        }

        public int getN() {
            return n;
        }

        public void setN(int n) {
            this.n = n;
        }
    }
}
