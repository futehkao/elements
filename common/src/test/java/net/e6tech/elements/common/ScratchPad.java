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

import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.resources.InjectionListener;
import net.e6tech.elements.common.resources.ResourcePool;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class ScratchPad {
    @Test
    public void scratch() throws Exception {
        String settlementDate = "20150911";
        LocalDate localDate = LocalDate.parse(settlementDate, DateTimeFormatter.BASIC_ISO_DATE);
        ZoneId id = ZoneId.of("UTC").normalized();
        ZonedDateTime time = ZonedDateTime.of(localDate, LocalTime.of(0,0), id);
        System.out.println(time);
        System.out.println(time.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        System.out.println(time.toEpochSecond() * 1000);

        Class l1 = lambda();

        Class l2 = lambda2();

        assertTrue(!l1.equals(l2));

    }

    Class lambda() {
        InjectionListener l = (r) -> {};
        return l.getClass();
    }

    Class lambda2() {
        InjectionListener l = (r) -> {};
        return l.getClass();
    }

    @Test
    public void interceptor() {
        X x = Interceptor.getInstance().newInstance(X.class, (target, method, args)-> {
            return "hello";
        });
        String ret = x.doSomething();
        System.out.println(ret);
    }

    public interface X {
        String doSomething();
    }
}
