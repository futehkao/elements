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

package net.e6tech.elements.common.reflection;

import net.e6tech.elements.common.Tags;
import net.e6tech.elements.common.inject.BindPropA;
import net.e6tech.elements.common.inject.BindPropX;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

@Tags.Common
public class LambdaTest {
    @SuppressWarnings("unchecked")
    @Test
    void methodHandle() throws Throwable {
        Constructor constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Integer.TYPE);
        constructor.setAccessible(true);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) constructor.newInstance(BindPropX.class, -1);
        Method method = BindPropX.class.getDeclaredMethod("setA", BindPropA.class);
        Field field = BindPropX.class.getDeclaredField("a");
        field.setAccessible(true);

        MethodHandle mh = lookup.unreflect(method);
        BiConsumer methodLambda = Lambda.reflectSetter(lookup, method);

        BindPropX x = new BindPropX();
        BindPropA a = new BindPropA();

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            x.setA(a);
        }
        System.out.println("Direct invocation " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            method.invoke(x, a);
        }
        System.out.println("Reflection method invoke " + (System.currentTimeMillis() - start) + "ms");

        mh = lookup.unreflectSetter(field);
        mh.invoke(x, a);
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            mh.invoke(x, a);
        }
        System.out.println("MethodHandle invoke " + (System.currentTimeMillis() - start)+ "ms");

        field.set(x, a);
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            field.set(x, a);
        }
        System.out.println("Reflection field access " + (System.currentTimeMillis() - start)+ "ms");

        methodLambda.accept(x, a);
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            methodLambda.accept(x, a);
        }
        System.out.println("LambdaMetaFactory " + (System.currentTimeMillis() - start)+ "ms");

    }
}
