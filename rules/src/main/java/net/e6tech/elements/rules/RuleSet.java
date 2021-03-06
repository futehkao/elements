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

import groovy.lang.Closure;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.script.AbstractScriptShell;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.jmx.JMXService;

import javax.script.ScriptException;
import java.util.*;

/**
 * Created by futeh.
 */
public class RuleSet extends AbstractScriptShell {

    private static final String DEFAULT = "default";
    private static Logger logger = Logger.getLogger();

    private Map<String, Rule> rules;  // this is only used during load and then set to null
    private Rule root;  // this is only used during load and then set to null
    private Map<String, Rule> rootSet = new LinkedHashMap<>();
    private Map<String, Map<String, Rule>> rootRules = new LinkedHashMap<>();
    private boolean measurement = false;
    private String mbeanPrefix = "";

    public RuleSet(Properties properties) {
        super(properties);
    }

    public RuleSet(ResourceManager resourceManager) {
        this(null, resourceManager);
    }

    public RuleSet(String beanName, ResourceManager resourceManager) {
        super(resourceManager.getProperties());
        setKnownEnvironments(resourceManager.getKnownEnvironments());
        if (beanName != null)
            resourceManager.registerBean(beanName, this);
    }

    public String getMbeanPrefix() {
        return mbeanPrefix;
    }

    public void setMbeanPrefix(String mbeanPrefix) {
        this.mbeanPrefix = mbeanPrefix;
    }

    public void loadRoots(String ... rootSet) throws ScriptException {
        for (String rt : rootSet)
            load(rt);
    }

    // override load to clear out rules
    @Override
    public synchronized void load(String str) throws ScriptException {
        rules = new LinkedHashMap<>();
        super.load(str);
        registerMBean(getMbeanPrefix(), root);
        root = null;
        rules = null;
    }

    protected void registerMBean(String path, Rule rule) {
        if (!measurement)
            return;

        if (rule.getMeasurement() != null) {
            rule.getMeasurement().setName(rule.getName());
            rule.getMeasurement().setUnit("ms");
            String objectName = "net.e6tech:type=Rule,name=" + path + rule.getName();
            JMXService.registerMBean(rule.getMeasurement(), objectName);
        }

        String contextPath = path + rule.getName() + ".";
        for (Rule child : rule.getChildren()) {
            registerMBean(contextPath, child);
        }
    }

    public Rule createRule(String name, Closure closure, boolean anonymous) {
        Rule rule = new Rule();
        rule.setRuleSet(this);
        rule.setName(name);
        rule.measurement(measurement);
        closure.setDelegate(rule);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.run();
        if (!anonymous)
            addRule(rule);
        return rule;
    }

    public void addRoot(String ruleSetName, Rule root) {
        String ruleSet = ruleSetName;
        if (ruleSet == null || DEFAULT.equalsIgnoreCase(ruleSet.trim())) {
            ruleSet = DEFAULT;
        }
        rootSet.put(ruleSet, root);
        rootRules.put(ruleSet, rules);
        this.root = root;
    }

    void addRule(Rule rule) {
        if (rules.get(rule.getName()) != null) {
            throw new SystemException("Duplicate rule name: " + rule.getName());
        }
        rules.put(rule.getName(), rule);
    }

    Rule getRule(String key) {
        return rules.get(key);
    }

    public Optional<Rule> getRoot(String ruleSetName) {
        String ruleSet = ruleSetName;
        if (rootSet.size() == 0)
            throw new SystemException("root not set");
        if (ruleSet == null || DEFAULT.equalsIgnoreCase(ruleSet.trim())) {
            ruleSet = DEFAULT;
        }

        return Optional.ofNullable(rootSet.get(ruleSet));
    }

    public void runRule(String ruleSetName, RuleContext context) {
        String ruleSet = ruleSetName;
        if (rootSet.size() == 0)
            throw new SystemException("root not set");
        if (ruleSet == null || DEFAULT.equalsIgnoreCase(ruleSet.trim())) {
            ruleSet = DEFAULT;
        }
        if (rootSet.get(ruleSet) == null)
            throw new SystemException("ruleSet " + ruleSet + " not found");
        try {
            context.setRuleSet(this);
            context.getProperties().putAll(getScripting().getVariables());
            rootSet.get(ruleSet).run(context);
        } finally {
            context.setRuleSet(null);
        }
    }

    public RuleSet measurement(boolean b) {
        measurement = b;
        return this;
    }

    public boolean measurement() {
       return measurement;
    }

    public String log() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Rule> entry : rootSet.entrySet()) {
            String ruleName = entry.getKey();
            builder.append("\nRoot (").append(ruleName).append(")\n");
            _log(entry.getValue(), builder, 1);
        }
        String str = builder.toString();
        logger.info(str);
        return str;
    }

    @SuppressWarnings({"squid:S134", "squid:S00100"})
    private void _log(Rule rule, StringBuilder builder, int indent) {
        for (Object object : rule.verifies) {
            for (int i = 0; i < indent; i++)
                builder.append("  ");
            if (object instanceof Rule) {
                builder.append("- ");
                Rule child = (Rule) object;
                String ruleName = child.getName();
                builder.append(ruleName).append("\n");
                _log(child, builder, indent + 1);
            } else if (object instanceof Object[]) {
                builder.append("  verify ");
                Object[] objects = (Object[]) object;
                boolean first = true;
                for (Object obj : objects) {
                    if (first) {
                        first = false;
                    } else {
                        builder.append(", ");
                    }
                    builder.append(obj);
                }
                builder.append("\n");
            } else {
                builder.append("  verify ");
                builder.append(object).append("\n");
            }
        }
    }
}
