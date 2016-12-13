/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Configuration;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

/**
 * Created by futeh.
 */
public class ConfigurationTest {

    @Test
    public void test() {
        System.out.println("home=" + System.getProperty("home"));
        Logger logger = Logger.getLogger();
        logger.info("Just a test");
        Exception ex =  logger.runtimeException("Just a test");
    }

    @Test
    public void simple() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("home", "/usr/local");
        Configuration config = new Configuration();
        config.setProperties(properties);
        config.loadFile("src/test/resources/config.yaml");
        System.out.println(config.get("x").toString());
        System.out.println(config.get("z").toString());
        System.out.println(config.get("dir").toString());
    }

    @Test
    public void textSubstitution() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("home", "/usr/local");
        Configuration config = new Configuration();
        config.setProperties(properties);
        config.loadFile("src/test/resources/config.yaml");
        Map map = new Properties();

        config.configure(map);
        assertTrue(map.size() > 0);
        assertTrue(map.get("z").equals("world nested hello world"));

        // test if map is populate
        config.configure(map, "map", null, null);
        assertTrue(map.size() > 0);
    }

    @Test
    public void dotReference() {
        Configuration config = new Configuration();
        String yaml = ""
                + "x.a: ^A\n"
                + "x.b: B\n"
                + "x.child:\n"
                + "   a: ^A";
        config.load(yaml);
        X x = new X();
        String reference = "reference A";
        config.configure(x, "x",
                (ref) -> { if (ref.equals("A")) return reference; else return  null; } , null);
        assertTrue(System.identityHashCode(x.getA()) == System.identityHashCode(reference));
        assertTrue(x.getB().equals("B"));
    }

    @Test
    public void mapReference() {
        Configuration config = new Configuration();
        String yaml = ""
                + "x.b: B\n"
                + "x.child.a: ^A\n"
                + "x.child:\n"
                + "    b: ^B";
        config.load(yaml);
        X x = new X();
        String referenceA = "reference A";
        String referenceB = "reference B";
        config.configure(x, "x",
                (ref) -> {
                    if (ref.equals("A")) return referenceA;
                    else if (ref.equals("B")) return referenceB;
                    else return  null; },
                null);
        assertTrue(System.identityHashCode(x.getChild().getA()) == System.identityHashCode(referenceA));
        assertTrue(System.identityHashCode(x.getChild().getB()) == System.identityHashCode(referenceB));
    }

    @Test
    public void mapReference2() {
        Configuration config = new Configuration();
        String yaml = ""
                + "x:\n"
                + "   child:\n"
                + "      a: ^A";
        config.load(yaml);
        X x = new X();
        String reference = "reference A";
        config.configure(x, "x",
                (ref) -> { if (ref.equals("A")) return reference; else return  null; } , null);
        assertTrue(System.identityHashCode(x.getChild().getA()) == System.identityHashCode(reference));
    }

    @Test
    public void properties() {
        Configuration config = new Configuration();
        String yaml = ""
                + "x.properties.a: a";
        config.load(yaml);
        X x = new X();
        config.configure(x, "x", null, null);
        assertTrue(x.getProperties().getProperty("a").equals("a"));
    }

    private static class X {
        String a;
        String b;
        X child;
        Properties properties;

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public X getChild() {
            return child;
        }

        public void setChild(X child) {
            this.child = child;
        }

        public Properties getProperties() {
            return properties;
        }

        public void setProperties(Properties properties) {
            this.properties = properties;
        }
    }

    @Test
    public void simpleYaml() {
        Yaml yaml = new Yaml();
        Iterable<Object> iter = yaml.loadAll(test1);
        for (Object m : iter) {
            System.out.println(m);
        }
        iter = yaml.loadAll(test2);
        for (Object m : iter) {
            Map<String, Object> map = (Map<String, Object>) m;
            System.out.println(map);
        }
    }

    private static String test1 = "---\n" +
            "Time: 2001-11-23 15:01:42 -5\n" +
            "User: ed\n" +
            "Status: 1\n" +
            "Warning:\n" +
            "  This is an error message\n" +
            "  for the log file\n" +
            "---\n" +
            "Time: 2001-11-23 15:02:31 -5\n" +
            "User: ed\n" +
            "Warning:\n" +
            "  A slightly different error\n" +
            "  message.\n" +
            "---\n" +
            "Date: 2001-11-23 15:03:17 -5\n" +
            "User: ed\n" +
            "Fatal:\n" +
            "  Unknown variable \"bar\"\n" +
            "Stack:\n" +
            "  - file: TopClass.py\n" +
            "    line: 23\n" +
            "    code: |\n" +
            "      x = MoreObject(\"345\\n\")\n" +
            "  - file: MoreClass.py\n" +
            "    line: 58\n" +
            "    code: |-\n" +
            "      foo = bar";

    private static String test2 = "Time: 2001-11-23 15:01:42 -5\n" +
            "User: ed\n" +
            "Status: 1\n" +
            "Number: 2.22\n" +
            "Warning:\n" +
            "  This is an error message\n" +
            "  for the log file\n" ;

}
