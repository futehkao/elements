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

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by futeh.
 */
public class TextSubstitutionTest {

    @Test
    public void basic() throws Exception {
        String text = "${a} ${b} ${x.name}";
        TextSubstitution sub = new TextSubstitution(text);
        Map<String, Object> map = new HashMap<>();
        // map.put("a", "a");
        // map.put("b", "b");
        map.put("x", new X());
        System.out.println(sub.build(map));
    }

    private static class X {
        public String getName() {
            return "X";
        }
    }

}