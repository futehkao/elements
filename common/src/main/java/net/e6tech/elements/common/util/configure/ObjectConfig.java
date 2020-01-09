/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.util.configure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.e6tech.elements.common.reflection.Primitives;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;

@SuppressWarnings({"unchecked","squid:S1192", "squid:S3776", "squid:S1168"})
public class ObjectConfig {
    public static final ObjectMapper mapper;
    private static final String RESOLVER_START = "^";

    private String resolverIndicator = RESOLVER_START;
    private String prefix = "";
    private Resolver resolver;
    private Object instance;
    private Type instanceType;
    private InstanceCreationListener instanceCreationListener;
    private String current = "";
    private ObjectConfig parent;

    static {
        mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public ObjectConfig() {
    }

    private ObjectConfig(ObjectConfig parent) {
        this.parent = parent;
    }

    public ObjectConfig prefix(String prefix) {
        this.prefix = (prefix == null) ? "" : prefix.trim();

        // trim out tailing '.'
        while (this.prefix.length() > 0 && this.prefix.charAt(this.prefix.length() - 1) == '.') {
            this.prefix = this.prefix.substring(0, this.prefix.length() - 1).trim();
        }

        if (this.prefix.length() > 0) {
            this.prefix += ".";
        }
        return this;
    }

    public ObjectConfig resolver(Resolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public ObjectConfig resolverIndicator(String str) {
        resolverIndicator = str;
        return this;
    }

    public ObjectConfig instance(Object instance) {
        this.instance = instance;
        return this;
    }

    public ObjectConfig type(Type type) {
        instanceType = type;
        return this;
    }

    public ObjectConfig creationListener(InstanceCreationListener listener) {
        instanceCreationListener = listener;
        return this;
    }

    protected ObjectConfig newChild(Object instance, Type toType) {
        return new ObjectConfig(this)
                .instance(instance)
                .type(toType)
                .resolver(resolver)
                .resolverIndicator(resolverIndicator)
                .creationListener(instanceCreationListener);
    }

    @SuppressWarnings("squid:S3776")
    public void configure(Map<?,?> map) throws Exception {
        if (instance instanceof Map) {
            configureMap(map);
            return;
        }

        Map<String, PropertyDescriptor> descriptors = new HashMap<>();
        BeanInfo info = Introspector.getBeanInfo(instance.getClass());
        for (PropertyDescriptor desc : info.getPropertyDescriptors()) {
            descriptors.put(desc.getName(), desc);
        }

        // traverse the map
        Map<String, Object> links = new LinkedHashMap<>();
        for (Map.Entry entry : map.entrySet()) {
            String key = entry.getKey().toString();
            String key2 = key + ".";
            current = "";
            if (prefix.length() > 0 && !key.startsWith(prefix) && !key2.equals(prefix)) {
                continue;  // skip because wrong prefix
            }

            if (prefix.length() > 0 && key.startsWith(prefix)) {
                key = key.substring(prefix.length()).trim();
            } else if (key2.equals(prefix)) {
                key = "";
            }

            if (key.contains(".")) {
                links.put(key, entry.getValue());
            } else if (key.length() == 0) {
                // we are dealing with instance itself
                newChild(instance, null).configure(asMap(entry.getValue()));
            } else {
                PropertyDescriptor desc = descriptors.get(key);
                if (desc != null && desc.getWriteMethod() != null) {
                    current = entry.getKey().toString();
                    desc.getWriteMethod().invoke(instance, convert(entry.getValue(), desc.getWriteMethod().getGenericParameterTypes()[0]));
                }
            }
        }

        // going through
        for (Map.Entry<String,Object> entry : links.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String[] components = key.split("\\.");
            Object obj = instance;
            for (int i = 0; i < components.length - 1; i++) {
                try {
                    Object child = Reflection.getProperty(obj, components[i].trim());
                    if (child == null) {
                        PropertyDescriptor desc = new PropertyDescriptor(components[i], obj.getClass());
                        if (Map.class.isAssignableFrom(desc.getPropertyType())) {
                            child = new LinkedHashMap<>();
                        } else {
                            child = desc.getPropertyType().getDeclaredConstructor().newInstance();
                            if (instanceCreationListener != null)
                                instanceCreationListener.instanceCreated(child, desc.getPropertyType(), child);
                        }
                        desc.getWriteMethod().invoke(obj, child);
                    }
                    obj = child;
                } catch (Exception ex) {
                    throw new SystemException(instance.getClass().getName() + "." + key + ": No such property " + components[i], ex);
                }
            }

            if (obj != null) {
                try {
                    PropertyDescriptor desc = new PropertyDescriptor(components[components.length - 1], obj.getClass());
                    if (desc == null || desc.getWriteMethod() == null)
                        break;
                    Object val = convert(value, desc.getWriteMethod().getGenericParameterTypes()[0]);
                    desc.getWriteMethod().invoke(obj, val);
                } catch (IntrospectionException ex) {
                    throw new SystemException(instance.getClass().getName() + "." + key + ": No such property " + components[components.length - 1], ex);
                }
            }
        }
    }

    private void configureMap(Map map) {
        Map instanceMap = (Map) instance;
        for (Object k : map.keySet()) {
            String key = k.toString();
            String key2 = k + ".";
            if ("".equals(prefix) || key.startsWith(prefix)) {
                String subkey = key.substring(prefix.length());
                if (instance instanceof Properties)
                    instanceMap.put(subkey, resolve(map.get(key).toString()));
                else {
                    Object val = map.get(key);
                    if (val instanceof String) {
                        val = resolve(val.toString());
                    }
                    instanceMap.put(subkey, val);
                }
            } else if (key2.equals(prefix)) {
                newChild(instance, null).configureMap(asMap(map.get(key)));
            }
        }
    }

    private Object resolve(String value) {
        if (resolver == null || value == null)
            return value;
        String str = value.trim();
        if (str.startsWith(resolverIndicator)) {
            str = str.substring(resolverIndicator.length()).trim();
            return resolver.resolve(str);
        } else {
            return value;
        }
    }

    private Class getRawType(Type toType) {
        Class cls = null;
        if (toType instanceof Class) {
            cls = (Class) toType;
        } else if (toType instanceof ParameterizedType) {
            cls = (Class) ((ParameterizedType) toType).getRawType();
        } else if (toType instanceof GenericArrayType) {
            cls = getComponentClass((GenericArrayType) toType);
        } else if (toType instanceof TypeVariable && instanceType instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) instanceType;
            int index = 0;
            for (TypeVariable tv : ((Class) pType.getRawType()).getTypeParameters()) {
                if (tv.getName().equals(((TypeVariable) toType).getName())) {
                    Type type = pType.getActualTypeArguments()[index];
                    cls = getRawType(type);
                    break;
                }
                index ++;
            }
        }

        if (cls == null)
            throw new IllegalArgumentException("Cannot deduce class for type=" + toType + ", path=" + getPath());

        return cls;
    }

    private Class getComponentClass(GenericArrayType arrayType) {
        Class cls;
        Type componentType = arrayType.getGenericComponentType();
        if (componentType instanceof Class) {
            cls = (Class) componentType;
        } else if (componentType instanceof ParameterizedType) {
            cls = (Class) ((ParameterizedType) componentType).getRawType();
        } else {
            throw new IllegalArgumentException("Cannot deduce component class for type=" + arrayType + ", path=" + getPath());
        }
        return cls;
    }

    private Object convert(Object value, Type toType) throws Exception {
        Class cls = getRawType(toType);

        if (cls.isArray() || toType instanceof GenericArrayType) {
            return convertToArray(asCollection(value), toType);
        } else if (String.class.isAssignableFrom(cls)) {
            String str = (value == null) ? null : value.toString();
            return resolve(str);
        } else if (Map.class.isAssignableFrom(cls)) {
            return convertToMap(asMap(value), toType);
        } else if (Collection.class.isAssignableFrom(cls)) {
            return convertToCollection(asCollection(value), toType);
        } else {
            if (value instanceof Map) {
                // converting map to cls.
                Object child =  (Map.class.isAssignableFrom(cls)) ? new LinkedHashMap<>() : cls.getDeclaredConstructor().newInstance();
                newChild(child, toType).configure(asMap(value));
                return child;
            } else {
                if (value == null && cls.isPrimitive()) {
                    return Primitives.defaultValue(cls);
                } else {
                    String str = mapper.writeValueAsString(value);
                    return mapper.readValue(str, cls);
                }
            }
        }
    }

    private Map asMap(Object value) {
        if (value == null)
            return null;
        if (value instanceof Map) {
            return (Map) value;
        } else {
            throw new IllegalArgumentException("Cannot cast object of class=" + value.getClass() + " to Map, path=" + getPath());
        }
    }

    private Collection asCollection(Object value) {
        if (value == null)
            return null;
        if (value.getClass().isArray()) {
            List list = new ArrayList();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        } else if (value instanceof Collection) {
            return (Collection) value;
        } else {
            throw new IllegalArgumentException("Cannot cast object of class=" + value.getClass() + " to Map, path=" + getPath());
        }
    }

    private Collection convertToCollection(Collection collection, Type toType) throws Exception {
        if (collection == null)
            return null;
        Class rawType = getRawType(toType);

        Collection converted;
        if (List.class.isAssignableFrom(rawType)) {
            converted = new ArrayList();
        } else if (Set.class.isAssignableFrom(rawType)) {
            converted = new HashSet();
        } else {
            throw new IllegalArgumentException("Cannot convert object of class=" + collection.getClass() + " to Collection, path=" + getPath());
        }

        if (toType instanceof Class) {
            converted.addAll(collection);
        } else {
            ParameterizedType parametrized = (ParameterizedType) toType;
            Type componentType = parametrized.getActualTypeArguments()[0];
            for (Object component : collection) {
                converted.add(convert(component, componentType));
            }
        }
        return converted;
    }

    private Object convertToArray(Collection collection, Type toType) throws Exception {
        if (collection == null)
            return null;
        Object converted;
        Type componentType;
        Class rawType = null;
        if (toType instanceof Class) {
            rawType = ((Class) toType).getComponentType();
            componentType = rawType;
        } else if (toType instanceof ParameterizedType){
            ParameterizedType parametrized = (ParameterizedType) toType;
            componentType = parametrized.getRawType();
            rawType = (Class) componentType;
        } else if (toType instanceof GenericArrayType){
            componentType = ((GenericArrayType) toType).getGenericComponentType();
            rawType = getRawType(toType);
        } else {
            throw new IllegalArgumentException("Cannot deduce component class for type=" + toType + ", path=" + getPath());
        }

        converted = Array.newInstance(rawType, collection.size());
        Iterator iterator = collection.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            Object member = iterator.next();
            Array.set(converted, index, convert(member, componentType));
            index++;
        }
        return converted;

    }

    private Map convertToMap(Map<?,?> map, Type toType) throws Exception {
        if (map == null)
            return null;
        Map converted = new LinkedHashMap();
        if (toType instanceof Class) {
            converted.putAll(map);
        } else {
            ParameterizedType parametrized = (ParameterizedType) toType;
            Type valueType = parametrized.getActualTypeArguments()[1];
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(entry.getKey().toString(), convert(entry.getValue(), valueType));
            }
        }
        return converted;
    }

    protected String getPath() {
        StringBuilder builder = new StringBuilder();
        if (parent != null) {
            builder.append(parent.getPath()).append('.');
        }
        builder.append(current);
        return builder.toString();
    }

    @FunctionalInterface
    public interface InstanceCreationListener {
        void instanceCreated(Object value, Class toType, Object instance);
    }
}
