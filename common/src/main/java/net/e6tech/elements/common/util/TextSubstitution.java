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


package net.e6tech.elements.common.util;

import groovy.lang.Closure;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.ResourceManager;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static java.util.Locale.ENGLISH;

/**
 * Created by futeh.<br><br>
 *
 * This class is used for substitute variables in text.  It supports nested expressions like
 * <pre>"${${a}.${b}:+ ${${a}.${b}}} ${x.name}"</pre>
 * <br>
 * However, in general, one should not go crazy with it.<br>
 * <br>
 * An example of it usage:
 * <pre>
 *     String text = "${${a}.${b}:+ ${${a}.${b}}} ${x.name}";
 *     TextSubstitution sub = new TextSubstitution(text);
 *     Map<String, Object> map = new HashMap<>();
 *     map.put("x", new X());
 *     map.put("a", "x");
 *     map.put("b", "name");
 *     String output = sub.build(map);
 *
 *  ${var:default} expands to default if var not defined, to var if var is defined.
 *  ${var::default} same as above.  The extra ':' is to escape special characters like +, - and =
 *  ${var:+default} expands to "" if var is not defined, to default if var is defined.
 *  ${var:-default} expands to default if var not defined, to "" if var is defined.
 *  ${var:=predicate?match:not-match} expands to match if value of var equals to predicate, else not-match
 *     example: "${key := A ?B:C}";  the spaces after the '?' are important.
 *  ${var:^script} executes the script with value of var and returns a string from the execution.
 *  </pre>
 */
@SuppressWarnings("unchecked")
public class TextSubstitution {

    private static DefaultScriptable defaultScriptable = new DefaultScriptable();

    private Map<String, Var> variables;
    private String template;
    private Scriptable script = defaultScriptable;

    // NOTE.  If template ever gets modified, parseVariableNames needs to be called.

    public TextSubstitution(String template) {
        this.template = template;
        variables = parseVariableNames(template);
    }

    public TextSubstitution(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int len = 0;
        while ((len = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, len);
        }
        this.template = builder.toString();
        variables = parseVariableNames(template);
        reader.close();
    }

    public static DefaultScriptable defaultScriptable() {
        return defaultScriptable;
    }

    public String getTemplate() {
        return template;
    }

    public TextSubstitution withScriptable(Scriptable script) {
        this.script = script;
        return this;
    }

    public String build(Object binding) {
        return build(binding, Collections.emptyMap());
    }

    public String build(Object binding, Map<String, Object> auxillary) {
        if (template == null)
            return "";
        String text = template;
        for (Map.Entry<String, Var> entry : variables.entrySet()) {
            Var var = entry.getValue();
            text = replaceVariable(entry.getKey(), var.build(binding, auxillary), text);
        }

        text = text.replace("\\$", "$");
        return text;
    }

    private String replaceVariable(String key, String value, String text) {
        return text.replace("${" + key + "}", value);
    }

    private void parse(String text, List<Var> varList) {
        int max = text.length();
        int pos = 0;
        int prev = -1;
        while (pos < max - 1) {
            if (pos > 0)
                prev = text.codePointAt(pos - 1);
            if (prev != '\\' && text.codePointAt(pos) == '$' && text.codePointAt(pos + 1) == '{') {
                int start = pos + 2;
                Var var = parseVar(text, start);
                varList.add(var);
                pos = start + var.text.length() + 1; // +1 for '}'
            } else {
                pos ++;
            }
        }
    }

    @SuppressWarnings("squid:S3776")
    private Var parseVar(String text, int pos) {
        int max = text.length();
        String key = null;
        String strategy = null;
        int cursor = pos;
        int strategyIndex = -1;
        int prev = -1;
        while (cursor < max) {
            if (cursor > pos)
                prev = text.codePointAt(cursor - 1);

            if (prev != '\\' && text.codePointAt(cursor) == '$' && cursor < max - 1 && text.codePointAt(cursor + 1) == '{') {
                // we got nested express
                cursor = skipNested(text, cursor + 2);
            } else if (text.codePointAt(cursor) == '}') {
                return newVar(text.substring(pos, cursor), key, strategy,
                        (strategyIndex >= 0) ? text.substring(strategyIndex, cursor) : "");
            } else if (strategy == null && text.codePointAt(cursor) == ':' && cursor < max - 1 && text.codePointAt(cursor + 1) == '+') {
                key = text.substring(pos, cursor);
                strategy = ":+";
                strategyIndex = cursor + 2;
                cursor += 2;
            } else if (strategy == null && text.codePointAt(cursor) == ':' && cursor < max - 1 && text.codePointAt(cursor + 1) == '-') {
                key = text.substring(pos, cursor);
                strategy = ":-";
                strategyIndex = cursor + 2;
                cursor += 2;
            } else if (strategy == null && text.codePointAt(cursor) == ':' && cursor < max - 1 && text.codePointAt(cursor + 1) == '=') {
                key = text.substring(pos, cursor);
                strategy = ":=";
                strategyIndex = cursor + 2;
                cursor += 2;
            } else if (strategy == null && text.codePointAt(cursor) == ':' && cursor < max - 1 && text.codePointAt(cursor + 1) == '^') {
                // this is for something like ${a::+b}.  The +, or other special characters, is part of default value.
                key = text.substring(pos, cursor);
                strategy = ":^";
                strategyIndex = cursor + 2;
                cursor += 2;
            } else if (strategy == null && text.codePointAt(cursor) == ':' && cursor < max - 1 && text.codePointAt(cursor + 1) == ':') {
                // this is for something like ${a::+b}.  The +, or other special characters, is part of default value.
                key = text.substring(pos, cursor);
                strategy = ":";
                strategyIndex = cursor + 2;
                cursor += 2;
            } else if (strategy == null && text.codePointAt(cursor) == ':') {
                key = text.substring(pos, cursor);
                strategy = ":";
                strategyIndex = cursor + 1;
                cursor ++;
            } else {
                cursor++;
            }
        }

        return newVar(text.substring(pos, max), key, strategy,
                (strategyIndex >= 0) ? text.substring(strategyIndex, max) : "");
    }

    private Var newVar(String text, String key, String strategy, String defaultValue) {
        Var variable = new Var(text, key == null ? text : key);
        variable.strategy = strategy;
        variable.defaultValue = defaultValue;
        return variable;
    }

    private static int skipNested(String text, int pos) {
        int max = text.length();
        int cursor = pos;
        while (cursor < max) {
            if (text.codePointAt(cursor) == '$' && cursor < max - 1 && text.codePointAt(cursor + 1) == '{') {
                cursor = skipNested(text, cursor + 2);
            } else if (text.codePointAt(cursor) == '}') {
               return cursor + 1;
            } else {
                cursor++;
            }
        }
        return max;
    }

    private Map<String, Var> parseVariableNames(String text) {
        Map<String, Var> vars = new LinkedHashMap<>();
        List<Var> varList = new LinkedList<>();
        parse(text, varList);

        for (Var v : varList) {
            vars.put(v.text, v);
        }
        return vars;
    }

    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    private class Var {
        String text;
        String key;
        String strategy;
        String defaultValue;

        private String leading;
        private String trailing;
        private String[] path;

        Var(String text, String key) {
            this.text = text;
            this.key = key;

            // computing leading white spaces. only matter for expressions without ':' or ':+'
            int index = 0;
            for (int i = 0; i < key.length(); i++) {
                if (!Character.isWhitespace(key.codePointAt(i))) {
                    index = i;
                    break;
                }
            }
            leading = key.substring(0, index);

            // computing trailing white spaces. only matter for expressions without ':' or ':+'
            index = key.length();
            for (int i = key.length() - 1; i >= 0 ; i--) {
                if (!Character.isWhitespace(key.codePointAt(i))) {
                    index = i + 1;
                    break;
                }
            }

            trailing = "";
            if (index < key.length())
                trailing = key.substring(index);
            path = key.split("\\.");
        }

        @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S135", "squid:S1141", "squid:S134", "squid:S3776"})
        String build(Object object, Map<String, Object> auxillary) {
            String[] components = path;
            if (key.contains("${")) {
                components = new TextSubstitution(key).build(object, auxillary).split("\\.");
            }

            Object result = object;
            PropertyDescriptor desc;
            for (String comp : components) {
                comp = comp.trim();
                if (comp.isEmpty()) { // skip empty path component
                    result = null;
                    continue;
                }
                try {
                    if (result == null)
                        break;

                    if (result instanceof Map) {
                        result = ((Map) result).get(comp);
                        if (result == null)
                            result = auxillary.get(comp);
                    } else {
                        try {
                            desc = new PropertyDescriptor(comp, result.getClass(), "is" + capitalize(comp), null);
                            result = desc.getReadMethod().invoke(result);
                        } catch (IntrospectionException ex) {
                            Logger.suppress(ex);
                            result = null;
                        }
                        if (result == null)
                            result = auxillary.get(comp);
                    }
                } catch (Exception e) {
                    Logger.suppress(e);
                    throw new SystemException(e);
                }
            }

            String value = defaultValue;
            if (value.contains("${")) {
                value = new TextSubstitution(defaultValue).build(object, auxillary);
            }

            if (result == null) {
                // variable not defined
                if (":+".equals(strategy)) {
                    return "";
                } else if (":-".equals(strategy)) {
                    return value;
                } else if (":=".equals(strategy)) {
                    return ternary(null, value);
                } else if (":^".equals(strategy)) {
                    return scripting(result, value);
                } else if (":".equals(strategy)) {
                    return value;
                }
                return "";
            } else {
                // variable is defined
                if (":+".equals(strategy)) {
                    return value;
                } else if (":-".equals(strategy)) {
                    return "";
                } else if (":=".equals(strategy)) {
                    return ternary(result.toString(), value);
                } else if (":^".equals(strategy)) {
                    return scripting(result, value);
                } else if (":".equals(strategy)) {
                    return  leading + result.toString() + trailing;
                }
                return leading + result.toString() + trailing;
            }
        }

        private String ternary(String result, String value) {
            int p = value.indexOf('?');
            if (p < 0)
                throw new SystemException("Invalid := expression, missing '?'");
            String predicate = value.substring(0, p).trim();
            int c = value.indexOf(':', p);
            if (c < 0)
                throw new SystemException("Invalid := expression, missing ':' after '?'");
            String match = value.substring(p + 1, c);
            String not = value.substring(c + 1);
            if (result != null && result.equals(predicate)) {
                return match;
            } else {
                return not;
            }
        }

        private String scripting(Object result, String value) {
            if (script == null) {
                return "";
            } else {
                return script.eval(result, value);
            }
        }
    }

    @FunctionalInterface
    public interface Scriptable {
        String eval(Object result, String value);
    }

    public static class DefaultScriptable implements Scriptable {
        private ResourceManager resourceManager = new ResourceManager();

        public synchronized ResourceManager getResourceManager() {
            return resourceManager;
        }

        public synchronized void setResourceManager(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        public String eval(Object result, String value) {
            Closure<String> closure = (Closure<String>) resourceManager.getScripting().eval("{ it ->" + value + " }", true);
            return "" + closure.call(result);
        }
    }
}
