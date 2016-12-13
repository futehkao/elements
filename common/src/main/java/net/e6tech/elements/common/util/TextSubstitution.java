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
 *  ${var:-default}  expands to "" if var is not defined, to default if var is defined.
 */
public class TextSubstitution {

    private Map<String, Var> variables = new LinkedHashMap<>();
    private Map<String, Var> declared = new LinkedHashMap<>();
    private String template;

    public TextSubstitution(String template) {
        this.template = template;
    }

    public TextSubstitution(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int len = 0;
        while ((len = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, len);
        }
        this.template = builder.toString();
        reader.close();
    }

    public TextSubstitution declare(String var) {
        declared.put(var, new Var(var));
        return this;
    }

    public String build(Object binding, String ... declares) {
        if (template == null) return "";
        if (declares != null) {
            for (String var : declares) {
                declared.put(var, new Var(var));
            }
        }
        parseVariableNames(template);
        String text = template;
        for (Map.Entry<String, Var> entry : variables.entrySet()) {
            Var var = entry.getValue();
            text = replaceVariable(entry.getKey(), var.build(binding), text);
        }
        return text;
    }

    private String replaceVariable(String key, String value, String text) {
        return text.replaceAll("\\$\\{" + key + "}", value);
    }

    private void parseVariableNames(String text) {
        List<String> names = new LinkedList<>();
        int pos = 0, max = text.length();
        while (pos < max) {
            pos = text.indexOf("${", pos);
            if (pos == -1)
                break;
            int end = text.indexOf("}", pos + 2);
            if (end == -1)
                break;
            String name = text.substring(pos + 2, end);
            names.add(name);
            pos = end + 1;
        }

        for (String name : names) {
            String key = name.trim();
            String defVal = null;
            String strategy = null;
            if (name.contains(":-")) {
                int index = name.indexOf(":-");
                if (index > 0) {
                    key = name.substring(0, index).trim();
                    defVal = name.substring(index + 2).trim();
                    strategy = ":-";
                }
            } else if (name.contains(":")) {
                int index = name.indexOf(":");
                if (index > 0) {
                    key = name.substring(0, index).trim();
                    defVal = name.substring(index + 1).trim();
                    strategy = ":";
                }
            }

            String[] tokens = key.split("\\.");
            Var var = declared.get(tokens[0]);
            if (var == null) {
                // throw new IllegalArgumentException("Unrecognized variable: " + tokens[0]);
                var = new Var(name);
            } else {
                var = var.clone();
            }
            var.defaultValue = defVal;
            var.strategy = strategy;
            var.path = tokens;
            variables.put(name, var);
        }
    }

    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    private static class Var implements Cloneable {
        String name;
        String defaultValue;
        String strategy;
        String[] path;

        Var(String name) {
            this.name = name;
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

        public String build(Object object) {
            Object result = object;
            PropertyDescriptor desc;
            for (String comp : path) {
                try {
                    if (result == null) break;
                    if (result instanceof Map) {
                        result = ((Map) result).get(comp);
                    } else {
                        desc = new PropertyDescriptor(comp, result.getClass(), "is" + capitalize(comp), null);
                        result = desc.getReadMethod().invoke(result);
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            if (result == null) {
                if (":-".equals(strategy)) {
                    return "";
                } else if (":".equals(strategy)) {
                    return (defaultValue == null) ? "" : defaultValue;
                }
                return "";
            } else {
                if (":-".equals(strategy)) {
                    return defaultValue;
                } else if (":".equals(strategy)) {
                    return result.toString();
                }
                return result.toString();
            }
        }

        public Var clone() {
            try {
                return (Var) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
