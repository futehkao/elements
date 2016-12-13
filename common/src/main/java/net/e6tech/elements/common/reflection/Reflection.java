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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.TextSubstitution;
import net.e6tech.elements.common.cache.CacheFacade;

import java.beans.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

import static java.util.Locale.ENGLISH;

/**
 * Created by futeh.
 */
public class Reflection {
    static final class PrivateSecurityManager extends SecurityManager {
        protected Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    private static final PrivateSecurityManager securityManager = new PrivateSecurityManager();

    private static CacheFacade<Class, Type[]> parametrizedTypes = new CacheFacade<Class, Type[]>("parameterizedTypes") {};

    static Logger logger = Logger.getLogger();

    public static PropertyDescriptor propertyDescriptor(Method method) {
        String name = method.getName();

        Parameter[] parameters = method.getParameters();

        String property ;
        if (name.startsWith("set")) {
            if (parameters.length != 1) throw new IllegalArgumentException("" + method.getName() + " is not a setter");
            property = name.substring(3);
        } else if (name.startsWith("get")) {
            if (parameters.length != 0) throw new IllegalArgumentException("" + method.getName() + " is not a getter");
            property = name.substring(3);
        } else if (name.startsWith("is")) {
            if (parameters.length != 0) throw new IllegalArgumentException("" + method.getName() + " is not a getter");
            property = name.substring(2);
        } else {
            throw new IllegalArgumentException("" + method.getName() + " is not an property accessor");
        }

        boolean lowerCase = true;
        if (property.length() > 1 && Character.isUpperCase(property.charAt(1))) lowerCase = false;
        if (lowerCase) property = property.substring(0, 1).toLowerCase(ENGLISH) + property.substring(1);
        try {
            return new PropertyDescriptor(property, method.getDeclaringClass());
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class getCallingClass() {
        Class<?>[] trace = securityManager.getClassContext();
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

    public static StackTraceElement[] getCallingStackTrace() {
        return getCallingStackTrace(0);
    }

    public static StackTraceElement[] getCallingStackTrace(int skip) {
        Throwable th = new Throwable();
        StackTraceElement[] trace = th.getStackTrace();
        String thisClassName = Reflection.class.getName();

        int i;
        for (i = 0; i < trace.length; i++) {
            if (thisClassName.equals(trace[i].getClassName()));
                break;
        }

        // trace[i] = Reflection; trace[i+1] = caller; trace[i+2] = caller's caller
        if (i >= trace.length || i + 2 + skip >= trace.length) {
            throw new IllegalStateException("Failed to find caller in the stack");
        }

        return Arrays.copyOfRange(trace, 2 + skip, trace.length);
    }

    public static void printStackTrace(StringBuilder builder, String indent, int start, int end) {
        StackTraceElement[] elements = getCallingStackTrace(1);
        int i = start;
        while (i < end && i < elements.length) {
            builder.append("\n");
            if (indent != null) builder.append(indent);
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
            throw new RuntimeException(e);
        }
    }

    public static Class loadClass(String className, ClassLoader loader) {
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader == null) {
            loader = getCallingClass().getClassLoader();
        }
        try {
            return loader.loadClass(className);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void forEachAnnotatedAccessor(Class objectClass,
                                                Class<? extends Annotation> annotationClass,
                                                Consumer<AccessibleObject> consumer) {
        Class cls = objectClass;
        BeanInfo beanInfo = null;
        try {
            beanInfo = Introspector.getBeanInfo(cls);
        } catch (IntrospectionException e) {
            e.printStackTrace();
        }
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
            PropertyDescriptor desc = new PropertyDescriptor(property, object.getClass(),
                   "is" + TextSubstitution.capitalize(property), null);
            if (desc == null || desc.getReadMethod() == null) return null;
            return (V) desc.getReadMethod().invoke(object);
        } catch (Throwable e) {
            throw new RuntimeException(object.getClass().getName() + "." + property, e);
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

    public static Field getField(Class cls, String fieldName) {
        while (cls != null || !cls.equals(Object.class)) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (Throwable e) {
            }
            try {
                cls = cls.getSuperclass();
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No bankId defined");
    }

    public static Class getParametrizedType(Class clazz, int index) {

        Type[] pTypes = parametrizedTypes.get(clazz, ()-> {
            Class cls = clazz;
            while (!cls.equals(Object.class)) {
                try {
                    Type genericSuper = cls.getGenericSuperclass();
                    if (genericSuper instanceof ParameterizedType) {
                        ParameterizedType parametrizedType = (ParameterizedType) genericSuper;
                        return parametrizedType.getActualTypeArguments();
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                cls = cls.getSuperclass();
            }
            return null;
        });

        if (pTypes == null)
            throw new IllegalArgumentException("No parametrized types found");
        if (pTypes.length <= index)
            throw new IllegalArgumentException("No parametrized type at index=" + index);
        if (pTypes[index] instanceof Class) {
            return (Class) pTypes[index];
        }
        return null;

        /*
        Class entityType = null;

        while (!clazz.equals(Object.class)) {
            try {
                Type genericSuper = clazz.getGenericSuperclass();
                if (genericSuper instanceof ParameterizedType) {
                    ParameterizedType parametrizedType = (ParameterizedType) genericSuper;
                    if (parametrizedType.getActualTypeArguments().length <= index)
                        throw new IllegalArgumentException("No parametrized type at index=" + index);
                    Type type = parametrizedType.getActualTypeArguments()[index];
                    if (type instanceof Class) {
                        entityType = (Class) type;
                        break;
                    }
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
            clazz = clazz.getSuperclass();
        }
        if (entityType == null) throw new IllegalStateException("Class " + clazz + " does not provided a generic type");
        return entityType; */
    }

    public static <T> List<T> newInstance(Class<T> cls, List objectList) {
        return newInstance(cls, objectList, null);
    }

    public static <T> List<T> newInstance(Class<T> cls, List objectList, CopyListener listener) {
        if (objectList == null) return null;

        List<T> list = new ArrayList<>();
        for (Object o : objectList) {
            T target = (new Instance()).newInstance(cls, o, listener);
            list.add(target);
        }
        return list;
    }

    public static <T> T newInstance(Class<T> cls, Object object) {
        return (new Instance()).newInstance(cls, object, new HashMap<>(), null);
    }

    public static <T> T newInstance(Class<T> cls, Object object, CopyListener listener) {
        return (new Instance()).newInstance(cls, object, new HashMap<>(), listener);
    }

    public static void copyInstance(Object target, Object object) {
        (new Instance()).copy(target, object, new HashMap<>(), null);
    }

    public static void copyInstance(Object target, Object object, CopyListener listener) {
        (new Instance()).copy(target, object, new HashMap<>(), listener);
    }

    public static boolean compare(Object target, Object object) {
        return (new Instance()).compare(target, object);
    }

    public static class Instance {
        Map<Class, Map<String, Method>> setters = new HashMap<>();
        Map<Class, PropertyDescriptor[]> propertyDescriptors = new HashMap<>();

        private Map<String, Method> getSetters(Class cls) {
            return setters.computeIfAbsent(cls, (key) -> {
                HashMap<String, Method> methods = new HashMap<>();
                PropertyDescriptor[] props = getBeanInfo(key).getPropertyDescriptors();
                for (PropertyDescriptor prop : props) {
                    if (prop.getWriteMethod() != null) {
                        methods.put(prop.getName(), prop.getWriteMethod());
                    }
                }
                return methods;
            });
        }

        private PropertyDescriptor[] getPropertyDescriptors(Class cls) {
            return propertyDescriptors.computeIfAbsent(cls, key -> getBeanInfo(key).getPropertyDescriptors());
        }

        private BeanInfo getBeanInfo(Class cls) {
            try {
                return Introspector.getBeanInfo(cls);
            } catch (IntrospectionException e) {
                throw new RuntimeException(e);
            }
        }

        public <T> T newInstance(Class<T> cls, Object object) {
            return (new Instance()).newInstance(cls, object, new HashMap<>(), null);
        }

        public <T> T newInstance(Class<T> cls, Object object, CopyListener listener) {
            return (new Instance()).newInstance(cls, object, new HashMap<>(), listener);
        }

        private <T> T newInstance(Type toType, Object object, Map<Integer, Object> seen, CopyListener listener) {
            if (object == null) return null;

            Integer hashCode = null;
            if (!object.getClass().isPrimitive()) {
                hashCode = System.identityHashCode(object);
                if (seen.get(hashCode) != null) return (T) seen.get(hashCode);
            }

            T target = null;
            if (toType instanceof Class) {
                try {
                    target = (T) ((Class) toType).newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                copy(target, object, seen, listener);
            } else {
                ParameterizedType parametrized = (ParameterizedType) toType;
                Class enclosedType = (Class) parametrized.getRawType();
                Type type = parametrized.getActualTypeArguments()[0];
                if (Collection.class.isAssignableFrom(enclosedType)) {
                    Collection collection = null;
                    if (List.class.isAssignableFrom(enclosedType)) {
                        collection = new ArrayList<>();
                    } else if (Set.class.isAssignableFrom(enclosedType)) {
                        collection = new LinkedHashSet<>();
                    } else {
                        collection = new ArrayList<>();
                    }
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
                        throw new RuntimeException(e);
                    }
                    copy(target, object, seen, listener);
                }
            }

            if (hashCode != null) seen.put(hashCode, target);
            return target;
        }

        public void copy(Object target, Object object, CopyListener copyListener) {
            copy(target, object, new HashMap<>(), copyListener);
        }

        public void copy(Object target, Object object) {
            copy(target, object, new HashMap<>(), null);
        }

        private void copy(Object target, Object object, Map<Integer, Object> seen, CopyListener copyListener) {
            if (target == null || object == null) return;

            for (PropertyDescriptor prop : getPropertyDescriptors(object.getClass())) {
                if (prop.getReadMethod() != null) {
                    Method setter = getSetters(target.getClass()).get(prop.getName());
                    if (setter == null) continue;
                    try {
                        boolean annotated = (prop.getReadMethod().getAnnotation(DoNotCopy.class) != null);
                        if (!annotated && prop.getWriteMethod() != null) {
                            annotated = (prop.getWriteMethod().getAnnotation(DoNotCopy.class) != null);
                        }
                        if (!annotated) {
                            Object value = prop.getReadMethod().invoke(object);
                            try {
                                boolean handled = false;
                                if (copyListener != null) handled = copyListener.copy(target, setter, setter.getParameterTypes()[0], value);
                                if (!handled) {
                                    if (setter.getParameterTypes()[0].isAssignableFrom(prop.getReadMethod().getReturnType())) {
                                        setter.invoke(target, value);
                                    } else {
                                        try {
                                            Object converted = newInstance(setter.getGenericParameterTypes()[0], value, seen, copyListener);
                                            setter.invoke(target, converted);
                                        } catch (Throwable ex) {
                                            logger.warn("Error copying " + value + " to " + setter.getDeclaringClass() + "::" + setter.getName(), ex);
                                        }
                                    }
                                }
                            } catch (PropertyVetoException ex) {

                            }
                        }
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        public boolean compare(Object target, Object object) {
            Stack<String> stack = new Stack<>();
            if (target != null) stack.push(target.getClass().getName());
            return compare(target, object, new HashSet<>(), stack);
        }

        private boolean compare(Object target, Object object, Set<String> seen, Stack<String> stack) {
            if (target == null && object == null) return true;
            if (target == null || object == null) return false;
            // at this point target and object are not null
            int hashCode = System.identityHashCode(target);
            int hashCode2 = System.identityHashCode(object);
            String compared = "" + hashCode + ":" + hashCode2;
            if (seen.contains(compared)) return true;
            if (target.getClass().isAssignableFrom(object.getClass())) {
                if (!target.equals(object)) {
                    if (logger.isDebugEnabled()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("Comparison failed at ");
                        boolean first = true;
                        for (String element : stack) {
                            if (first) first = false;
                            else builder.append(".");
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
                if (prop.getReadMethod() != null && !prop.getName().equals("class")) {
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
                            }
                            if (objectGetter == null) continue;
                            Object value = objectGetter.invoke(object);
                            Object targetFieldValue = prop.getReadMethod().invoke(target);
                            stack.push(prop.getName());
                            if (!compare(targetFieldValue, value, seen, stack)) {
                                return false;
                            }
                            stack.pop();
                        }
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
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
