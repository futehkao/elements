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
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;
import net.e6tech.elements.common.logging.Logger;
import org.codehaus.groovy.runtime.InvokerHelper;
import static net.e6tech.elements.rules.ControlFlow.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private Rule ruleFailed;
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
        ruleFailed = rule;
        if (this.failedMessage == null) {
            if (failedMessage == null) {
                this.failedMessage = RULE_FAILED_MSG + rule.getName() + ".";
            } else {
                this.failedMessage = RULE_FAILED_MSG + rule.getName() + " - " + failedMessage;
            }
        }
    }

    void ruleFailed(Rule rule, Throwable throwable) {
        exception = throwable;
        ruleFailed = rule;
        if (this.failedMessage == null) {
            if (throwable == null) {
                this.failedMessage = RULE_FAILED_MSG + rule.getName() + ".";
            } else {
                this.failedMessage = RULE_FAILED_MSG + rule.getName() + " - " + throwable.getMessage();
            }
        }
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
        return ruleFailed;
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

    public Object getProperty(String property) {
        if ("result".equals(property))
            return getResult();
        return properties.get(property);
    }

    public void setProperty(String property, Object newValue) {
        properties.put(property, newValue);
    }

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
