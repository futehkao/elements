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
package net.e6tech.elements.common.instance;

import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.ObjectFinder;
import net.e6tech.elements.common.serialization.ObjectLocator;
import net.e6tech.elements.common.serialization.ObjectReference;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * This class is used to set fields with Delegate annotation. There are
 * two use cases.  First, an instance of the implementation class is created and then
 * delegate fields are set.  Second, if the implementation class is an abstract class,
 * an interceptor is created with delegate fields set.  In such a case, when a method is
 * called, delegate fields are search to find the most appropriate method to call.  In
 * a way, this is somewhat similar to multiple inheritance.
 *
 * Created by futeh.
 */
public class Instance {
    Interceptor interceptor;
    Class implementationClass;
    List<Field> delegateFields;
    Map<Field, Instance> children = new LinkedHashMap<>(); // contains fields marked with @Delegate
    List<Class> searchOrder = new LinkedList<>();
    boolean proxy = false;

    public static List<Field> getDelegateFields(Class cls) {
        Class c = cls;
        List<Field> fieldList = new LinkedList<>();
        while (c != null && ! c.equals(Object.class)) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                if (f.getAnnotation(Delegate.class) != null) {
                    f.setAccessible(true);
                    fieldList.add(f);
                }
            }
            c = c.getSuperclass();
        }
        return fieldList;
    }

    public <T> T newInstance(Resources resources, Object ... delegates) {
        References references = new References();
        Handler handler = new Handler(resources, searchOrder);
        if (delegates != null) {
            for (Object obj : delegates) {
                references.put(obj.getClass(), obj);
            }
        }
        return (T) createInstance(resources, null, handler, references);
    }

    public Instance(Class cls, Interceptor interceptor) {
        implementationClass = cls;
        this.interceptor = interceptor;

        // breadth first search to get delegate fields.
        Set<Class> seen = new HashSet<>();
        LinkedList<Class> list = new LinkedList<>();
        list.add(cls);
        while (list.size() > 0) {
            Class c = list.remove();
            searchOrder.add(c);
            List<Field> fields = getDelegateFields(c);
            for (Field f : fields) {
                if (!seen.contains(f.getType())) {
                    seen.add(f.getType());
                    list.add(f.getType());
                }
            }
        }

        if (Modifier.isAbstract(implementationClass.getModifiers())
                && !Modifier.isInterface(implementationClass.getModifiers())) {
            proxy = true;
        }

        delegateFields = getDelegateFields(implementationClass);


        delegateFields.forEach((field) -> {
            field.setAccessible(true);
            Instance child = new Instance(field.getType(), interceptor);
            children.put(field, child);
        });
    }

    public List<Field> getDelegateFields() {
        return delegateFields;
    }

    public Object createInstance(Resources resources, ObjectLocator locator,
                                 String methodName, Class[] parameterTypes) {
        References references = new References();
        Handler handler = new Handler(resources, searchOrder);
        Object obj = createInstance(resources, locator, handler, references);

        if (methodName != null && !Interceptor.isProxyObject(obj)) {
            obj = handler.findImplementor(methodName, parameterTypes);
        }
        return obj;
    }

    protected Object createInstance(Resources resources, ObjectLocator locator, Handler handler,
                                    References references) {
        Object instance = null;

        if (references.get(implementationClass) != null) {
            instance = references.get(implementationClass);
        } else if (locator != null && locator.findObjectReference(implementationClass) != null) {
            // use object reference to create an object
            ObjectReference ref = locator.findObjectReference(implementationClass);
            ObjectFinder finder = resources.getInstance(ObjectFinder.class);
            instance = finder.toObject(resources, ref);
            references.put(implementationClass, instance);
        } else if (proxy) {
            // create an interceptor object.  Need to go through children to set injected fields
            try {
                instance = interceptor.newInstance(implementationClass, handler);
                for (Field field : children.keySet()) {
                    field.set(instance, children.get(field).createInstance(resources, locator, handler, references));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // no object reference ... just a regular injection.
            ObjectFinder finder = resources.getInstance(ObjectFinder.class);
            if (!finder.hasObjectReference(resources, implementationClass)) {
                instance = resources.newInstance(implementationClass);
            }
        }

        if (instance != null) {
            for (Field field : children.keySet()) {
                try {
                    field.set(instance, children.get(field).createInstance(resources, locator, handler, references));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            handler.add(implementationClass, instance);
        }
        return instance;
    }

    /*
      This class is used to implement delegate pattern.  If the implementation class is an abstract class, an
      interceptor is created and the handler is used to dispatch calls to one of the Delegate field.
     */
    private static class Handler implements InterceptorHandler {

        // transient Roles roles;
        transient Map<Class, Object> implementations = new HashMap<>();
        transient List<Class> classes = new LinkedList<>();

        public Handler(Resources resources, List<Class> list) {
            // this.roles = resources.get(Roles.class);
            this.classes = list;
        }

        public void add(Class cl, Object obj) {
            implementations.put(cl, obj);
        }

        public Object get(Class cl) {
            return implementations.get(cl);
        }

        @Override
        public Object invoke(Object target, Method thisMethod, Object[] args) throws Throwable {
            Object obj = findImplementor(thisMethod.getName(), thisMethod.getParameterTypes());
            if (obj != null) {
                Method m = obj.getClass().getMethod(thisMethod.getName(), thisMethod.getParameterTypes());
                return m.invoke(obj, args);
            } else {
                return thisMethod.invoke(target, args);
            }
        }

        public Object findImplementor(String methodName, Class[] parameterTypes) {
            for (Class cl : classes) {
                try {
                    Method m = cl.getMethod(methodName, parameterTypes);
                    if (m != null && !Modifier.isAbstract(m.getModifiers())) {
                        Object obj = implementations.get(cl);
                        if (obj != null) {
                            /* PermitAll permitAll = m.getDeclaredAnnotation(PermitAll.class);
                            RolesAllowed rolesAllowed = m.getDeclaredAnnotation(RolesAllowed.class);
                            PermitAll clPermitAll = (PermitAll) cl.getDeclaredAnnotation(PermitAll.class);
                            RolesAllowed clRolesAllowed = (RolesAllowed) cl.getDeclaredAnnotation(RolesAllowed.class);
                            cl.getDeclaredAnnotation(RolesAllowed.class); */
                            return obj;
                        }
                    }
                } catch (NoSuchMethodException | SecurityException e) {
                }
            }
            return null;
        }
    }

    private static class References {
        private Map<String, Map<String, Object>> references = new LinkedHashMap<>();

        public void put(Class cls, Object reference) {
            String packageName = cls.getPackage().getName();
            Map<String, Object> pack = references.computeIfAbsent(packageName, (key) -> new LinkedHashMap<String, Object>());
            pack.put(cls.getSimpleName(), reference);
        }

        public Object get(Class cls) {
            String packageName = cls.getPackage().getName();
            Map<String, Object> pack = references.get(packageName);
            Object reference = null;
            if (pack != null) {
                reference = pack.get(cls.getSimpleName());
            }

            if (reference != null) return reference;

            for (Map<String, Object> p : references.values()) {
                for (Object obj : p.values()) {
                    if (obj != null && cls.isAssignableFrom(obj.getClass())) {
                        return obj;
                    }
                }
            }
            return null;
        }
    }
}
