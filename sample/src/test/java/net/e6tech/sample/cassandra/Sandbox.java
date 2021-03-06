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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.cassandra.annotations.ClusteringColumn;
import net.e6tech.elements.cassandra.annotations.PartitionKey;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.reflection.Signature;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.GenericType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
public class Sandbox {

    @Test
    void test() {
        System.out.println(new String(Base64.getEncoder().encode(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})));
        Map<Signature, Map<Class<? extends Annotation>, Annotation>> annotations = Reflection.getAnnotationsByName(Y.class);
        System.out.println(annotations);
    }

    @Test
    <T> void test2() throws Exception {
        GenericType<List<String>> type = new GenericType<List<String>>() {
        };
        type.getType();
        List<String> list = new ArrayList<>();
        list.add("abc");
        list.add("def");
        ObjectMapper mapper = new ObjectMapper();
        String encoded = mapper.writeValueAsString(list);
        TypeReference<T> ref = new TypeReference<T>() {
            public Type getType() {
                return type.getType();
            }
        };
        list = (List) mapper.readValue(encoded, ref);
        assertTrue(list.size() > 0);
    }

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

    public static class X implements B, D {

        @ClusteringColumn
        protected int m;


        @Override
        public String getB() {
            return null;
        }

        @Override
        public void setB(String b) {

        }

        @Override
        @PartitionKey
        public String getD() {
            return null;
        }

        @Override
        public void setD(String c) {

        }
    }

    public static class Y extends X implements C {

        @PartitionKey
        protected long m;

        @Override
        @PartitionKey
        public String getC() {
            return null;
        }

        @Override
        public void setC(String c) {

        }

        @Inject
        public long getM() {
            return m;
        }

        public void setM(long m) {
            m = m;
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

    interface C extends D {
        String getC();

        void setC(String c);
    }

    interface D {
        @Inject
        String getD();

        void setD(String c);
    }
}
