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

package net.e6tech.elements.common.reflection;

import net.e6tech.elements.common.Tags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tags.Common
public class AccessorsTest {

    @Test
    void basic() {
        Accessors<Accessor> accessors = Accessors.simple(Y.class);
        Y y = new Y();
        accessors.set(y, "a", 1);
        assertEquals(accessors.get(y, "a"), 1);

        accessors.set(y, "b", 2);
        assertEquals(accessors.get(y, "b"), 2);

        accessors.set(y, "c", "string");
        assertEquals(accessors.get(y, "c"), "string");

        accessors.set(y, "d", 1);
        assertEquals(accessors.get(y, "d"), 1);

        accessors.set(y, "e", 1);
        assertEquals(accessors.get(y, "e"), 1);
    }

    public static class X {
        private int a;
        private Integer b;
        private String c;
        private int d;
        private int e;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public Integer getB() {
            return b;
        }

        public void setB(Integer b) {
            this.b = b;
        }

        public String getC() {
            return c;
        }

        public void setC(String c) {
            this.c = c;
        }

        // No setter
        public int getD() {
            return d;
        }

        // No getter
        public void setE(int e) {
            this.e = e;
        }
    }

    public static class Y extends X {
        private int a;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }
    }
}
