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
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class RuleContext implements GroovyObject {

    Result result = new Result();
    Rule currentRule;
    boolean completed = false;
    private transient Object delegate;
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, Rule> rulesHalted = new HashMap<>();
    private Map<String, Rule> rulesExecuted = new HashMap<>();
    private Rule ruleFailed;
    private String failedMessage;

    public RuleContext() {
    }

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

    protected void onCheckFailed() {
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
        if (failedMessage == null) {
            this.failedMessage = "Rule failed: " + rule.getName() + ".";
        } else {
            this.failedMessage = "Rule failed: " + rule.getName() + " - " + failedMessage;
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

    public String getFailedMessage() {
        return failedMessage;
    }

    protected boolean verifyObject(Object object) {
        return true;
    }

    public boolean verify(Object ... objects) {
        for (Object value : objects) {
            boolean bool = true;
            if (value instanceof Closure) {
                Closure c1 = (Closure) ((Closure)value).clone();
                c1.setResolveStrategy(Closure.DELEGATE_FIRST);
                c1.setDelegate(this);
                Object obj = c1.call();
                if (obj == null) bool = true; // this may be counter intuitive.  We should assume true, if there is no effort to return false.
                else if (obj.getClass().equals(Boolean.TYPE) || obj.getClass().equals(Boolean.class)) {
                    bool = (boolean) obj;
                }
            } else if (value instanceof Verify) {
                Verify verify = (Verify) value;
                Closure c1 = (Closure) verify.getClosure().clone();
                c1.setResolveStrategy(Closure.DELEGATE_FIRST);
                c1.setDelegate(this);
                Object obj = c1.call();
                if (obj == null) bool = true; // this may be counter intuitive.  We should assume true, if there is no effort to return false.
                else if (obj.getClass().equals(Boolean.TYPE) || obj.getClass().equals(Boolean.class)) {
                    bool = (boolean) obj;
                }
            } else if (value instanceof Boolean) {
                bool = (boolean) value;
            } else {
                bool = verifyObject(value);
            }

            if (!bool) {
                onCheckFailed();
                throw new AssertionError("failed to verify rule " + currentRule.getName() + " -> " + value);
            }
        }
        return true;
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
        if (property.equals("result")) return getResult();
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