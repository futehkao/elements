/*
 * Copyright 2015 Futeh Kao
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.TextSubstitution;
import net.e6tech.elements.common.util.datastructure.Pair;
import net.e6tech.elements.common.util.lambda.Each;

import java.beans.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Locale.ENGLISH;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S134", "squid:S1149", "squid:S1141", "squid:MethodCyclomaticComplexity", "squid:S3776"})
public class Reflection {
    @SuppressWarnings("squid:S1185")
    static final class PrivateSecurityManager extends SecurityManager {
        @Override
        protected Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    private static Set<Class> convertibleTypes = new HashSet();
    static {
        convertibleTypes.add(Boolean.TYPE);
        convertibleTypes.add(Boolean.class);
        convertibleTypes.add(Double.TYPE);
        convertibleTypes.add(Double.class);
        convertibleTypes.add(Float.TYPE);
        convertibleTypes.add(Float.class);
        convertibleTypes.add(Integer.TYPE);
        convertibleTypes.add(Integer.class);
        convertibleTypes.add(Long.TYPE);
        convertibleTypes.add(Long.class);
        convertibleTypes.add(Short.TYPE);
        convertibleTypes.add(Short.class);
        convertibleTypes.add(BigDecimal.class);
        convertibleTypes.add(BigInteger.class);
    }

    private static final PrivateSecurityManager securityManager = new PrivateSecurityManager();

    private static LoadingCache<Method, PropertyDescriptor> methodPropertyDescriptors = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .initialCapacity(500)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Method, PropertyDescriptor>() {
                public PropertyDescriptor load(Method method) throws IntrospectionException {
                    String name = method.getName();
                    Parameter[] parameters = method.getParameters();
                    String property ;
                    if (name.startsWith("set")) {
                        if (parameters.length != 1)
                            throw new IllegalArgumentException("" + method.getName() + " is not a setter");
                        property = name.substring(3);
                    } else if (name.startsWith("get")) {
                        if (parameters.length != 0)
                            throw new IllegalArgumentException("" + method.getName() + " is not a getter");
                        property = name.substring(3);
                    } else if (name.startsWith("is")) {
                        if (parameters.length != 0)
                            throw new IllegalArgumentException("" + method.getName() + " is not a getter");
                        property = name.substring(2);
                    } else {
                        throw new IllegalArgumentException("" + method.getName() + " is not an property accessor");
                    }

                    boolean lowerCase = true;
                    if (property.length() > 1 && Character.isUpperCase(property.charAt(1)))
                        lowerCase = false;
                    if (lowerCase)
                        property = property.substring(0, 1).toLowerCase(ENGLISH) + property.substring(1);
                    return new PropertyDescriptor(property, method.getDeclaringClass());
                }
            });

    private static LoadingCache<Pair<Class, String>, PropertyDescriptor> propertyDescriptors = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .initialCapacity(500)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Pair<Class, String>, PropertyDescriptor>() {
                @Override
                public PropertyDescriptor load(Pair<Class, String> key) throws Exception {
                    Class cls = key.key();
                    String property = key.value();
                    PropertyDescriptor descriptor;
                    try {
                        descriptor = new PropertyDescriptor(property, cls,
                                "is" + TextSubstitution.capitalize(property), null);
                    } catch (IntrospectionException e) {
                        throw new SystemException(cls.getName() + "." + property, e);
                    }
                    return descriptor;
                }
            });

    private static LoadingCache<Class, Type[]> parametrizedTypes = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .initialCapacity(100)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<Class, Type[]>() {
                @Override
                public Type[] load(Class clazz) throws Exception {
                    Class cls = clazz;
                    Type[] types = null;
                    while (!cls.equals(Object.class)) {
                        try {
                            Type genericSuper = cls.getGenericSuperclass();
                            if (genericSuper instanceof ParameterizedType) {
                                ParameterizedType parametrizedType = (ParameterizedType) genericSuper;
                                types = parametrizedType.getActualTypeArguments();
                                break;
                            }
                        } catch (Exception th) {
                            logger.warn(th.getMessage(), th);
                        }
                        cls = cls.getSuperclass();
                    }
                    if (types == null)
                        throw new IllegalArgumentException("No parametrized types found");
                    return types;
                }
            });

    static Logger logger = Logger.getLogger();

    private Reflection() {
    }

    // should use weak hash map
    public static PropertyDescriptor propertyDescriptor(Method method) {
        try {
            return methodPropertyDescriptors.get(method);
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    @SuppressWarnings("squid:S1872")
    public static Class getCallingClass() {
        Class<?>[] trace = securityManager.getClassContext(); // trace[0]
                                                              // trace[1] is Reflection because of this call.
                                                              // trace[2] is the caller who wants the calling class
                                                              // trace[3] is the caller.
        String thisClassName = Reflection.class.getName();

        int i;
        for (i = 0; i < trace.length; i++) {
            if (thisClassName.equals(trace[i].getName()))
                break;
        }

        // trace[i] = Reflection; trace[i+1] = caller; trace[i+2] = caller's caller
        if (i >= trace.length || i + 2 >= trace.length) {
            throw new IllegalStateException("Failed to find caller in the stack");
        }

        return trace[i + 2];
    }

    public static <V, C> Optional<V> mapCallingStackTrace(Function<Each<StackTraceElement,C>, ? extends V> mapper) {
        Throwable th = new Throwable();
        StackTraceElement[] trace = th.getStackTrace();
        String thisClassName = Reflection.class.getName();

        int i;
        for (i = 0; i < trace.length; i++) {
            if (thisClassName.equals(trace[i].getClassName()))
                break;
        }

        Each.Mutator<StackTraceElement, C> mutator = Each.create();
        for (int j = i + 2; j < trace.length; j++) {
            mutator.setValue(trace[j]);
            V v = mapper.apply(mutator.each());
            if (v != null)
                return Optional.of(v);
        }

        return Optional.empty();
    }

    public static void printStackTrace(StringBuilder builder, String indent, int start, int end) {
        StackTraceElement[] elements = new Throwable().getStackTrace();
        int i = start + 1; // skip 1 for this printStackTrace call
        while (i < end && i < elements.length) {
            builder.append("\n");
            if (indent != null)
                builder.append(indent);
            builder.append(elements[i].getClassName());
            builder.append(".");
            builder.append(elements[i].getMethodName());
            builder.append("(");
            builder.append(elements[i].getFileName());
            builder.append(":");
            builder.append(elements[i].getLineNumber());
            builder.append(")");
            i ++;
        }
    }

    public static <T> T newInstance(String className, ClassLoader loader) {
        try {
            return (T) loadClass(className, loader).newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public static Class loadClass(String className, ClassLoader classLoader) {
        ClassLoader loader = classLoader;
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader == null) {
            loader = getCallingClass().getClassLoader();
        }
        try {
            return loader.loadClass(className);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public static BeanInfo getBeanInfo(Class cls) {
        try {
            return Introspector.getBeanInfo(cls);
        } catch (IntrospectionException e) {
            throw new SystemException(e);
        }
    }

    public static void forEachAnnotatedAccessor(Class objectClass,
                                                Class<? extends Annotation> annotationClass,
                                                Consumer<AccessibleObject> consumer) {
        Class cls = objectClass;
        BeanInfo beanInfo = getBeanInfo(cls);
        PropertyDescriptor[] props = beanInfo.getPropertyDescriptors();
        for (PropertyDescriptor prop : props) {
            if (prop.getReadMethod() == null) {
                continue;
            }
            if (prop.getReadMethod().getAnnotation(annotationClass) != null) {
                consumer.accept(prop.getReadMethod());
            }
        }

        while (!cls.equals(Object.class)) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (field.getAnnotation(annotationClass) != null) {
                    consumer.accept(field);
                }
            }
            cls = cls.getSuperclass();
        }
    }

    public static <V> V getProperty(Object object, String property) {
        try {
            Class cls = null;
            if (object instanceof  Class) {
                cls = (Class) object;
            } else {
                cls = object.getClass();
            }

            PropertyDescriptor descriptor =  getPropertyDescriptor(cls, property);
            if (descriptor == null || descriptor.getReadMethod() == null)
                return null;
            return (V) descriptor.getReadMethod().invoke(object);
        } catch (Exception e) {
            throw new SystemException(object.getClass().getName() + "." + property, e);
        }
    }

    public static PropertyDescriptor getPropertyDescriptor(Class cls, String property) {
        try {
            return propertyDescriptors.get(new Pair<>(cls, property));
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }
    }

    public static <V> V getField(Object object, String fieldName) {
        Field field = getField(object.getClass(), fieldName);
        try {
            return (V) field.get(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setField(Object object, String fieldName, Object value) {
        Field field = getField(object.getClass(), fieldName);
        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Field getField(Class clazz, String fieldName) {
        Class cls = clazz;
        while (cls != null && !cls.equals(Object.class)) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (Exception e) {
                Logger.suppress(e);
            }
            try {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No bankId defined");
    }

    public static Class getParametrizedType(Class clazz, int index) {
        Type[] types;
        try {
            types = parametrizedTypes.get(clazz);
        } catch (ExecutionException e) {
            throw new SystemException(e.getCause());
        }

        if (types.length <= index) {
            throw new IllegalArgumentException("No parametrized type at index=" + index);
        } else if (types[index] instanceof Class) {
            return (Class) types[index];
        } else if (types[index] instanceof ParameterizedType &&  ((ParameterizedType) types[index]).getRawType() instanceof Class) {
            return (Class)((ParameterizedType) types[index]).getRawType();
        } else {
            return null;
        }
    }

    public static <T> List<T> newInstance(Class<T> cls, List objectList) {
        return newInstance(cls, objectList, null);
    }

    @SuppressWarnings("squid:S1168")
    public static <T> List<T> newInstance(Class<T> cls, List objectList, CopyListener listener) {
        if (objectList == null)
            return null;

        List<T> list = new ArrayList<>();
        for (Object o : objectList) {
            T target = (new Replicator()).newInstance(cls, o, listener);
            list.add(target);
        }
        return list;
    }

    public static <T> T newInstance(Class<T> cls, Object object) {
        return (new Replicator()).newInstance(cls, object, new HashMap<>(), null);
    }

    public static <T> T newInstance(Class<T> cls, Object object, CopyListener listener) {
        return (new Replicator()).newInstance(cls, object, new HashMap<>(), listener);
    }

    public static void copyInstance(Object target, Object object) {
        (new Replicator()).copy(target, object, new HashMap<>(), null);
    }

    public static void copyInstance(Object target, Object object, CopyListener listener) {
        (new Replicator()).copy(target, object, new HashMap<>(), listener);
    }

    public static boolean compare(Object target, Object object) {
        return (new Replicator()).compare(target, object);
    }

    public static class Replicator {
        private Map<Class, Map<String, PropertyDescriptor>> targetPropertiesDescriptor = new HashMap<>();
        private Map<Class, PropertyDescriptor[]> propertyDescriptors = new HashMap<>();

        private synchronized Map<String, PropertyDescriptor> getTargetProperties(Class cls) {
            return targetPropertiesDescriptor.computeIfAbsent(cls, key -> {
                HashMap<String, PropertyDescriptor> descriptors = new HashMap<>();
                PropertyDescriptor[] props = getBeanInfo(key).getPropertyDescriptors();
                for (PropertyDescriptor prop : props) {
                    descriptors.put(prop.getName(), prop);
                }
                return descriptors;
            });
        }

        private synchronized PropertyDescriptor[] getPropertyDescriptors(Class cls) {
            return propertyDescriptors.computeIfAbsent(cls, key -> getBeanInfo(key).getPropertyDescriptors());
        }

        public synchronized Map<Class, Map<String, PropertyDescriptor>> getTargetPropertiesDescriptor() {
            return targetPropertiesDescriptor;
        }

        public synchronized void setTargetPropertiesDescriptor(Map<Class, Map<String, PropertyDescriptor>> targetPropertiesDescriptor) {
            this.targetPropertiesDescriptor = targetPropertiesDescriptor;
        }

        public synchronized Map<Class, PropertyDescriptor[]> getPropertyDescriptors() {
            return propertyDescriptors;
        }

        public synchronized void setPropertyDescriptors(Map<Class, PropertyDescriptor[]> propertyDescriptors) {
            this.propertyDescriptors = propertyDescriptors;
        }

        public <T> T newInstance(Class<T> cls, Object object) {
            return (new Replicator()).newInstance(cls, object, new HashMap<>(), null);
        }

        public <T> T newInstance(Class<T> cls, Object object, CopyListener listener) {
            return (new Replicator()).newInstance(cls, object, new HashMap<>(), listener);
        }

        private <T> T newInstance(Type toType, Object object, Map<Integer, Object> seen, CopyListener listener) {
            if (object == null)
                return null;

            T buildin = null;
            if (toType instanceof Class) {
                buildin = (T) convertBuiltinType((Class) toType, object);
                if (buildin != null)
                    return buildin;
            }

            Integer hashCode = null;
            if (!object.getClass().isPrimitive()) {
                hashCode = System.identityHashCode(object);
                if (seen.get(hashCode) != null)
                    return (T) seen.get(hashCode);
            }

            T target = null;
            if (toType instanceof Class) {
                Class cls = (Class) toType;
                if (Enum.class.isAssignableFrom((Class)toType)) {
                    target = (T) Enum.valueOf(cls, object.toString());
                } else if (Enum.class.isAssignableFrom(object.getClass()) && String.class.isAssignableFrom(cls)) {
                    target = (T) ((Enum) object).name();
                } else if (Collection.class.isAssignableFrom(cls)) {
                    Collection collection = newCollection(cls);
                    target = (T) collection;
                    if (object instanceof Collection) {
                        Collection c = (Collection) object;
                        for (Object o : c) {
                            collection.add(o);
                        }
                    } else {
                        throw new IllegalStateException("Do not know how to convert " + object.getClass() + " to " + toType);
                    }
                } else {
                    try {
                        target = (T) cls.newInstance();
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                    copy(target, object, seen, listener);
                }
            } else {
                ParameterizedType parametrized = (ParameterizedType) toType;
                Class enclosedType = (Class) parametrized.getRawType();
                Type type = parametrized.getActualTypeArguments()[0];
                if (Collection.class.isAssignableFrom(enclosedType)) {
                    Collection collection = newCollection(enclosedType);
                    target = (T) collection;
                    if (object instanceof Collection) {
                        Collection c = (Collection) object;
                        for (Object o : c) {
                            Object converted = newInstance(type ,o, seen, listener);
                            collection.add(converted);
                        }
                    } else {
                        throw new IllegalStateException("Do not know how to convert " + object.getClass() + " to " + toType);
                    }
                } else {
                    try {
                        target = (T) enclosedType.newInstance();
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                    copy(target, object, seen, listener);
                }
            }

            if (hashCode != null)
                seen.put(hashCode, target);
            return target;
        }

        protected Collection newCollection(Class cls) {
            Collection collection = null;
            if (List.class.isAssignableFrom(cls)) {
                collection = new ArrayList<>();
            } else if (Set.class.isAssignableFrom(cls)) {
                collection = new LinkedHashSet<>();
            } else {
                collection = new ArrayList<>();
            }
            return collection;
        }

        protected Object convertBuiltinType(Class type, Object object) {
            if (String.class.isAssignableFrom(type)) {
                return object.toString();
            }

            if (!convertibleTypes.contains(type))
                return null;

            if (type == Boolean.TYPE || type == Boolean.class)
                return new Boolean(object.toString());
            else if (type == Double.TYPE || type == Double.class) {
                return new Double(object.toString());
            } else if (type == Float.TYPE || type == Float.class) {
                return new Float(object.toString());
            } else if (type == Integer.TYPE || type == Integer.class) {
                return new Integer(object.toString());
            } else if (type == Long.TYPE || type == Long.class) {
                return new Long(object.toString());
            } else if (type == Short.TYPE || type == Short.class) {
                return new Short(object.toString());
            } else if (type == BigDecimal.class) {
                return new BigDecimal(object.toString());
            } else if (type == BigInteger.class) {
                return new BigInteger(object.toString());
            }
            return null;
        }

        public void copy(Object target, Object object, CopyListener copyListener) {
            copy(target, object, new HashMap<>(), copyListener);
        }

        public void copy(Object target, Object object) {
            copy(target, object, new HashMap<>(), null);
        }

        @SuppressWarnings("squid:S135")
        private void copy(Object target, Object object, Map<Integer, Object> seen, CopyListener copyListener) {
            if (target == null || object == null)
                return;

            for (PropertyDescriptor prop : getPropertyDescriptors(object.getClass())) {
                if (prop.getReadMethod() != null) {
                    PropertyDescriptor targetDesc = getTargetProperties(target.getClass()).get(prop.getName());
                    if (targetDesc == null)
                        continue;

                    Method setter = targetDesc.getWriteMethod();
                    if (setter == null)
                        continue;
                    try {
                        boolean annotated = (prop.getReadMethod().getAnnotation(DoNotCopy.class) != null);
                        if (!annotated && prop.getWriteMethod() != null) {
                            annotated = (prop.getWriteMethod().getAnnotation(DoNotCopy.class) != null);
                        }
                        if (!annotated) {
                            try {
                                boolean handled = false;
                                if (copyListener != null) {
                                    handled = copyListener.copy(target, targetDesc, object, prop);
                                }
                                if (!handled) {
                                    Object value = prop.getReadMethod().invoke(object);

                                    if (!(value instanceof Collection) &&
                                            setter.getParameterTypes()[0].isAssignableFrom(prop.getReadMethod().getReturnType())) {
                                        setter.invoke(target, value);
                                    } else {
                                        try {
                                            Object converted = newInstance(setter.getGenericParameterTypes()[0], value, seen, copyListener);
                                            setter.invoke(target, converted);
                                        } catch (Exception ex) {
                                            logger.warn("Error copying " + value + " to " + setter.getDeclaringClass() + "::" + setter.getName(), ex);
                                        }
                                    }
                                }
                            } catch (PropertyVetoException ex) {
                                Logger.suppress(ex);
                            }
                        }
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }
            }
        }

        public boolean compare(Object target, Object object) {
            Stack<String> stack = new Stack<>();
            if (target != null)
                stack.push(target.getClass().getName());
            return compare(target, object, new HashSet<>(), stack);
        }

        private boolean compare(Object target, Object object, Set<String> seen, Stack<String> stack) {
            if (target == null && object == null)
                return true;
            if (target == null || object == null)
                return false;
            // at this point target and object are not null
            int hashCode = System.identityHashCode(target);
            int hashCode2 = System.identityHashCode(object);
            String compared = Integer.toString(hashCode) + ":" + hashCode2;
            if (seen.contains(compared))
                return true;
            if (target.getClass().isAssignableFrom(object.getClass())) {
                if (!target.equals(object)) {
                    if (logger.isDebugEnabled()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("Comparison failed at ");
                        boolean first = true;
                        for (String element : stack) {
                            if (first)
                                first = false;
                            else
                                builder.append(".");
                            builder.append(element);
                        }
                        logger.debug(builder.toString());
                    }
                    return false;
                }
                return true;
            }

            if (!object.getClass().isPrimitive()) {
                seen.add(compared);
            }

            for (PropertyDescriptor prop : getPropertyDescriptors(target.getClass())) {
                if (prop.getReadMethod() != null && !"class".equals(prop.getName())) {
                    try {
                        boolean annotated = (prop.getReadMethod().getAnnotation(DoNotCopy.class) != null);
                        if (!annotated && prop.getWriteMethod() != null) {
                            annotated = (prop.getWriteMethod().getAnnotation(DoNotCopy.class) != null);
                        }
                        if (!annotated) {
                            Method objectGetter = null;
                            try {
                                PropertyDescriptor objectProp = new PropertyDescriptor(prop.getName(), object.getClass(),
                                        "is" + capitalize(prop.getName()), null);
                                objectGetter = objectProp.getReadMethod();
                            } catch (IntrospectionException e) {
                                Logger.suppress(e);
                            }
                            if (objectGetter == null)
                                continue;
                            Object value = objectGetter.invoke(object);
                            Object targetFieldValue = prop.getReadMethod().invoke(target);
                            stack.push(prop.getName());
                            if (!compare(targetFieldValue, value, seen, stack)) {
                                return false;
                            }
                            stack.pop();
                        }
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }
            }
            return true;
        }

        public static String capitalize(String name) {
            return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
        }
    }
}
