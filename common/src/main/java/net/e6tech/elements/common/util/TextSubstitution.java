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


package net.e6tech.elements.common.util;

import net.e6tech.elements.common.logging.Logger;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.Locale.ENGLISH;

/**
 * Created by futeh.
 *
 *  ${var:default} expands to default if var not defined, to var if var is defined.
 *  ${var:+default}  expands to "" if var is not defined, to default if var is defined.
 *  ${var:-default} expands to default if var not defined, to "" if var is defined.
 */
public class TextSubstitution {

    private Map<String, Var> variables = new LinkedHashMap<>();
    private Map<String, Var> declared = new LinkedHashMap<>();
    private String template;

    // NOTE.  If template ever gets modified, parseVariableNames needs to be called.

    public TextSubstitution(String template) {
        this.template = template;
        parseVariableNames(template);
    }

    public TextSubstitution(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int len = 0;
        while ((len = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, len);
        }
        this.template = builder.toString();
        parseVariableNames(template);
        reader.close();
    }

    public String getTemplate() {
        return template;
    }

    public TextSubstitution declare(String var) {
        declared.put(var, new Var(var));
        return this;
    }

    public String build(Object binding) {
        if (template == null)
            return "";
        String text = template;
        for (Map.Entry<String, Var> entry : variables.entrySet()) {
            Var var = entry.getValue();
            text = replaceVariable(entry.getKey(), var.build(binding), text);
        }
        return text;
    }

    private String replaceVariable(String key, String value, String text) {
        return text.replace("${" + key + "}", value);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S135", "squid:S3776"})
    private void parseVariableNames(String text) {
        variables.clear();
        List<String> expressions = new LinkedList<>();
        int pos = 0;
        int max = text.length();
        while (pos < max) {
            pos = text.indexOf("${", pos);
            if (pos == -1)
                break;
            int end = text.indexOf('}', pos + 2);
            if (end == -1)
                break;
            String name = text.substring(pos + 2, end);
            expressions.add(name);
            pos = end + 1;
        }

        for (String expression : expressions) {
            String key = expression;
            String defVal = null;
            String strategy = null;
            if (expression.contains(":+")) {
                int index = expression.indexOf(":+");
                if (index >= 0) {
                    key = expression.substring(0, index).trim();
                    defVal = expression.substring(index + 2);
                    strategy = ":+";
                }
            } else if (expression.contains(":-")) {
                int index = expression.indexOf(":-");
                if (index >= 0) {
                    key = expression.substring(0, index).trim();
                    defVal = expression.substring(index + 2);
                    strategy = ":-";
                }
            } else if (expression.contains(":")) {
                int index = expression.indexOf(':');
                if (index >= 0) {
                    key = expression.substring(0, index).trim();
                    defVal = expression.substring(index + 1);
                    strategy = ":";
                }
            }

            // computing leading white spaces. only matter for expressions without ':' or ':+'
            int index = 0;
            for (int i = 0; i < key.length(); i++) {
                if (!Character.isWhitespace(key.codePointAt(i))) {
                    index = i;
                    break;
                }
            }
            String leadingSpaces = key.substring(0, index);

            // computing trailing white spaces. only matter for expressions without ':' or ':+'
            index = key.length();
            for (int i = key.length() - 1; i >=0 ; i--) {
                if (!Character.isWhitespace(key.codePointAt(i))) {
                    index = i + 1;
                    break;
                }
            }
            String trailingSpaces = "";
            if (index < key.length())
                trailingSpaces = key.substring(index);

            String[] tokens = key.split("\\.");
            Var var = declared.get(tokens[0]);
            if (var == null) {
                var = new Var(expression);
            } else {
                var = new Var(var); // essentially clone var.
            }

            var.leading = leadingSpaces;
            var.trailing = trailingSpaces;
            var.defaultValue = defVal;
            var.strategy = strategy;
            var.path = tokens;
            variables.put(expression, var);
        }
    }

    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    private static class Var {
        String name;
        String leading;
        String trailing;
        String defaultValue;
        String strategy;
        String[] path;

        Var(String name) {
            this.name = name;
        }

        Var(Var var) {
            this.name = var.name;
            this.leading = var.leading;
            this.trailing = var.trailing;
            this.defaultValue = var.defaultValue;
            this.strategy = var.strategy;
            this.path = var.path;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S135", "squid:S1141", "squid:S134", "squid:S3776"})
        public String build(Object object) {
            Object result = object;
            PropertyDescriptor desc;
            for (String comp : path) {
                comp = comp.trim();
                if (comp.isEmpty()) {
                    result = null;
                    continue;
                }
                try {
                    if (result == null)
                        break;
                    if (result instanceof Map) {
                        result = ((Map) result).get(comp);
                    } else {
                        try {
                            desc = new PropertyDescriptor(comp, result.getClass(), "is" + capitalize(comp), null);
                            result = desc.getReadMethod().invoke(result);
                        } catch (IntrospectionException ex) {
                            Logger.suppress(ex);
                            result = null;
                        }
                    }
                } catch (Exception e) {
                    Logger.suppress(e);
                    throw new SystemException(e);
                }
            }

            if (result == null) {
                // variable not defined
                if (":+".equals(strategy)) {
                    return "";
                } else if (":-".equals(strategy)) {
                    return (defaultValue == null) ? "" : defaultValue;
                } else if (":".equals(strategy)) {
                    return (defaultValue == null) ? "" : defaultValue;
                }
                return "";
            } else {
                // variable is defined
                if (":+".equals(strategy)) {
                    return defaultValue;
                } else if (":-".equals(strategy)) {
                    return "";
                } else if (":".equals(strategy)) {
                    return result.toString();
                }
                return leading + result.toString() + trailing;
            }
        }
    }
}
