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

package net.e6tech.elements.common.reflection;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * Created by futeh.
 * This class is used to create an instance of Annotation and setting its values.
 * See AnnotationTest for an example
 */
public class Annotator implements InvocationHandler {
    static Map<String, Integer> objectMethods = new HashMap<>();

    private Map<Method, Object> values;
    private Method lastAccessed;
    private Class<? extends Annotation> type;
    private Integer hashCode;
    private String toString;

    static {
        objectMethods.put("toString", 0);
        objectMethods.put("hashCode", 0);
        objectMethods.put("annotationType", 0);
        objectMethods.put("equals", 1);
    }

    protected Annotator(Class type, Map<Method, Object> values) {
        this.type = type;
        this.values = values;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T create(Class<? extends Annotation> cls, BiConsumer<AnnotationValue, T> consumer) {
        return create(cls, null, consumer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T create(Class<? extends Annotation> cls, T original, BiConsumer<AnnotationValue, T> consumer) {
        Map<Method, Object> values = new LinkedHashMap<>();
        for (Method method : cls.getDeclaredMethods()) {
            if (objectMethods.containsKey(method.getName())
                && method.getParameterCount() == objectMethods.get(method.getName())) {
                // to be generated
            } else {
                Object defaultValue = method.getDefaultValue();
                values.put(method, defaultValue);
            }
        }

        Annotator annotator = new Annotator(cls, values);
        annotator.copyFrom(original);
        AnnotationValue annotationValue = new AnnotationValue(annotator);
        if (consumer != null)
            consumer.accept(annotationValue, (T) Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, annotator));
        return (T) Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, annotator);
    }

    @Override
    @SuppressWarnings({"squid:S3776", "squid:MethodCyclomaticComplexity"})
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if ("hashCode".equals(methodName) && method.getParameterCount() == 0) {
            if (hashCode == null)
                hashCode = hashCodeImpl();
            return hashCode;
        } else if ("equals".equals(methodName) && method.getParameterCount() == 1
                && method.getParameterTypes()[0].equals(Object.class)) {
            return equalsImpl(args[0]);
        } else if ("annotationType".equals(methodName) && method.getParameterCount() == 0) {
            return type;
        } else if ("toString".equals(methodName) && method.getParameterCount() == 0) {
            if (toString == null)
                toString = toStringImpl();
            return toString;
        } else {
            lastAccessed = method;
            Object value = values.get(method);
            if (value == null && method.getReturnType().isPrimitive()) {
                return Primitives.defaultValue(method.getReturnType());
            }
            return value;
        }
    }

    private <T extends Annotation> void copyFrom(T original) {
        if (original == null)
            return;
        Iterator<Map.Entry<Method, Object>> iterator = values.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<Method, Object> entry = iterator.next();
            try {
                entry.setValue(entry.getKey().invoke(original));
            } catch (Exception e) {
                Logger.suppress(e);
            }
        }
        hashCode = null;
        toString = null;
    }

    private int hashCodeImpl() {
        int hash = 0;
        Map.Entry<Method, Object> entry;
        for(Iterator iterator = values.entrySet().iterator(); iterator.hasNext();
            hash += 127 * (entry.getKey().getName()).hashCode() ^ Primitives.hashCode(entry.getValue())) {
            entry = (Map.Entry) iterator.next();
        }
        return hash;
    }

    @SuppressWarnings("squid:S134")
    private boolean equalsImpl(Object object) {
        if (object == null)
            return false;
        if(object == this) {
            return true;
        } else if(!this.type.isInstance(object)) {
            return false;
        } else {
            Iterator<Map.Entry<Method, Object>> iterator = values.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<Method, Object> entry = iterator.next();
                try {
                    Object value2 = entry.getKey().invoke(object);
                    if (! Primitives.equals(entry.getValue(), value2))
                        return false;
                } catch (Exception e) {
                    Logger.suppress(e);
                    return false;
                }
            }
            return true;
        }
    }

    private String toStringImpl() {
        StringBuilder builder = new StringBuilder(128);
        builder.append('@');
        builder.append(type.getName());
        builder.append('(');
        boolean first = true;
        Iterator<Map.Entry<Method, Object>> iterator = values.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<Method, Object> entry = iterator.next();
            if(first) {
                first = false;
            } else {
                builder.append(", ");
            }

            builder.append(entry.getKey().getName());
            builder.append('=');
            builder.append(Primitives.toString(entry.getValue()));
        }

        builder.append(')');
        return builder.toString();
    }

    public static class AnnotationValue {
        private Annotator handler;

        public AnnotationValue(Annotator handler) {
            this.handler = handler;
        }

        public AnnotationValue set(Callable<String> callable, String value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Boolean> callable, Boolean value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<boolean[]> callable, boolean[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Byte> callable, Byte value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<byte[]> callable, byte[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Character> callable, Character value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<char[]> callable, char[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Double> callable, Double value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<double[]> callable, double[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Float> callable, Float value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<float[]> callable, float[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Integer> callable, Integer value) {
            return _set(callable, value);
        }

        public AnnotationValue  set(Callable<int[]> callable, int[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Long> callable, Long value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<long[]> callable, long[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Short> callable, Short value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<short[]> callable, short[] value) {
            return _set(callable, value);
        }

        public AnnotationValue set(Callable<Enum> callable, Enum value) {
            return _set(callable, value);
        }

        @SuppressWarnings("squid:S00100")
        private <K> AnnotationValue _set(Callable<K> callable, K value) {
            try {
                callable.call();
            } catch (Exception e) {
                throw new SystemException(e);
            }
            handler.values.put(handler.lastAccessed, value);
            handler.hashCode = null;
            handler.toString = null;
            handler.lastAccessed = null;
            return this;
        }
    }
}
