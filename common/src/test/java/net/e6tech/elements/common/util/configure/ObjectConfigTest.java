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

package net.e6tech.elements.common.util.configure;

import net.e6tech.elements.common.Tags;
import net.e6tech.elements.common.resources.Configuration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags.Common
public class ObjectConfigTest {

    public Map load(String configStr) {
        String text = configStr;
        Yaml yaml = Configuration.newYaml();

        Iterable<Object> iterable = yaml.loadAll(text);
        Map map = null;
        for (Object obj: iterable) {
            if (obj instanceof Map) {
                if (map == null) map = (Map) obj;
                else map.putAll((Map)obj);
            } else {
                if (map == null) map = (Map) obj;
                map.put(obj.toString(), null);
            }
        }
        return map;
    }

    @Test
    void basic() throws Exception {
        String config = String.join("\n",
                "_x.a.b: x.a.b",
                "_x:",
                "  y:",
                "    integer: 1",
                "    integer2: null",
                "    decimal: 2.01",
                "  map:",
                "    'key':",
                "      - 1",
                "      - 2");
        Map<String, Object> map = load(config);

        X x = new X();
        new ObjectConfig().prefix("_x").instance(x).configure(map);
        assertTrue(x.a.b.equals("x.a.b"));
        assertTrue(x.y.integer == 1);
        assertTrue(x.y.integer2 == 0);
        assertTrue(x.y.decimal.compareTo(new BigDecimal("2.01")) == 0);
        assertTrue(x.map.get("key").size() == 2);
        assertTrue(x.map.get("key").get(0).equals(1));

        config = String.join("\n",
                "_x.a.b: x.a.b",
                "_x.y:",
                "    integer: 1",
                "    integer2: null",
                "    decimal: 2.01");
        map = load(config);
        x = new X();
        new ObjectConfig().prefix("_x").instance(x).configure(map);
        assertTrue(x.a.b.equals("x.a.b"));
        assertTrue(x.y.integer == 1);
        assertTrue(x.y.integer2 == 0);
        assertTrue(x.y.decimal.compareTo(new BigDecimal("2.01")) == 0);
    }

    @Test
    void listAndArray() throws Exception {
        Map map = new LinkedHashMap<>();
        Map child = new LinkedHashMap<>();
        List list = new ArrayList();

        Map y = new LinkedHashMap<>();
        y.put("integer", 1);
        y.put("integer2", null);
        y.put("decimal", 1L);
        list.add(y);

        y = new LinkedHashMap<>();
        y.put("integer", 2);
        y.put("integer2", null);
        y.put("decimal", "3.02");
        list.add(y);

        // associate with a.list
        child.put("list", list);
        map.put("_x.a", child);

        X x = new X();
        new ObjectConfig().prefix("_x").instance(x).configure(map);
        assertTrue(x.a.list.size() == 2);
        assertTrue(x.a.list.get(0).integer == 1);
        assertTrue(x.a.list.get(1).integer == 2);

        // associate with a.array
        child.remove("list");
        child.put("array", list);
        x = new X();
        new ObjectConfig().prefix("_x").instance(x).configure(map);
        assertTrue(x.a.array.length == 2);
        assertTrue(x.a.array[0].integer == 1);
        assertTrue(x.a.array[1].integer == 2);
    }

    @Test
    void templateArray() throws Exception {
        Map map = new LinkedHashMap<>();
        Map child = new LinkedHashMap<>();
        List list = new ArrayList();

        map.put("_x.a", child);
        child.put("array2", list);

        Map z = new LinkedHashMap();
        z.put("t", 1);
        list.add(z);

        z = new LinkedHashMap();
        z.put("t", 2);
        list.add(z);

        X x = new X();
        new ObjectConfig().prefix("_x").instance(x).configure(map);

        assertTrue(x.a.array2.length == 2);
        assertTrue(x.a.array2[0].t == 1);
        assertTrue(x.a.array2[1].t == 2);
    }

    public static class X {
        A a;
        Y y;
        Map<String, List<Integer>> map = new HashMap<>();

        public A getA() {
            return a;
        }

        public void setA(A a) {
            this.a = a;
        }

        public Y getY() {
            return y;
        }

        public void setY(Y y) {
            this.y = y;
        }

        public Map<String, List<Integer>> getMap() {
            return map;
        }

        public void setMap(Map<String, List<Integer>> map) {
            this.map = map;
        }
    }

    public static class A {
        private String b;
        private List<Y> list;
        private Y[] array;
        private Z<Integer>[] array2;

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public List<Y> getList() {
            return list;
        }

        public void setList(List<Y> list) {
            this.list = list;
        }

        public Y[] getArray() {
            return array;
        }

        public void setArray(Y[] array) {
            this.array = array;
        }

        public Z<Integer>[] getArray2() {
            return array2;
        }

        public void setArray2(Z<Integer>[] array2) {
            this.array2 = array2;
        }
    }

    public static class Y {
        private int integer;
        private int integer2;
        private BigDecimal decimal;

        public int getInteger() {
            return integer;
        }

        public void setInteger(int integer) {
            this.integer = integer;
        }

        public int getInteger2() {
            return integer2;
        }

        public void setInteger2(int integer2) {
            this.integer2 = integer2;
        }

        public BigDecimal getDecimal() {
            return decimal;
        }

        public void setDecimal(BigDecimal decimal) {
            this.decimal = decimal;
        }
    }

    public static class Z<T> {
        private T t;

        public T getT() {
            return t;
        }

        public void setT(T t) {
            this.t = t;
        }
    }
}
