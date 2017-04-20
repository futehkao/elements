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

package net.e6tech.elements.common.util;


import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by futeh.
 */
public class TextSubstitutionTest {

    @Test
    public void basicMap() throws Exception {
        String text = "${a} ${b} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("  X"));
    }

    @Test
    public void basicObject() throws Exception {
        String text = "${ a }${b} ${name}";
        TextSubstitution sub = new TextSubstitution(text);
        String output = sub.build(new X());
        System.out.println(output);
        assertTrue(output.equals(" A  X"));
    }

    @Test
    public void undefined() throws Exception {
        String text = "${:a }${name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "My name");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("a My name"));
    }

    @Test
    public void moreTest() {
        String  template = "Purchase${location:+ at }${location}";
        TextSubstitution sub = new TextSubstitution(template);
        Map<String, Object> map = new HashMap<>();
        map.put("location", "112 Main St.");
        String output = sub.build(map);
        System.out.println(sub.build(map));

        template = "Reversal${reversed.memo:+ - }${reversed.memo}";
        sub = new TextSubstitution(template);
        map = new HashMap<>();
        map.put("reversed", new X());
        output = sub.build(map);
        System.out.println(sub.build(map));

        template = "${:currencyCode} is null.";
        sub = new TextSubstitution(template);
        map = new HashMap<>();
        output = sub.build(map);
        System.out.println(sub.build(map));

        template = "Error: ${:FxService} for provider ${0} not set up in ${:FxProviderGateway}.";
        sub = new TextSubstitution(template);
        map = new HashMap<>();
        map.put("0", "blah");
        output = sub.build(map);
        System.out.println(sub.build(map));

    }

    private static class X {
        public String getName() {
            return "X";
        }
        public String getA() { return "A"; }
        public String getMemo() { return "memo";}
    }
}