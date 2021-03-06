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
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.jmx.stat.Measurement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static net.e6tech.elements.rules.ControlFlow.*;

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
    Closure halted;
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
            if (object.equals(rule))
                iterator.remove();
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
        if (description == null)
            description = name;
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

    public void halted(Closure closure) {
        this.halted = closure;
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

    protected void measurement(boolean b) {
        measurement.setEnabled(b);
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
        boolean result = runInternal(context, true) == Failed;
        context.setCompleted(!result);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134", "squid:S00100", "squid:S3776"})
    protected ControlFlow runInternal(RuleContext context, boolean root) {
        long start = System.currentTimeMillis();
        boolean cond = true;
        context.setCurrentRule(this);

        // evaluate this rule's condition
        if (condition != null) {
            Closure c1 = (Closure) condition.clone();
            c1.setResolveStrategy(Closure.DELEGATE_FIRST);
            c1.setDelegate(context);
            try {
                Object obj = c1.call();
                if (obj == null)
                    cond = true; // this may be counter intuitive.  We should assume true, if there is no effort to return false.
                else if (obj.getClass().equals(Boolean.TYPE) || obj.getClass().equals(Boolean.class)) {
                    cond = (boolean) obj;
                } else if (Failed == obj) {
                    cond = false;
                }
            } catch (Exception ex) {
                return handleException(context, ex);
            }
        }

        ControlFlow flow = Continue;
        if (cond) {
            // run children
            Exception exception = null;
            for (Object object : verifies) {
                context.setCurrentRule(this);
                if (object instanceof Rule) {
                    Rule child = (Rule) object;
                    if (flow == Failed || flow == Success) { // if there are previous failure from children, simply mark halted and not run further.
                        context.ruleHalted(child);
                    } else if (flow == Continue) {
                        try {
                            flow = child.runInternal(context, false);
                        } catch (Exception ex) {
                            context.ruleHalted(child);
                            flow = Failed;
                            exception = ex;
                        }
                    }
                } else {
                    if (flow == Continue) { // only run if it is still Continue.
                        try {
                            context.setCurrentRule(this);
                            if (object instanceof Object[]) {
                                Object[] verifyObjects = (Object[]) object;
                                flow = context.verify(verifyObjects); // either keep going or throws AssertionError
                            } else {
                                flow = context.verify(object); // either keep going or throws AssertionError
                            }
                        } catch (Exception ex) {
                            flow = Failed;
                            exception = ex;
                        }
                    }
                }
            }

            try {
                context.setCurrentRule(this); // because child rule changed it.
                if (flow == Failed) {
                    if (exception != null)
                        handleException(context, exception);
                    else {
                        runClosure(context, failed);
                        if(measurement != null)
                            measurement.fail();
                    }
                    context.setCompleted(false);
                    context.ruleHalted(this);
                    return Failed; // pass it up
                }

                runClosure(context, proceed);
                context.ruleExecuted(this);
                if(measurement != null)
                    measurement.append((double)(System.currentTimeMillis() - start));
            } catch (Exception ex) {
                return handleException(context, ex);
            }
        } else {
            try {
                // halt just means the rule did not fired.
                runClosure(context, halted);
                context.ruleHalted(this);
            } catch (Exception ex) {
                return handleException(context, ex);
            }
        }

        return flow;
    }

    private ControlFlow handleException(RuleContext context, Exception throwable) {
        if(measurement != null)
            measurement.fail();
        context.setCurrentRule(this);
        try {
            runClosure(context, failed);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception th) {
            throw new SystemException(th);
        }
        context.ruleFailed(this, throwable);
        return Failed;
    }

    private void runClosure(RuleContext context, Closure closure) {
        if (closure == null)
            return;
        Closure a1 = (Closure) closure.clone();
        a1.setResolveStrategy(Closure.DELEGATE_FIRST);
        a1.setDelegate(context);
        a1.run();
    }
}
