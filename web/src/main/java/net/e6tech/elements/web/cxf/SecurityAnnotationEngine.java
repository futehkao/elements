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

package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.logging.Logger;
import org.apache.cxf.common.util.ClassHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by barry.
 */
public class SecurityAnnotationEngine {
    private static Logger logger = Logger.getLogger();

    private static final String ROLES_ALLOWED_CLASS_NAME = "javax.annotation.security.RolesAllowed";
    private static final String PERMIT_ALL_CLASS_NAME    = "javax.annotation.security.PermitAll";
    private static final String DENY_ALL_CLASS_NAME      = "javax.annotation.security.DenyAll";

    private static final String ROLE_KEY_PERMIT_ALL = "PERMITALL";
    private static final String ROLE_KEY_DENY_ALL   = "DENYALL";

    private static final Set<String> SKIP_METHODS;
    static {
        SKIP_METHODS = new HashSet<>();
        SKIP_METHODS.addAll(Arrays.asList(
            new String[] {"wait", "notify", "notifyAll",
                          "equals", "toString", "hashCode"}));
    }

    private Map<String,Map<String,String>> scannedClassMap = new HashMap<>();

    public void register(Object object) {
        Class<?> cls = ClassHelper.getRealClass(object);
        if (scannedClassMap.containsKey(cls.getName()))
            return;

        //
        // We scan in the following order of precedence:
        //
        //   @PermitAll    - will set value of PERMITALL
        //   @RolesAllowed - will set value with list of roles
        //   @DenyAll      - will set value of DENYALL
        //
        Map<String,String> methodMap = new HashMap<>();
        scanForPermitAll(cls,methodMap);
        scanForDenyAll(cls,methodMap);
        scanForRolesAllowed(cls, methodMap);
        if (methodMap.isEmpty()) {
            logger.warn("The roles map is empty, the service object is not protected: " + cls.getName());
        }

        scannedClassMap.put(cls.getName(), methodMap);
    }

    public boolean hasAccess(Object instance, Method method, Object[] args, String userRole) {
        return hasAccess(instance,method,args,Arrays.asList(userRole));
    }

    public boolean hasAccess(Object instance, Method method, Object[] args, List<String> userRoles) {
        String value = lookupRole(instance,method,args);
        if (value == null) {
            logger.warn("no security map entry found: class:" + instance.getClass().getName() + " method:" + createMethodSig(method));
            return true;
        }
        else if (value.equals(ROLE_KEY_DENY_ALL))
            return false;
        else if (value.equals(ROLE_KEY_PERMIT_ALL))
            return true;
        else {
            for (String userRole : userRoles) {
                if (value.contains(userRole))
                    return true;
            }
            return false;
        }
    }

    public String lookupRole(Object instance, Method method, Object[] args) {
        Class<?> cls = ClassHelper.getRealClass(instance);
        String methodSig = createMethodSig(method);
        logger.debug("lookupRole: class:" + cls.getName() +" method:" + methodSig);

        Map<String,String> methodMap = scannedClassMap.get(cls.getName());
        if (methodMap == null)
            return null;

        String roles = methodMap.get(methodSig);
        logger.debug("==> cls:" + cls + " m:" + methodSig + " roles:" + roles);
        return roles;
    }

    public void logMethodMap() {
        List<String> clsNameList = new ArrayList<>(scannedClassMap.keySet());
        Collections.sort(clsNameList);
        for (String clsName : clsNameList) {
            logger.debug("registered class: " + clsName);
            Map<String,String> methodMap = scannedClassMap.get(clsName);
            List<String> methodNameList = new ArrayList<>(methodMap.keySet());
            for (String methodName : methodNameList) {
                String roles = methodMap.get(methodName);
                logger.debug("  method: " + methodName + " roles: " + roles);
            }
        }
    }

    private void scanForPermitAll(Class<?> cls, Map<String, String> rolesMap) {
        if (cls == null || cls == Object.class)
            return;

        boolean clsLevelPermitAll = isAnnotationPresent(cls.getAnnotations(), PERMIT_ALL_CLASS_NAME);
        for (Method m : cls.getMethods()) {
            if (SKIP_METHODS.contains(m.getName())) {
                continue;
            }

            boolean methodLevelPerimitAll = isAnnotationPresent(m.getAnnotations(), PERMIT_ALL_CLASS_NAME);
            if (clsLevelPermitAll || methodLevelPerimitAll) {
                rolesMap.put(createMethodSig(m), ROLE_KEY_PERMIT_ALL);
            }
        }

        if (clsLevelPermitAll)
            return;

        scanForPermitAll(cls.getSuperclass(), rolesMap);
        // NOTE: Per Futeh: annotations should not be placed at the interface level
    }

    private void scanForDenyAll(Class<?> cls, Map<String, String> rolesMap) {
        if (cls == null || cls == Object.class)
            return;

        boolean clsLevelDenyAll = isAnnotationPresent(cls.getAnnotations(),DENY_ALL_CLASS_NAME);
        for (Method m : cls.getMethods()) {
            if (SKIP_METHODS.contains(m.getName())) {
                continue;
            }

            boolean methodLevelDenyAll = isAnnotationPresent(m.getAnnotations(),DENY_ALL_CLASS_NAME);
            if (clsLevelDenyAll || methodLevelDenyAll) {
                rolesMap.put(createMethodSig(m), ROLE_KEY_DENY_ALL);
            }
        }

        if (clsLevelDenyAll)
            return;

        scanForDenyAll(cls.getSuperclass(), rolesMap);
        // NOTE: Per Futeh: annotations should not be placed at the interface level
    }

    @SuppressWarnings("squid:S1872")
    private boolean isAnnotationPresent(Annotation[] anns, String annName) {
        for (Annotation ann : anns) {
            if (ann.annotationType().getName().equals(annName)) {
                return true;
            }
        }
        return false;
    }

    private void scanForRolesAllowed(Class<?> cls, Map<String, String> rolesMap) {
        if (cls == null || cls == Object.class) {
            return;
        }
        String classRolesAllowed = getRoles(cls.getAnnotations(), ROLES_ALLOWED_CLASS_NAME);
        for (Method m : cls.getMethods()) {
            if (SKIP_METHODS.contains(m.getName())) {
                continue;
            }
            String methodRolesAllowed = getRoles(m.getAnnotations(), ROLES_ALLOWED_CLASS_NAME);
            String theRoles = methodRolesAllowed != null ? methodRolesAllowed : classRolesAllowed;
            if (theRoles != null) {
                rolesMap.put(m.getName(), theRoles);
                rolesMap.put(createMethodSig(m), theRoles);
            }
        }
        if (!rolesMap.isEmpty()) {
            return;
        }

        scanForRolesAllowed(cls.getSuperclass(), rolesMap);
        // NOTE: Per Futeh: annotations should not be placed at the interface level
    }

    @SuppressWarnings("squid:S134")
    private String getRoles(Annotation[] anns, String annName) {
        for (Annotation ann : anns) {
            if (ann.annotationType().getName().equals(annName)) {
                try {
                    Method valueMethod = ann.annotationType().getMethod("value", new Class[]{});
                    String[] roles = (String[])valueMethod.invoke(ann, new Object[]{});
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < roles.length; i++) {
                        sb.append(roles[i]);
                        if (i + 1 < roles.length) {
                            sb.append(" ");
                        }
                    }
                    return sb.toString();
                } catch (Exception ex) {
                    Logger.suppress(ex);
                }
                break;
            }
        }
        return null;
    }

    private String createMethodSig(Method method) {
        StringBuilder b = new StringBuilder(method.getReturnType().getName());
        b.append(' ').append(method.getName()).append('(');
        boolean first = true;
        for (Class<?> cls : method.getParameterTypes()) {
            if (first) {
                b.append(", ");
                first = false;
            }
            b.append(cls.getName());
        }
        b.append(')');
        return b.toString();
    }
}
