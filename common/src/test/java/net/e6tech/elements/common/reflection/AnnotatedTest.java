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

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.*;

public class AnnotatedTest {

    @Test
    public void basic() {
        X x = new X();
        x.setV1("Some random stuff");
        Annotated.Accessor<A, String, String> accessor = Annotated.accessor(x, A.class, A::value, String.class);

        String value = accessor.get("V1");
        assertEquals(value, x.getV1());

        accessor.set("V1", "Some value");
        assertEquals("Some value", x.getV1());

        assertNull(accessor.get("V3"));

        x.setV2("This is V2");
        value = accessor.get("V2");
        assertEquals(value, x.getV2());

        Annotated.Accessor<A, E, Integer> accessor2 = Annotated.accessor(x, A.class, A::e, Integer.TYPE);
        x.setE(1);
        int n = accessor2.get(E.a);
        assertEquals(n, x.getE());

        Map<String, ?> map = accessor.get("V1", "V2");
        assertEquals(map.get("V1"), x.getV1());
        assertEquals(map.get("V2"), x.getV2());

        Map<String, String> map2 = accessor.getAll();
        assertTrue(map2.size() == 2);

        Annotated.Accessor<A, String, ?> accessor3 = Annotated.accessor(x, A.class, A::value, null);
        assertTrue(accessor3.getAll().size() == 3);

        Annotated.Accessor<A, String, ?> accessor4 = Annotated.accessor(x, A.class, A::value, null);
        assertTrue(accessor4.getAll().size() == 3);
    }

    @Target({ METHOD, FIELD })
    @Retention(RUNTIME)
    public @interface A {
        String value() default "";
        E e() default E.a;
    }

    public enum E {
        a, b, c
    }

    public static class X {
        private String v1;

        @A("V2")
        private String v2;

        private int e;

        @A("V1")
        public String getV1() {
            return v1;
        }

        public void setV1(String v1) {
            this.v1 = v1;
        }

        public String getV2() {
            return v2;
        }

        public void setV2(String v2) {
            this.v2 = v2;
        }

        @A(e = E.a)
        public int getE() {
            return e;
        }

        public void setE(int e) {
            this.e = e;
        }
    }
}
