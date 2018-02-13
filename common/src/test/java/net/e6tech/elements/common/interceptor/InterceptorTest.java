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

import javax.annotation.Nonnull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by futeh.
 */
public class InterceptorTest {

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
        Class cls = interceptor.newPrototypeClass(TestClass.class, prototype);
        TestClass test = (TestClass) cls.newInstance();
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
        TestClass test = (TestClass) cls.newInstance();
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
}
