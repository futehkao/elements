/*
Copyright 2015-2019 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.rules;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Properties;

/**
 * Created by futeh.
 */
public class RuleSetTest {

    @Test
    public void test() throws Exception {
        RuleSet ruleSet = new RuleSet(new Properties());
        ruleSet.load("classpath://net/e6tech/elements/rules/test.groovy");
        RuleContext context = new RuleContext();
        context.setProperty("a", "abc");
        // attributes.setProperty("result", new ResultMap());
        ruleSet.runRule("test", context);
        System.out.println(context);
    }


    public static class ResultMap extends LinkedHashMap<String, Object> {
        public Object get(String key) {
            return super.get(key);
        }

        public Object put(String key, Object obj) {
            return super.put(key, obj);
        }
    }
}
