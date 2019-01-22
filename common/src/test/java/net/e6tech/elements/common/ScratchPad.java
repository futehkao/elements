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

import net.e6tech.elements.common.inject.BindPropA;
import net.e6tech.elements.common.inject.BindPropX;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by futeh.
 */
public class ScratchPad {

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
