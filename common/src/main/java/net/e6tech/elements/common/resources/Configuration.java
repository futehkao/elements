/*
 * Copyright 2015-2019 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.ObjectConverter;
import net.e6tech.elements.common.reflection.Primitives;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by futeh.
 */
@SuppressWarnings({"unchecked", "squid:S3776", "squid:S134", "squid:MethodCyclomaticComplexity"})
public class Configuration extends LinkedHashMap<String, Object> {

    private static Logger logger = Logger.getLogger();
    private static final String NO_SUCH_PROPERTY = ": No such property ";
    private static final String BEGIN = "${";
    private static final String END = "}";
    private static final YamlConstructor yamlConstructor = new YamlConstructor();

    private Properties properties = new Properties();
    private Map<String, List<Reference>> references = new HashMap<>();  // reformatMap() for description of usage

    public Configuration() {
    }

    public Configuration(Properties properties) {
        if (properties != null)
            this.properties = properties;
    }

    public static Map<String, List<String>> defineEnvironments(String str) {
        Yaml yaml = new Yaml(new YamlConstructor());
        return yaml.load(str);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Configuration))
            return false;
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public static Yaml newYaml() {
        return new Yaml(yamlConstructor);
    }

    public Configuration loadFile(String file) throws IOException {
        Path path = FileSystems.getDefault().getPath(file);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            return load(builder.toString());
        }
    }

    public Configuration load(Configuration config) {
        properties.putAll(config.properties);
        merge(this, config);
        for (Map.Entry<String, List<Reference>> entry : config.references.entrySet()) {
            List<Reference> current = references.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            current.addAll(entry.getValue());
        }
        return this;
    }

    public Configuration load(String configStr) {
        String text = configStr;
        Yaml yaml = newYaml();
        if (text.contains(BEGIN)) {
            text = parse(text, true);
        }
        Iterable<Object> iterable = yaml.loadAll(text);
        loadYaml(iterable);

        references.clear();
        reformatMap("", this, references);
        return this;
    }

    private void loadYaml(Iterable<Object> iterable) {
        List<Map<String, Object>> maps = new LinkedList<>();
        for (Object obj: iterable) {
            if (obj instanceof Map) {
                maps.add((Map)obj);
            } else {
                Map map = new LinkedHashMap<>();
                map.put(obj.toString(), null);
                maps.add(map);
            }
        }

        for (Map<String, Object> map : maps) {
            if (map != null)
                merge(this, map);
        }
    }

    private static class Reference implements Serializable {
        String key;
        String lookup;
        Reference(String key, String lookup) {
            this.key = key;
            this.lookup = lookup;
        }
    }

    private void merge(Map<String, Object> map1, Map<String, Object> map2) {
        for (Map.Entry<String, Object> entry : map2.entrySet()) {
            if (map1.get(entry.getKey()) != null) {
                Object existing = map1.get(entry.getKey());
                if (existing instanceof Map && entry.getValue() instanceof Map) {
                    merge((Map<String, Object>)existing, (Map<String, Object>)entry.getValue());
                } else {
                    map1.put(entry.getKey(), entry.getValue());
                }
            } else {
                map1.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public Path dump(String file) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : keySet()) {
            map.put(key, get(key));
        }
        Yaml yaml = new Yaml();
        Path path = Paths.get(file);
        yaml.dump(map, Files.newBufferedWriter(path, StandardCharsets.UTF_8));
        return path;
    }

    public <T> T get(String key) {
        Object object = super.get(key);
        if (object instanceof String) {
            String value = (String) object;
            if (value.contains(BEGIN)) {
                Yaml yaml = newYaml();
                value = parse(value, false);
                Map<String, Object> map = yaml.load( key + ": " + value);
                put(key, map.get(key));
                return (T) map.get(key);
            } else {
                return (T) object;
            }
        } else {
            return (T) object;
        }
    }

    private String parse(String valueStr, boolean useProperties) {
        String value = valueStr;
        int start = value.indexOf(BEGIN);

        if (start >= 0) {
            boolean loop = true;
            while (loop) {
                int end = value.indexOf(END, start + BEGIN.length());
                if (end < 0) { // no more END token, get out of the loop
                    loop = false;
                } else {
                    int nested = value.indexOf(BEGIN, start + BEGIN.length());
                    if (nested < end && nested >= 0) { // nested BEGIN token
                        String substring = parse(value.substring(nested), useProperties);
                        if (substring.startsWith(BEGIN)) {
                            // need to skip
                            int lineTerm = substring.indexOf('\n');
                            String line = substring;
                            if (lineTerm > 0)
                                line = substring.substring(0, lineTerm);

                            int begin = BEGIN.length();
                            int count = 2;
                            int matchingEnd = 0;
                            while (count > 0) {
                                int nextEnd = line.indexOf(END, begin);
                                int nextBegin = line.indexOf(BEGIN, begin);
                                if (nextEnd < 0) {
                                    // done
                                    matchingEnd = line.length();
                                } else {
                                    if (nextBegin > 0 && nextBegin < nextEnd) {
                                        begin = nextBegin + BEGIN.length();
                                        count ++;
                                    } else {
                                        count --;
                                        matchingEnd = nextEnd;
                                    }
                                }
                            }
                            start += matchingEnd;
                        }
                        value = value.substring(0, nested) + substring;
                        loop = true;
                    } else {
                        String substring = value.substring(start + BEGIN.length(), end);
                        String remain = (end + END.length() == value.length()) ? "" : value.substring(end + END.length());
                        value = value.substring(0, start) + resolve(substring, useProperties) + remain;
                        start = value.indexOf(BEGIN, start + BEGIN.length());
                        loop = start >= 0;
                    }
                }
            }
        }

        return value;
    }

    private String resolve(String value, boolean useProperties) {
        String obj = (useProperties) ? properties.getProperty(value.trim()) : super.get(value.trim()).toString();
        if (obj == null)
            return "${" + value + "}";
        return obj;
    }

    public void configure(Object object) {
        configure(object, null, null, null);
    }

    public void configure(Object object, String prefixArg, Resolver resolver, ObjectConverter.InstanceCreationListener listener) {
        configure(new ObjectConverter(resolver, listener), object, prefixArg);
    }

    // subOnly is true when running through substitution.
    @SuppressWarnings("squid:S1141")
    public void configure(ObjectConverter converter, Object object, String prefixArg) {
        if (object == null)
            throw new IllegalArgumentException();
        String prefix = prefixArg;
        BeanInfo info;
        try {
            if (get(prefix) instanceof Map && !(object instanceof Map)) {
                configureWithMap(converter, object, get(prefix));
            }

            if (prefix != null)
                prefix = prefix.trim();
            if (prefix != null && !prefix.endsWith("."))
                prefix += ".";
            if (prefix == null || ".".equals(prefix))
                prefix = "";

            if (object instanceof Map) {
                Map map = (Map) object;
                for (String key : keySet()) {
                    if ("".equals(prefix) || key.startsWith(prefix)) {
                        String subkey = key.substring(prefix.length());
                        if (object instanceof Properties)
                            map.put(subkey, get(key).toString());
                        else
                            map.put(subkey, get(key));
                    } else if (key.equals(prefixArg) && get(prefixArg) instanceof Map) {
                        Map val = get(prefixArg);
                        val.forEach((k, v) -> {
                            if (converter.getResolver() != null && v instanceof String && v.toString().startsWith("^"))
                                map.put(k, converter.getResolver().resolve(v.toString()));
                            else
                                map.put(k, v);
                        });
                    }
                }

                if (converter.getResolver() != null) {
                    List<Reference> referenceList = references.get(prefixArg);
                    if (referenceList != null) {
                        for (Reference reference : referenceList) {
                            map.put(reference.key, converter.getResolver().resolve(reference.lookup));
                        }
                    }
                }
                return;
            }

            // getting setters and getter from the object
            info = Introspector.getBeanInfo(object.getClass());
            Map<String, PropertyDescriptor> setters = new LinkedHashMap<>();
            Map<String, PropertyDescriptor> getters = new LinkedHashMap<>();
            for (PropertyDescriptor desc : info.getPropertyDescriptors()) {
                if (desc.getWriteMethod() != null) {
                    String key = prefix + desc.getName();
                    Object value = get(key);
                    if (value != null)
                        setters.put(key, desc);
                }

                if (desc.getReadMethod() != null) {
                    String key = prefix + desc.getName();
                    getters.put(key, desc);
                }
            }

            // getting a list of relevant properties from Configuration
            Set<String> applicableKeys = new HashSet<>();
            for (String key : keySet()) {
                if (key.startsWith(prefix)) {
                    int index = key.indexOf('.', prefix.length());
                    if (index < 0)
                        applicableKeys.add(key); // if index > 0, key includes subfields
                }
            }

            // Setting object's property
            Set<String> configured = new HashSet<>();
            for (Map.Entry<String, PropertyDescriptor> entry : setters.entrySet()) {
                PropertyDescriptor desc = entry.getValue();
                Object value = get(entry.getKey());
                if (!Map.class.isAssignableFrom(desc.getPropertyType())
                        && value instanceof Map && desc.getReadMethod() != null && desc.getReadMethod().invoke(object) != null) { // let the next section configure it.
                    applicableKeys.remove(entry.getKey());
                    continue;
                }
                setValueForOwner(converter, object, desc, value);
                applicableKeys.remove(entry.getKey());
                configured.add(entry.getKey());
            }

            if (!applicableKeys.isEmpty()) {
                logger.warn("object {} does not have properties: {}", object.getClass(), applicableKeys);
            }

            // recurse into fields
            for (String key : keySet()) {
                if (configured.contains(key))
                    continue;
                if ("".equals(prefix) || key.startsWith(prefix)) {
                    boolean shouldRecurse = true;
                    String subkey = key.substring(prefix.length());
                    String fieldKey;
                    if (subkey.contains(".")) {
                        fieldKey = subkey.substring(0, subkey.indexOf('.'));
                    } else {
                        fieldKey = subkey;
                        Object val = get(key);
                        if (!(val instanceof Map))
                            shouldRecurse = false;
                    }
                    PropertyDescriptor desc = getters.get(prefix + fieldKey);
                    if (desc != null && desc.getReadMethod() != null) {
                        Object val = desc.getReadMethod().invoke(object);
                        if (shouldRecurse) {
                            Class fieldClass = desc.getReadMethod().getReturnType();
                            // trying to create a map or properties instance
                            if (val == null && Map.class.isAssignableFrom(fieldClass) && desc.getWriteMethod() != null) {
                                if (Properties.class.isAssignableFrom(fieldClass)) {
                                    val = new Properties();
                                } else {
                                    try {
                                        val = fieldClass.getConstructor().newInstance();
                                    } catch (Exception th) {
                                        Logger.suppress(th);
                                        val = new LinkedHashMap<>();
                                    }
                                }
                                try {
                                    desc.getWriteMethod().invoke(object, val);
                                } catch (Exception th) {
                                    Logger.suppress(th);
                                    val = null;
                                }
                            }
                            if (val != null)
                                configure(converter, val, prefix + fieldKey);
                        }
                    }
                }
            }

            // recurse into substitution
            resolveReferences(object, prefix, (Resolver) converter.getResolver());

        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }


    // subOnly is true when running through substitution.
    private void resolveReferences(Object object, String prefixArg, Resolver resolver) {
        if (object == null)
            throw new IllegalArgumentException();
        String prefix = prefixArg;
        try {
            if (prefix != null)
                prefix = prefix.trim();
            if (prefix != null && !prefix.endsWith("."))
                prefix += ".";
            if (prefix == null || ".".equals(prefix))
                prefix = "";

            // recurse into substitution
            if (resolver != null) {
                String substitutionObjectKey = prefix;
                while (substitutionObjectKey.endsWith("."))
                    substitutionObjectKey = substitutionObjectKey.substring(0, substitutionObjectKey.length() - 1);
                for (Map.Entry<String, List<Reference>> entry: references.entrySet()) {
                    // setting substitution.
                    if (entry.getKey().equals(substitutionObjectKey)) {
                        List<Reference> referenceList = entry.getValue();
                        for (Reference reference : referenceList) {
                            if (object instanceof Map) {
                                ((Map) object).put(reference.key, resolver.resolve(reference.lookup));
                            } else {
                                PropertyDescriptor desc = new PropertyDescriptor(reference.key, object.getClass());
                                if (desc != null && desc.getWriteMethod() != null) {
                                    desc.getWriteMethod().invoke(object, resolver.resolve(reference.lookup));
                                }
                            }
                        }
                    } else if (entry.getKey().contains(prefix)) {
                        // recurse into substitute
                        String subkey = entry.getKey().substring(prefix.length());
                        String fieldKey;
                        if (subkey.contains(".")) {
                            fieldKey = subkey.substring(0, subkey.indexOf('.'));
                        } else {
                            fieldKey = subkey;
                        }

                        PropertyDescriptor desc = new PropertyDescriptor(fieldKey, object.getClass());
                        if (desc != null && desc.getReadMethod() != null) {
                            Object val = desc.getReadMethod().invoke(object);
                            if (val != null) {
                                resolveReferences(val, prefix + fieldKey, resolver);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }

    public void configureWithMap(Object object, Map<String, Object> map, Resolver resolver, ObjectConverter.InstanceCreationListener listener) {
        configureWithMap(new ObjectConverter(resolver, listener), object, map);
    }

    // object is the owner
    @SuppressWarnings("squid:S1141")
    protected void configureWithMap(ObjectConverter converter, Object object, Map<String, Object> map) {
        // getting a list of relevant properties from Configuration
        Set<String> applicableKeys = new HashSet<>();
        applicableKeys.addAll(map.keySet());

        try {
            // going throw simple keys that match the object's property name
            BeanInfo info = Introspector.getBeanInfo(object.getClass());
            for (PropertyDescriptor desc : info.getPropertyDescriptors()) {
                if (desc.getWriteMethod() != null && map.containsKey(desc.getName())) {
                    Object val = map.get(desc.getName());
                    setValueForOwner(converter, object, desc, val);
                    applicableKeys.remove(desc.getName());
                }
            }

            // looking for keys that looks like x.y.z etc.
            Iterator<String> iterator = applicableKeys.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key.contains(".")) {
                    String[] path = key.split("\\.");
                    Object obj = null;
                    try {
                        if (path[0].trim().length() > 0)
                            obj = Reflection.getProperty(object, path[0]);
                    } catch (Exception ex) {
                        throw new SystemException(object.getClass().getName() + "." + key + NO_SUCH_PROPERTY + path[0], ex);
                    }
                    for (int i = 1; i < path.length - 1; i++) {
                        if (obj == null)
                            break;
                        try {
                            obj = Reflection.getProperty(obj, path[i].trim());
                        } catch (Exception ex) {
                            throw new SystemException(object.getClass().getName() + "." + key + NO_SUCH_PROPERTY + path[i], ex);
                        }
                    }
                    if (obj != null) {
                        try {
                            PropertyDescriptor desc = new PropertyDescriptor(path[path.length - 1], obj.getClass());
                            if (desc.getWriteMethod() == null)
                                break;
                            Object val = map.get(key);
                            setValueForOwner(converter, obj, desc, val);
                            iterator.remove();
                        } catch (IntrospectionException ex) {
                            throw new SystemException(object.getClass().getName() + "." + key + NO_SUCH_PROPERTY + path[path.length - 1], ex);
                        }
                    }
                }
            }

            if (!applicableKeys.isEmpty()) {
                logger.warn("object {} does not have properties: {}", object.getClass().getName(), applicableKeys);
            }

        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }

    private void setValueForOwner(ObjectConverter converter, Object owner, PropertyDescriptor desc, Object val)
            throws IOException, InvocationTargetException, IllegalAccessException {
        Method method = desc.getWriteMethod() != null ? desc.getWriteMethod() : desc.getReadMethod();

        Object value = null;
        if (desc.getReadMethod() != null) {
            Object curr = desc.getReadMethod().invoke(owner);
            if (curr != null) {
                if (val instanceof Map && !(curr instanceof Map)) {
                    configureWithMap(converter, curr, (Map) val);
                    return;
                } else {
                    value = converter.convert(val, desc.getReadMethod());
                }
            }
        }

        if (value == null)
            value = converter.convert(val, method);
        if (converter.getListener() != null && val != null)
            converter.getListener().instanceCreated(value, desc.getPropertyType(), value);
        try {
            if (Primitives.isPrimitive(desc.getPropertyType()) && value == null)
                value = Primitives.defaultValue(desc.getPropertyType());
            desc.getWriteMethod().invoke(owner, value);
        } catch (IllegalArgumentException ex)  {
            throw new SystemException(ex);
        }
    }

    // This method is called only during load.
    // reformatMap goes through a map and tries to detect if a value is a string and starts with ^.  When it is found,
    // the entry will be removed and added to references
    // There are two cases of specifying references.  First, one can use the do notation and specify the path,
    // e.g. a.b.c = ^something
    // Second, a reference can be embbeded in a map as in
    // a:
    //   b: ^something.
    // reformatMap would remove references from a nested map and convert it to dot notation because
    // when de-serializing a map into an object, a reference would cause the serialization to failed since
    // we rely on ObjectMapper to convert a map into an object and it would not know what to do with a reference.
    private void reformatMap(String prefixArg,  Map<String, Object> map, Map<String, List<Reference>> substitutions) {
        String prefix = prefixArg;
        if (prefix != null)
            prefix = prefix.trim();
        if (prefix == null)
            prefix = "";

        List<String> toBeRemoved = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof  String) {
                String entryKey = entry.getKey();
                String value = ((String) entry.getValue()).trim();
                if (value.startsWith("^")) {
                    String lookup = value.substring(1).trim();
                    Reference reference = new Reference(entry.getKey(), lookup);
                    List<Reference> referenceList = null;
                    if ("".equals(prefix)) {
                        if (entryKey.contains(".")) {
                            int lastIndex = entryKey.lastIndexOf('.');
                            String owner = entryKey.substring(0, lastIndex);
                            String key = entryKey.substring(lastIndex + 1);
                            reference = new Reference(key, lookup);
                            referenceList = substitutions.computeIfAbsent(owner, k -> new ArrayList<>());
                        } else {
                            throw new IllegalArgumentException("top level key cannot use '^' reference.");
                        }
                    } else {
                        referenceList = substitutions.computeIfAbsent(prefix, k -> new ArrayList<>());
                    }
                    referenceList.add(reference);
                    toBeRemoved.add(entry.getKey());
                }
            } else if (entry.getValue() instanceof  Map) {
                StringBuilder builder = new StringBuilder();
                builder.append(prefix);
                if (!"".equals(prefix))
                    builder.append(".");
                builder.append(entry.getKey());
                reformatMap(builder.toString(), (Map) entry.getValue(), substitutions);
            } else if (entry.getValue() instanceof  Collection) {
                // we don't support collection yet.
            }
        }

        for (String key: toBeRemoved) {
            map.remove(key);
        }
    }

    public static class YamlConstructor extends Constructor {

        public YamlConstructor() {
            super(new LoaderOptions());
            this.yamlConstructors.put(Tag.FLOAT, new BigDecimalConstructor());
            this.yamlConstructors.put(Tag.INT, new LongConstructor());
        }

        private class BigDecimalConstructor extends AbstractConstruct {
            public Object construct(Node node) {
                String value = constructScalar((ScalarNode) node).replaceAll("_", "");
                int sign = +1;
                char first = value.charAt(0);
                if (first == '-') {
                    sign = -1;
                    value = value.substring(1);
                } else if (first == '+') {
                    value = value.substring(1);
                }
                BigDecimal decimal = new BigDecimal(value);
                if (sign > 0)
                    return decimal;
                else return decimal.negate();

            }
        }

        private class LongConstructor extends AbstractConstruct {
            public Object construct(Node node) {
                String value = constructScalar((ScalarNode) node).replaceAll("_", "");
                return Long.parseLong(value);
            }
        }
    }

    @FunctionalInterface
    public interface Resolver extends net.e6tech.elements.common.reflection.Resolver {
    }
}
