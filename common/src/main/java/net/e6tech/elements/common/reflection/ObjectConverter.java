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

package net.e6tech.elements.common.reflection;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.std.*;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.ClassUtil;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class ObjectConverter {

    public static final  ObjectMapper mapper;
    private Resolver resolver;
    private InstanceCreationListener listener;
    private ObjectMapper objectMapper;

    static {
        mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public static Class loadClass(ClassLoader loader, String name) throws ClassNotFoundException {
        if (Primitives.isPrimitive(name))
            return Primitives.get(name);
        return loader.loadClass(name);
    }

    public static Type conversionType(Method getterOrSetter) throws IOException {
        Type returnType = getterOrSetter.getGenericReturnType();
        if (!returnType.equals(Void.TYPE)) {
            // getter
            if (getterOrSetter.getParameterTypes().length != 0) {
                throw new IllegalArgumentException("Method " + getterOrSetter.getName() + " must be a getter");
            }
            return returnType;
        } else {
            Type[] parameters = getterOrSetter.getGenericParameterTypes();
            if (parameters.length != 1)
                throw new IllegalArgumentException("Method " + getterOrSetter.getName() + " must be a setter");
            return parameters[0];
        }
    }

    protected static Object resolve(Resolver resolver, Object value) {
        if (resolver != null && value instanceof String) {
            String exp = ((String) value).trim();
            if (exp.startsWith("^")) {
                while (exp.startsWith("^"))
                    exp = exp.substring(1);
                return resolver.resolve(exp);
            }
        }
        return value;
    }

    public ObjectConverter() {
    }

    public ObjectConverter(Resolver resolver) {
        this.resolver = resolver;
    }

    public ObjectConverter(Resolver resolver, InstanceCreationListener listener) {
        this.resolver = resolver;
        this.listener = listener;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    public InstanceCreationListener getListener() {
        return listener;
    }

    public void setListener(InstanceCreationListener listener) {
        this.listener = listener;
    }

    public Object convert(Object from, Method getterOrSetter, InstanceCreationListener listener) throws IOException {
        this.listener = listener;
        return convert(from, conversionType(getterOrSetter));
    }

    public Object convert(Object from, Method getterOrSetter) throws IOException {
        return convert(from, conversionType(getterOrSetter));
    }

    public Object convert(Object from, Method getterOrSetter, Resolver resolver, InstanceCreationListener listener) throws IOException {
        this.resolver = resolver;
        this.listener = listener;
        objectMapper = null;
        return convert(from, conversionType(getterOrSetter));
    }

    public Object convert(Object from, Field field) throws IOException {
        return convert(from, field.getGenericType());
    }

    public Object convert(Object from, Field field, InstanceCreationListener listener) throws IOException {
        this.listener = listener;
        return convert(from, field.getGenericType());
    }

    public Object convert(Object from, Field field, Resolver resolver, InstanceCreationListener listener) throws IOException {
        this.resolver = resolver;
        this.listener = listener;
        objectMapper = null;
        return convert(from, field.getGenericType());
    }

    public Object convert(Object from, Type toType, InstanceCreationListener listener) throws IOException {
        this.listener = listener;
        return convert(from, toType);
    }

    @SuppressWarnings({"squid:S134", "squid:S3776"})
    public Object convert(Object from, Type toType) throws IOException {
        if (from == null)
            return null;
        Object converted;
        ObjectMapper objectMapper = createObjectMapper();

        if (toType instanceof Class) {
            converted = convert(objectMapper, from, (Class) toType);
        } else {
            JavaType ctype = TypeFactory.defaultInstance().constructType(toType);
            String str = objectMapper.writeValueAsString(from);
            converted = objectMapper.readValue(str, ctype);
            ParameterizedType parametrized = (ParameterizedType) toType;
            Class enclosedType = (Class) parametrized.getRawType();
            if (listener != null) {
                Type type = parametrized.getActualTypeArguments()[0];
                Class elementType = null;
                if (type instanceof Class)
                    elementType = (Class) type;
                else {
                    ParameterizedType ptype = (ParameterizedType) type;
                    if (ptype.getRawType() instanceof Class) {
                        elementType = (Class) ptype.getRawType();
                    }
                }

                if (Collection.class.isAssignableFrom(enclosedType)) {
                    Iterator iter1 = ((Collection) from).iterator();
                    Iterator iter2 = ((Collection) converted).iterator();
                    while (iter1.hasNext() && iter2.hasNext()) {
                        listener.instanceCreated(iter1.hasNext(), elementType, iter2.next());
                    }
                } else if (Map.class.isAssignableFrom(enclosedType)) {
                    Map<?,?> fromMap = (Map) from;
                    Map<?,?> convertedMap = (Map) converted;
                    for (Map.Entry entry : convertedMap.entrySet()) {
                        Object old = fromMap.get(entry.getKey());
                        listener.instanceCreated(old, elementType, entry.getValue());
                    }
                }
            }


        }
        return converted;
    }


    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S3776"})
    private Object convert(ObjectMapper objectMapper, Object val, Class toType) throws IOException {
        Object value = val;
        Class fromType = value.getClass();

        if (toType.isArray()) {
            if (fromType.isArray() && fromType.getComponentType().equals(toType.getComponentType())) {
                return value;
            }

            if (value instanceof Collection) {
                Collection coll = (Collection) value;
                Object array = Array.newInstance(toType.getComponentType(), coll.size());
                Iterator iterator = coll.iterator();
                int index = 0;
                while (iterator.hasNext()) {
                    Object member = iterator.next();
                    Array.set(array, index, convert(member, toType.getComponentType()));
                    index++;
                }
                return array;
            } else {
                return objectMapper.convertValue(val, toType);
            }
        } else if (toType.isAssignableFrom(fromType)) {
            // no conversion
            return value;
        } else if (toType.isPrimitive() || fromType.isPrimitive()) {
            // converting primitive type
            boolean needConversion;
            if (toType.equals(fromType))
                needConversion = false;
            else if (toType.isPrimitive())
                needConversion = shouldConvertPrimitive(toType, fromType);
            else needConversion = shouldConvertPrimitive(fromType, toType);
            if (!needConversion)
                return value;
        } else if (toType.equals(String.class)) {
            return value.toString();
        } else if (value instanceof String && !(toType.isAssignableFrom(Class.class))) {
            // converting from String to other types.
            try {
                // converting from String directly, e.g. mapper can convert from String to BigDecimal
                if (resolver != null && value.toString().trim().startsWith("^")) {
                    return resolver.resolve(value.toString());
                }
                String str = objectMapper.writeValueAsString(value);
                value = objectMapper.readValue(str, toType);
                return value;
            } catch (Exception e) {
                Logger.suppress(e);
                // OK mapper cannot convert String directly so we assume the String is a full
                // class name.  We load the class and create an instance.
                try {
                    Class cls = getClass().getClassLoader().loadClass((String) value);
                    value = cls.getDeclaredConstructor().newInstance();
                    if (listener != null && value != null)
                        listener.instanceCreated(value, toType, value);
                    return value;
                } catch (Exception e1) {
                    throw new SystemException(e1);
                }
            }
        }

        // we use the mapper to convert
        String str = objectMapper.writeValueAsString(value);
        return objectMapper.readValue(str, toType);
    }

    private Collection convertCollection(ObjectMapper objectMapper, Collection value, Class<? extends Collection> collectionType, Class elementType) throws IOException {

        CollectionType ctype = TypeFactory.defaultInstance().constructCollectionType(collectionType, elementType);
        String str = objectMapper.writeValueAsString(value);
        Collection converted = objectMapper.readValue(str, ctype);

        if (listener != null) {
            Iterator iter1 = value.iterator();
            Iterator iter2 = converted.iterator();
            while (iter1.hasNext() && iter2.hasNext()) {
                listener.instanceCreated(iter1.hasNext(), elementType, iter2.next());
            }
        }
        return converted;
    }

    private Map convertMap(ObjectMapper objectMapper, Map value, Type type) throws IOException {

        JavaType ctype = TypeFactory.defaultInstance().constructType(type);
        String str = objectMapper.writeValueAsString(value);
        Map converted = objectMapper.readValue(str, ctype);

        /*if (listener != null) {
            Iterator iter1 = value.iterator();
            Iterator iter2 = converted.iterator();
            while (iter1.hasNext() && iter2.hasNext()) {
                listener.instanceCreated(iter1.hasNext(), elementType, iter2.next());
            }
        }*/
        return converted;
    }

    @SuppressWarnings({"squid:S1067", "squid:MethodCyclomaticComplexity"})
    protected boolean shouldConvertPrimitive(Class c1, Class c2) {
        return ! ((c1.equals(Boolean.TYPE) && Boolean.class.equals(c2))
                || (c1.equals(Character.TYPE) && Character.class.equals(c2))
                || (c1.equals(Byte.TYPE) && Byte.class.equals(c2))
                || (c1.equals(Short.TYPE) && Short.class.equals(c2))
                || (c1.equals(Integer.TYPE) && Integer.class.equals(c2))
                || (c1.equals(Long.TYPE) && Long.class.equals(c2))
                || (c1.equals(Float.TYPE) && Float.class.equals(c2))
                || (c1.equals(Double.TYPE) && Double.class.equals(c2)));
    }

    @FunctionalInterface
    public interface InstanceCreationListener {
        void instanceCreated(Object value, Class toType, Object instance);
    }

    public ObjectMapper createObjectMapper() {
        if (resolver == null)
            return mapper;
        if (objectMapper != null)
            return objectMapper;

        objectMapper = new ObjectMapper(null, null, new MyDefaultDeserializationContext(resolver, new MyBeanDeserializerFactory(resolver,
                new DeserializerFactoryConfig())));
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.addHandler(new MyDeserializationProblemHandler(resolver));
        return objectMapper;
    }

    public static class MyBeanDeserializerFactory extends BeanDeserializerFactory {
        Resolver resolver;
        public MyBeanDeserializerFactory(Resolver resolver, DeserializerFactoryConfig config) {
            super(config);
            this.resolver = resolver;
        }

        @Override
        public DeserializerFactory withConfig(DeserializerFactoryConfig config)
        {
            if (_factoryConfig == config) {
                return this;
            }
            return new MyBeanDeserializerFactory(resolver, config);
        }

        public JsonDeserializer<?> findDefaultDeserializer(DeserializationContext ctxt,
                                                           JavaType type, BeanDescription beanDesc) throws JsonMappingException {
            JsonDeserializer deserializer = super.findDefaultDeserializer(ctxt, type, beanDesc);
            if (deserializer instanceof UntypedObjectDeserializer) {
                DeserializationConfig config = ctxt.getConfig();
                JavaType lt, mt;

                if (_factoryConfig.hasAbstractTypeResolvers()) {
                    lt = _findRemappedType(config, List.class);
                    mt = _findRemappedType(config, Map.class);
                } else {
                    lt = mt = null;
                }
                return new MyUntypedObjectDeserializer(resolver, lt, mt);
            }
            return deserializer;
        }
    }

    /** The Object type parameter is just to bypass TypeReference's check. The real type is the type param */
    public static class MyTypeReference extends TypeReference<Object> {
        private Type type;
        public MyTypeReference(Type type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }

    public static class MyUntypedObjectDeserializer extends UntypedObjectDeserializer {
        Resolver resolver;

        public MyUntypedObjectDeserializer(Resolver resolver, JavaType listType, JavaType mapType) {
            super(listType, mapType);
            this.resolver = resolver;
        }

        protected MyUntypedObjectDeserializer(UntypedObjectDeserializer base,
                                            boolean nonMerging) {
            super(base, nonMerging);
        }

        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Object value = super.deserialize(p, ctxt);
            return ObjectConverter.resolve(resolver, value);
        }
    }

    static Object handleMissingInstantiator(Resolver resolver,  Class<?> instClass, JsonParser p) throws IOException {
        if (resolver != null) {
            String value = p.getText();
            Object resolved =  ObjectConverter.resolve(resolver, value);
            if (resolved == null) {
                return resolved;
            } else if (instClass.isAssignableFrom(resolved.getClass())) {
                return resolved;
            } else if (ClassLoader.class.isAssignableFrom(instClass)) {
                return resolved.getClass().getClassLoader();
            }
        }
        return  null;
    }

    public static class MyDeserializationProblemHandler extends DeserializationProblemHandler {

        Resolver resolver;

        public MyDeserializationProblemHandler(Resolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public Object handleMissingInstantiator(DeserializationContext ctxt,
                                                Class<?> instClass, ValueInstantiator valueInsta, JsonParser p,
                                                String msg)
                throws IOException {
            if (resolver != null) {
                return ObjectConverter.handleMissingInstantiator(resolver, instClass, p);
            }
            return super.handleMissingInstantiator(ctxt, instClass, valueInsta, p, msg);
        }
    }

    public static class MyDefaultDeserializationContext extends DefaultDeserializationContext {

        private static final long serialVersionUID = 1L;

        Resolver resolver;

        /**
         * Default constructor for a blueprint object, which will use the standard
         * {@link DeserializerCache}, given factory.
         */
        public MyDefaultDeserializationContext(Resolver resolver, DeserializerFactory df) {
            super(df, null);
            this.resolver = resolver;
        }

        private MyDefaultDeserializationContext(MyDefaultDeserializationContext src,
                     DeserializationConfig config, JsonParser p, InjectableValues values) {
            super(src, config, p, values);
            resolver = src.resolver;
        }

        private MyDefaultDeserializationContext(MyDefaultDeserializationContext src) {
            super(src);
            resolver = src.resolver;
        }

        private MyDefaultDeserializationContext(MyDefaultDeserializationContext src, DeserializerFactory factory) {
            super(src, factory);
            resolver = src.resolver;
        }

        private MyDefaultDeserializationContext(MyDefaultDeserializationContext src, DeserializationConfig config) {
            super(src, config);
            resolver = src.resolver;
        }

        @Override
        public DefaultDeserializationContext copy() {
            return new MyDefaultDeserializationContext(this);
        }

        @Override
        public DefaultDeserializationContext createInstance(DeserializationConfig config,
                                                            JsonParser p, InjectableValues values) {
            return new MyDefaultDeserializationContext(this, config, p, values);
        }

        @Override
        public DefaultDeserializationContext createDummyInstance(DeserializationConfig config) {
            // need to be careful to create "real", not blue-print, instance
            return new MyDefaultDeserializationContext(this, config);
        }

        @Override
        public DefaultDeserializationContext with(DeserializerFactory factory) {
            return new MyDefaultDeserializationContext(this, factory);
        }

        public Object readRootValue(JsonParser p, JavaType valueType,
                                    JsonDeserializer<Object> deser, Object valueToUpdate)
                throws IOException {
            if (resolver != null && p.getText().trim().startsWith("^")) {
                return resolver.resolve(p.getText().trim());
            }
            return super.readRootValue(p, valueType, deser, valueToUpdate);
        }

        public Object handleMissingInstantiator(Class<?> instClass, ValueInstantiator valueInst,
                                                JsonParser p, String msg, Object... msgArgs)
                throws IOException {
            if (resolver != null) {
                return ObjectConverter.handleMissingInstantiator(resolver, instClass, p);
            }
            return super.handleMissingInstantiator(instClass, valueInst, p, msg, msgArgs);
        }
    }
}
