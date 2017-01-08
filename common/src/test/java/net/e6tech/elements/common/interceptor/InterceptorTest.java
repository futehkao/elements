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

import org.junit.Test;

import javax.annotation.Nonnull;

/**
 * Created by futeh.
 */
public class InterceptorTest {

    @Test
    public void basic() throws Exception {
        Interceptor interceptor2 = new Interceptor();
        TestClass testClass = interceptor2.newInstance(TestClass.class, (target,  thisMethod,  args)-> {
            System.out.print("Intercepted " + thisMethod.getName() + " ... ");
            Nonnull nonnull = thisMethod.getAnnotation(Nonnull.class);
            return thisMethod.invoke(target, args);
        });

        testClass.methodA();
        testClass.methodB("Hello World!");
        testClass.methodC("Hello World1", 10);

        Interceptor.getInterceptorHandler(testClass);
        Interceptor.setInterceptorHandler(testClass, (target,  thisMethod,  args)-> {
            System.out.print("New Handler - Intercepted " + thisMethod.getName() + " ... ");
            Nonnull nonnull = thisMethod.getAnnotation(Nonnull.class);
            return thisMethod.invoke(target, args);
        });

        testClass.methodA();
        testClass.methodB("Hello World!");
        testClass.methodC("Hello World1", 10);

        Interceptor.getTargetClass(testClass);
    }

    public static class TestClass {

        @Nonnull
        public void methodA() {
            System.out.println("methodA");
        }


        public String methodB(String arg) {
            System.out.println("methodB: " + arg);
            return arg;
        }

        public String methodC(String arg, int arg2) {
            System.out.println("methodC: " + arg + ", "+ arg2);
            return arg;
        }
    }
}
