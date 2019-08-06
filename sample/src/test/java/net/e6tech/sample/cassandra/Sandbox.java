/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.sample.cassandra;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.reflection.Accessor;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class Sandbox {

    @Test
    void sessionProvider4() {
        DriverConfigLoader loader =
                DriverConfigLoader.programmaticBuilder()
                        .withString(DefaultDriverOption.REQUEST_TIMEOUT, "5 seconds")
                        .withString(DefaultDriverOption.CONNECTION_MAX_REQUESTS, "32768")
                        .withString(DefaultDriverOption.SOCKET_KEEP_ALIVE, "true")
                        .build();

        Duration duration = loader.getInitialConfig().getDefaultProfile().getDuration(DefaultDriverOption.REQUEST_TIMEOUT);
        System.out.println(duration);
    }

    @Test
    void basic() throws Exception {

        Class cls = Y.class;

        LinkedHashSet<Class> set = new LinkedHashSet<>();
        while (cls != null && cls != Object.class) {
            for (Class c : cls.getInterfaces())
                set.add(c);
            cls = cls.getSuperclass();
        }

        Map<String, Map<Class<? extends Annotation>, Annotation>> annotations = new HashMap<>(50);
        for (Class c : set) {
            for (PropertyDescriptor desc : Introspector.getBeanInfo(c).getPropertyDescriptors()) {
                Map<Class<? extends Annotation>, Annotation> map = Accessor.getAnnotations(desc);
                annotations.put(desc.getName(), map);
            }
        }
    }

    public static class X implements B {

        @Override
        public String getB() {
            return null;
        }

        @Override
        public void setB(String b) {

        }
    }

    public static class Y extends X implements A {

        @Override
        public int getA() {
            return 0;
        }

        @Override
        public void setA(int a) {

        }

        @Override
        public String getC() {
            return null;
        }

        @Override
        public void setC(String c) {

        }
    }

    interface A extends B, C {
        int getA();
        void setA(int a);
    }

    interface B {
        String getB();

        @Inject
        void setB(String b);
    }

    interface C {
        String getC();
        void setC(String c);
    }
}
