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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Created by futeh.
 */

@Tags.Common
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

        Z z = new Z();
        z.setType(Z.Type.a);
        Reflection.copyInstance(x, z);
        assertTrue(x.getType() == X.Type.a);
    }

    @Test
    public void copyEnumList() {
        X1 x = new X1();
        x.getTypes().add(X1.Type.a);

        Z1 z = Reflection.newInstance(Z1.class, x);
        assertTrue(z.getTypes().get(0) == Z1.Type.a);
    }

    @Test
    public void copyComplexObject(){
        int id = 1;
        String name = "foo";

        ComplexObject complexObject = new ComplexObject(id, name);
        ComplexObject target = new ComplexObject();

        Reflection.copyInstance(target,complexObject);

        String updateName = "bar";

        assertEquals(target.getId(), id);
        assertTrue(target.getName().equals(name));
        assertTrue(target.getSimpleObject() != null);
        assertEquals(target.getSimpleObject().getId(), id);

        target.setName(updateName);
        target.getSimpleObject().setName(updateName);

        // original object should not be updated
        assertTrue(complexObject.getName().equals(name));
        assertTrue(complexObject.getSimpleObject().getName().equals(name));
        assertTrue(target.getName().equals(updateName));
        assertTrue(target.getSimpleObject().getName().equals(updateName));
    }

    public static class SimpleObject{

        SimpleObject(){}

        SimpleObject(int id, String name){
            this.id = id;
            this.name = name;
        }

        private int id;
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    public static class ComplexObject{

        ComplexObject(){}

        ComplexObject(int id, String name){
            this.id = id;
            this.name = name;
            this.simpleObject = new SimpleObject(id, name);

        }

        private int id;
        private String name;
        private SimpleObject simpleObject;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public SimpleObject getSimpleObject() {
            return simpleObject;
        }

        public void setSimpleObject(SimpleObject simpleObject) {
            this.simpleObject = simpleObject;
        }
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

    public static class Z {
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

    public static class X1 {
        enum Type {
            a, b
        }
        private List<Type> types = new ArrayList<>();

        public List<Type> getTypes() {
            return types;
        }

        public void setTypes(List<Type> types) {
            this.types = types;
        }
    }

    public static class Z1 {
        enum Type {
            a, b
        }
        private List<Type> types = new ArrayList<>();

        public List<Type> getTypes() {
            return types;
        }

        public void setTypes(List<Type> types) {
            this.types = types;
        }
    }

}
