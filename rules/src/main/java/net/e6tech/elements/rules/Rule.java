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
import net.e6tech.elements.jmx.stat.Measurement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * VERY IMPORTANT.  This class must be thread safe and not keep any mutable data.
 * All of the closure are cloned before executing.
 *
 * Created by futeh.
 */
public class Rule {

    String name;
    String description;
    Closure condition;
    Closure halt;
    Closure proceed;
    Closure failed;
    List<Object> verifies = new ArrayList<>();
    Rule parent;
    RuleSet ruleSet;
    Measurement measurement = new Measurement();

    public void addChild(Rule rule) {
        rule.setParent(this);
        verifies.add(rule);
    }

    public boolean removeChild(Rule rule) {
        Iterator iterator = verifies.iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            Object object = iterator.next();
            if (object.equals(rule)) iterator.remove();
            removed = true;
        }
        return removed;
    }

    public Rule[] getChildren() {
        List<Rule> list = new ArrayList<>();
        for (Object object : verifies) {
            if (object instanceof Rule) {
                list.add((Rule)object);
            }
        }
        return list.toArray(new Rule[list.size()]);
    }

    public Rule getParent() {
        return parent;
    }

    public void setParent(Rule parent) {
        this.parent = parent;
    }

    public String ruleName() {
        return getName();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (description == null) description = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void verify(Object ... objects) {
        verifies.add(objects);
    }

    public void verify(String name, Closure closure) {
        verifies.add(new Verify(name, closure));
    }

    public void condition(Closure closure) {
        this.condition = closure;
    }

    public void proceed(Closure closure) {
        this.proceed = closure;
    }

    public void halt(Closure closure) {
        this.halt = closure;
    }

    public void failed(Closure closure) {
        this.failed = closure;
    }

    public RuleSet getRuleSet() {
        return ruleSet;
    }

    public void setRuleSet(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    public Rule rule(String name, Closure closure) {
        Rule rule = ruleSet.createRule(name, closure, true);
        addChild(rule);
        return rule;
    }

    public Rule rule(String name) {
        Rule rule = ruleSet.getRule(name);
        addChild(rule);
        return rule;
    }

    public void run(RuleContext context) {
        _run(context, true);
    }

    private boolean _run(RuleContext context, boolean root) {
        long start = System.currentTimeMillis();
        boolean bool = true;

        // evaluate this rule's condition
        if (condition != null) {
            Closure c1 = (Closure) condition.clone();
            c1.setResolveStrategy(Closure.DELEGATE_FIRST);
            c1.setDelegate(context);
            try {
                Object obj = c1.call();
                if (obj == null) bool = true; // this may be counter intuitive.  We should assume true, if there is no effort to return false.
                else if (obj.getClass().equals(Boolean.TYPE) || obj.getClass().equals(Boolean.class)) {
                    bool = (boolean) obj;
                }
            } catch (AssertionError ex) {
                return handleFailed(context, root, ex);
            }
        }

        // Once we have reached this point, it means AssertionError has been not thrown
        if (bool) {
            // run children
            AssertionError error = null;
            Object failedObject = null;
            for (Object object : verifies) {
                if (object instanceof Rule) {
                    Rule child = (Rule) object;
                    if (error != null) {
                        // mark rest of the rules as halted
                        context.ruleHalted(child);
                    } else {
                        try {
                            child._run(context, false);
                        } catch (AssertionError ex) {
                            error = ex;
                            failedObject = child;
                            context.ruleHalted(child);
                        }
                    }
                } else {
                    if (error == null) {
                        try {
                            context.setCurrentRule(this);
                            if (object instanceof Object[]) {
                                Object[] verifyObjects = (Object[]) object;
                                bool = context.verify(verifyObjects);
                            } else {
                                bool = context.verify(object);
                            }
                        } catch (AssertionError ex) {
                            error = ex;
                            failedObject = object;
                        }
                    }
                }
            }

            if (error != null) {
                runClosure(context, failed);
                // propagate exception up if not root
                if (failedObject instanceof Rule) {
                    if(measurement != null) measurement.fail();
                    if (!root) throw error;
                    else return false;
                } else {
                    return handleFailed(context, root, error);
                }
            }
        }

        context.setCurrentRule(this);

        try {
            if (bool) {
                runClosure(context, proceed);
                context.ruleExecuted(this);
                if(measurement != null) measurement.add(System.currentTimeMillis() - start);
            } else {
                // halt just means the rule did not fired.
                runClosure(context, halt);
                context.ruleHalted(this);
            }
        } catch (AssertionError ex) {
            return handleFailed(context, root, ex);
        }

        // ok ran children and proceed or halt ok, so set completed to true
        context.setCompleted(true);
        return true;
    }

    private boolean handleFailed(RuleContext context, boolean root, AssertionError error) {
        if(measurement != null) measurement.fail();
        context.setCurrentRule(this);
        context.setCompleted(false);
        runClosure(context, failed);
        context.ruleFailed(this, error.getMessage());
        if (!root) throw error;
        return false;
    }

    private void runClosure(RuleContext context, Closure closure) {
        if (closure == null) return;
        try {
            Closure a1 = (Closure) closure.clone();
            a1.setResolveStrategy(Closure.DELEGATE_FIRST);
            a1.setDelegate(context);
            a1.run();
        } catch (AssertionError ex) {
            if (closure == failed) return;
            throw ex;
        } catch (Throwable throwable) {
            if (closure == failed) return;
            throw new AssertionError(throwable);
        }
    }
}
