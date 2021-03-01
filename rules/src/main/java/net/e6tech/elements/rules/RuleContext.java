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
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;
import net.e6tech.elements.common.logging.Logger;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.*;

import static net.e6tech.elements.rules.ControlFlow.Continue;
import static net.e6tech.elements.rules.ControlFlow.Failed;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S2065")
public class RuleContext implements GroovyObject {

    private static final String RULE_FAILED_MSG = "Rule failed: ";
    static Logger logger = Logger.getLogger();

    Result result = new Result();
    Rule currentRule;
    boolean completed = false;
    private transient Object delegate; // transient so that Groovy does not serialize and hold on to it.
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, Rule> rulesHalted = new LinkedHashMap<>();
    private Map<String, Rule> rulesExecuted = new LinkedHashMap<>();
    private List<FailedRule> failedRules = new LinkedList<>();
    private String failedMessage;
    private Throwable exception;
    private RuleSet ruleSet;

    public Object getDelegate() {
        return delegate;
    }

    public void setDelegate(Object delegate) {
        this.delegate = delegate;
    }

    public Result getResult() {
        return result;
    }

    public Rule getCurrentRule() {
        return currentRule;
    }

    public void setCurrentRule(Rule rule) {
        this.currentRule = rule;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getFailedMessage() {
        return failedMessage;
    }

    public void setFailedMessage(String failedMessage) {
        this.failedMessage = failedMessage;
    }

    protected void onCheckFailed() {
        // to be subclassed.  this method is called whenever check fails.
    }

    void ruleExecuted(Rule rule) {
        rulesExecuted.put(rule.getName(), rule);
    }

    // condition is false so that the rule is not executed or its halt closure has been executed
    void ruleHalted(Rule rule) {
        rulesHalted.put(rule.getName(), rule);
    }

    // rule threw an exception
    void ruleFailed(Rule rule, String failedMessage) {
        String message;
        if (failedMessage == null) {
            message = RULE_FAILED_MSG + rule.getName() + ".";
        } else {
            message = RULE_FAILED_MSG + rule.getName() + " - " + failedMessage;
        }
        if (this.failedMessage == null) {
            this.failedMessage = message;
        }
        failedRules.add(new FailedRule(rule, message));
    }

    void ruleFailed(Rule rule, Throwable throwable) {
        exception = throwable;
        String message = null;
        if (throwable == null) {
            message = RULE_FAILED_MSG + rule.getName() + ".";
        } else {
            message = RULE_FAILED_MSG + rule.getName() + " - " + throwable.getMessage();
        }
        if (this.failedMessage == null) {
            this.failedMessage = message;
        }
        failedRules.add(new FailedRule(rule, throwable, message));
        if (throwable != null) {
            logger.debug(throwable.getMessage(), throwable);
        }
    }

    public Rule getRuleExecuted(String ruleName) {
        return rulesExecuted.get(ruleName);
    }

    public Rule getRuleHalted(String ruleName) {
        return rulesHalted.get(ruleName);
    }

    public Rule getRuleFailed() {
        return failedRules.isEmpty() ? null : failedRules.get(0).getRule();
    }

    public Throwable getException() {
        return exception;
    }

    public RuleSet getRuleSet() {
        return ruleSet;
    }

    public void setRuleSet(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    // to be subclassed
    @SuppressWarnings("squid:S1172")
    protected ControlFlow verifyObject(Object object) {
        return Continue;
    }

    public ControlFlow verify(Object ... objects) {
        ControlFlow flow = Continue;
        for (Object value : objects) {
            if (value instanceof Closure) {
                Closure c1 = (Closure) ((Closure)value).clone();
                c1.setResolveStrategy(Closure.DELEGATE_FIRST);
                c1.setDelegate(this);
                Object obj = c1.call();
                flow = interpret(obj);
            } else if (value instanceof Verify) {
                Verify verify = (Verify) value;
                Closure c1 = (Closure) verify.getClosure().clone();
                c1.setResolveStrategy(Closure.DELEGATE_FIRST);
                c1.setDelegate(this);
                Object obj = c1.call();
                flow = interpret(obj);
            } else if (value instanceof Boolean) {
                flow = interpret(value);
            } else {
                flow = verifyObject(value);
            }

            if (flow == Failed) {
                onCheckFailed();
                ruleFailed(currentRule, "failed to verify rule " + currentRule.getName() + " -> " + value);
                break;
            }
        }
        return flow;
    }

    @SuppressWarnings("squid:S4165")
    private ControlFlow interpret(Object obj) {
        ControlFlow flow = Continue;
        if (obj == null)
            flow = Continue; // this may be counter intuitive.  We should assume true, if there is no effort to return false.
        else if (obj.getClass().equals(Boolean.TYPE) || obj.getClass().equals(Boolean.class)) {
            boolean bool = (boolean) obj;
            if (!bool)
                flow = Failed;
        } else if (obj instanceof ControlFlow) {
            flow = (ControlFlow) obj;
        }
        return flow;
    }

    protected void handleFailed(Closure closure) {
        onCheckFailed();
        if (closure != null) {
            Closure c1 = (Closure) closure.clone();
            c1.setResolveStrategy(Closure.DELEGATE_FIRST);
            c1.setDelegate(this);
            c1.run();
        }
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public Object getProperty(String property) {
        if ("result".equals(property))
            return getResult();
        return properties.get(property);
    }

    @Override
    public void setProperty(String property, Object newValue) {
        properties.put(property, newValue);
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            return getMetaClass().invokeMethod(this, name, args);
        }  catch (MissingMethodException ex) {
            if (delegate != null) {
                MetaClass meta = InvokerHelper.getMetaClass(delegate.getClass());
                return meta.invokeMethod(delegate, name, args);
            } else {
                throw ex;
            }
        }
    }

    public MetaClass getMetaClass() {
        return InvokerHelper.getMetaClass(getClass());
    }

    public void setMetaClass(MetaClass metaClass) {
        throw new UnsupportedOperationException();
    }
}
