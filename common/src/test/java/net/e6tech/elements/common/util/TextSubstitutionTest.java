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

package net.e6tech.elements.common.util;


import net.e6tech.elements.common.Tags;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by futeh.
 */
@Tags.Common
public class TextSubstitutionTest {

    @Test
    public void basicMap() {
        String text = "${a} ${b} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("  X"));
    }

    @Test
    public void basicMap2() {
        String text = "${${${a}.${b}}}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        map.put("b", "name");
        map.put("X", "Y");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("Y"));
    }

    @Test
    public void nested() {
        String text = "${${a}.name:+ABC} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("ABC X"));
    }

    @Test
    public void nested2(){
        String text = "${${a}.name:+ ${x.name}} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals(" X X"));
    }

    @Test
    public void nested3(){
        String text = "${${a}.${b}:+ ${${a}.${b}}} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        map.put("b", "name");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals(" X X"));
    }

    @Test
    public void nested4(){
        String text = "${z: ${${a}.${b}}} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        map.put("b", "name");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals(" X X"));
    }

    @Test
    public void nested5(){
        String text = "${${${a}.${b}}} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        map.put("b", "name");
        map.put("X", "Y");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("Y X"));
    }

    @Test
    public void nested6(){
        String text = "${a:+${${b}}}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("a", "x");
        map.put("b", "name");
        map.put("name", "Y");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("Y"));
    }

    @Test
    public void nested7(){
        String text = "${${${b}}}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("b", "name");
        map.put("name", "Y");
        map.put("Y", "Z");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("Z"));
    }

    @Test
    public void auxillary(){
        String text = "${ z:+ ${${a}.${b}}} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", new X());
        map.put("a", "x");
        map.put("b", "name");
        Map<String, Object> auxillary = new HashMap<>();
        auxillary.put("z", "Z");
        String output = sub.build(map, auxillary);
        System.out.println(output);
        assertTrue(output.equals(" X X"));
    }

    @Test
    public void basicObject() {
        String text = "${ a }${b} ${name}";
        TextSubstitution sub = new TextSubstitution(text);
        String output = sub.build(new X());
        System.out.println(output);
        assertTrue(output.equals(" A  X"));
    }

    @Test
    public void escape() {
        String text = "\\${:a }${name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "My name");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("${:a }My name"));
    }

    @Test
    public void predicate() {
        String text = "${${x} := A ?B:C}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("x", "y");
        map.put("y", "A");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("B"));

        map.remove("y");
        output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("C"));
    }

    @Test
    public void undefined() {
        String text = "${:a }${name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "My name");
        String output = sub.build(map);
        System.out.println(output);
        assertTrue(output.equals("a My name"));
    }

    @Test
    public void minusDefault() {
        String text = "Parameter value ${1} for parameter named ${0} is invalid${2:+: }${2}.";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        map.put("0", "NAME");
        map.put("1", "NULL");
        map.put("2", "NULL");
        String output = sub.build(map);
        System.out.println(output);

    }

    @Test
    public void moreTest() {
        String  template = "Purchase${location:+ at }${location}";
        TextSubstitution sub = new TextSubstitution(template);
        Map<String, Object> map = new HashMap<>();
        map.put("location", "112 Main St.");
        String output = sub.build(map);
        assertTrue(output.equals("Purchase at 112 Main St."));

        template = "Reversal${reversed.memo:+ - }${reversed.memo}";
        sub = new TextSubstitution(template);
        map = new HashMap<>();
        map.put("reversed", new X());
        output = sub.build(map);
        assertTrue(output.equals("Reversal - memo"));

        template = "${:currencyCode} is null.";
        sub = new TextSubstitution(template);
        map = new HashMap<>();
        output = sub.build(map);
        assertTrue(output.equals("currencyCode is null."));

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