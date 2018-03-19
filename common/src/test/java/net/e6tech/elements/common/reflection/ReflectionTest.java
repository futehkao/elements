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

import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Created by futeh.
 */
public class ReflectionTest {

    @Test
    public void callingClass() {
        Runnable runnable = () -> {
            Class cls = Reflection.getCallingClass();
            assertTrue(this.getClass().equals(ReflectionTest.class));
        };
        runnable.run();
    }

    @Test
    public void copyEnum() {
        Y y = new Y();
        y.setType("a");
        X x = new X();
        Reflection.copyInstance(x, y);
        assertTrue(x.getType() == X.Type.a);

        x.setType(X.Type.b);
        Reflection.copyInstance(y, x);
        assertTrue(y.getType().equals("b"));
    }

    public static class X {
        enum Type {
            a, b
        }
        private Type type;

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }
    }

    public static class Y {
        String type;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
