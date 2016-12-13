/*
Copyright 2015 Futeh Kao

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

import groovy.lang.Closure;
import net.e6tech.elements.common.script.AbstractScriptBase;
import net.e6tech.elements.common.resources.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Created by futeh.
 */
abstract public class RuleSetScript extends AbstractScriptBase<RuleSet> {

    public Rule rule(String name, Closure closure) {
        return getShell().createRule(name, closure, false);
    }

    // called by Groovy config scripts
    public void root(String ruleName, Closure<String> closure) {
        // runAfter because we need to have all of the rules loaded.
        getShell().runAfter(() -> {
            Configuration configuration = new Configuration(getShell().getProperties());

            Rule root = new Rule();
            // switch closure's delegate to root
            Object delegate = closure.getDelegate();
            closure.setDelegate(root);
            String value = closure.call();

            // switch closure's delegate back to original
            closure.setDelegate(delegate);
            // load configuration
            configuration.load(value);
            traverse(root, configuration);

            // add rule to be a root in RuleSet
            root.setName(ruleName);
            getShell().addRoot(ruleName, root);

        });
    }

    protected void traverse(Rule parent, Object obj) {
        if (obj instanceof List) {
            List list = (List) obj;
            for (Object child : list) {
                traverse(parent, child);
            }
        } else if (obj instanceof String) {
            String key = (String) obj;
            Rule rule = getShell().getRule(key);
            if (rule != null) parent.addChild(rule);
        } else if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (String key : map.keySet()) {
                Rule rule = getShell().getRule(key);
                if (rule != null) {
                    parent.addChild(rule);
                    Object child = map.get(key);
                    traverse(rule, child);
                }
            }
        }
    }
}
