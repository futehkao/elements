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
    private static final String PERMIT_ALL_CLASS_NAME = "javax.annotation.security.PermitAll";
    private static final String DENY_ALL_CLASS_NAME = "javax.annotation.security.DenyAll";

    private static final String ROLE_KEY_PERMIT_ALL = "PERMITALL";
    private static final String ROLE_KEY_DENY_ALL = "DENYALL";

    private static final Set<String> SKIP_METHODS;

    static {
        SKIP_METHODS = new HashSet<>();
        SKIP_METHODS.addAll(Arrays.asList("wait", "notify", "notifyAll", "equals", "toString", "hashCode"));
    }

    private Map<String, Map<MethodSignature, Set<String>>> scannedClassMap = new HashMap<>();

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
        Map<MethodSignature, Set<String>> methodMap = new HashMap<>();
        scanForPermitAll(cls, methodMap);
        scanForDenyAll(cls, methodMap);
        scanForRolesAllowed(cls, methodMap);
        if (methodMap.isEmpty()) {
            logger.warn("The roles map is empty, the service object is not protected: " + cls.getName());
        }

        scannedClassMap.put(cls.getName(), methodMap);
    }

    public boolean hasAccess(Object instance, Method method, Object[] args, String userRole) {
        if (userRole == null) {
            return hasAccess(instance, method, args, Collections.emptyList());
        }
        return hasAccess(instance, method, args, Arrays.asList(userRole));
    }

    public boolean hasAccess(Object instance, Method method, Object[] args, List<String> userRoles) {
        Set<String> value = lookupRole(instance, method, args);
        if (value == null) {
            if (logger.isWarnEnabled())
                logger.warn("no security map entry found: class: {} method:{}",
                        instance.getClass().getName(), createMethodSig(method));
            return true;
        } else if (value.contains(ROLE_KEY_DENY_ALL))
            return false;
        else if (value.contains(ROLE_KEY_PERMIT_ALL))
            return true;
        else {
            for (String userRole : userRoles) {
                if (value.contains(userRole))
                    return true;
            }
            return false;
        }
    }

    @SuppressWarnings("squid:S1172")
    public Set<String> lookupRole(Object instance, Method method, Object[] args) {
        Class<?> cls = ClassHelper.getRealClass(instance);
        MethodSignature methodSig = createMethodSig(method);
        logger.trace("lookupRole: class: {} method:{}", cls.getName(), methodSig);

        Map<MethodSignature, Set<String>> methodMap = scannedClassMap.get(cls.getName());
        if (methodMap == null)
            return Collections.emptySet();

        Set<String> roles = methodMap.get(methodSig);
        logger.trace("==> cls:{} m:{} roles:{}", cls, methodSig, roles);
        return roles;
    }

    public void logMethodMap() {
        if (logger.isTraceEnabled()) {
            List<String> clsNameList = new ArrayList<>(scannedClassMap.keySet());
            Collections.sort(clsNameList);
            for (String clsName : clsNameList) {
                logger.trace("registered class: {}", clsName);
                Map<MethodSignature, Set<String>> methodMap = scannedClassMap.get(clsName);
                List<MethodSignature> methodNameList = new ArrayList<>(methodMap.keySet());
                for (MethodSignature signature : methodNameList) {
                    Set<String> roles = methodMap.get(signature);
                    logger.trace("  method:{} roles:{}", signature, roles);
                }
            }
        }
    }

    private void scanForPermitAll(Class<?> cls, Map<MethodSignature, Set<String>> rolesMap) {
        if (cls == null || cls == Object.class)
            return;

        boolean clsLevelPermitAll = isAnnotationPresent(cls.getAnnotations(), PERMIT_ALL_CLASS_NAME);
        for (Method m : cls.getMethods()) {
            if (SKIP_METHODS.contains(m.getName())) {
                continue;
            }

            boolean methodLevelPermitAll = isAnnotationPresent(m.getAnnotations(), PERMIT_ALL_CLASS_NAME);
            if (clsLevelPermitAll || methodLevelPermitAll) {
                Set<String> set = new HashSet<>();
                set.add(ROLE_KEY_PERMIT_ALL);
                rolesMap.put(createMethodSig(m), set);
            }
        }

        if (clsLevelPermitAll)
            return;

        scanForPermitAll(cls.getSuperclass(), rolesMap);
        // NOTE: Per Futeh: annotations should not be placed at the interface level
    }

    private void scanForDenyAll(Class<?> cls, Map<MethodSignature, Set<String>> rolesMap) {
        if (cls == null || cls == Object.class)
            return;

        boolean clsLevelDenyAll = isAnnotationPresent(cls.getAnnotations(), DENY_ALL_CLASS_NAME);
        for (Method m : cls.getMethods()) {
            if (SKIP_METHODS.contains(m.getName())) {
                continue;
            }

            boolean methodLevelDenyAll = isAnnotationPresent(m.getAnnotations(), DENY_ALL_CLASS_NAME);
            if (clsLevelDenyAll || methodLevelDenyAll) {
                Set<String> set = new HashSet<>();
                set.add(ROLE_KEY_DENY_ALL);
                rolesMap.put(createMethodSig(m), set);
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

    private void scanForRolesAllowed(Class<?> cls, Map<MethodSignature, Set<String>> rolesMap) {
        if (cls == null || cls == Object.class) {
            return;
        }

        Set<String> classRolesAllowed = getRoles(cls.getAnnotations(), ROLES_ALLOWED_CLASS_NAME);
        for (Method m : cls.getMethods()) {
            if (SKIP_METHODS.contains(m.getName())) {
                continue;
            }

            Set<String> methodRolesAllowed = getRoles(m.getAnnotations(), ROLES_ALLOWED_CLASS_NAME);
            Set<String> theRoles = !methodRolesAllowed.isEmpty() ? methodRolesAllowed : classRolesAllowed;
            if (!theRoles.isEmpty()) {
                rolesMap.put(createMethodSig(m), theRoles);
            }
        }
        if (!rolesMap.isEmpty()) {
            return;
        }

        scanForRolesAllowed(cls.getSuperclass(), rolesMap);
        // NOTE: Per Futeh: annotations should not be placed at the interface level
    }

    @SuppressWarnings({"squid:S134", "squid:S3776", "squid:S3878"})
    private Set<String> getRoles(Annotation[] anns, String annName) {
        for (Annotation ann : anns) {
            if (ann.annotationType().getName().equals(annName)) {
                try {
                    Method valueMethod = ann.annotationType().getMethod("value", new Class[]{});
                    String[] roles = (String[]) valueMethod.invoke(ann, new Object[]{});
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < roles.length; i++) {
                        sb.append(roles[i]);
                        if (i + 1 < roles.length) {
                            sb.append(" ");
                        }
                    }
                    Set<String> set = new HashSet<>();
                    for (String role : roles)
                        set.add(role);
                    return set;
                } catch (Exception ex) {
                    Logger.suppress(ex);
                }
                break;
            }
        }
        return Collections.emptySet();
    }

    private MethodSignature createMethodSig(Method method) {
        return new MethodSignature(method);
    }

    static class MethodSignature {

        private String name;
        private Class returnType;
        private Class<?>[] parameterTypes;
        private String signature;

        MethodSignature(Method method) {
            name = method.getName();
            returnType = method.getReturnType();
            parameterTypes = method.getParameterTypes();
        }

        public boolean equals(Object obj) {
            if (obj instanceof MethodSignature) {
                MethodSignature other = (MethodSignature)obj;
                if (name.equals(other.name)) {
                    if (!returnType.equals(other.returnType))
                        return false;
                    return equalParamTypes(parameterTypes, other.parameterTypes);
                }
            }
            return false;
        }

        public int hashCode() {
            return name.hashCode() ^ returnType.hashCode();
        }

        boolean equalParamTypes(Class<?>[] params1, Class<?>[] params2) {
            /* Avoid unnecessary cloning */
            if (params1.length == params2.length) {
                for (int i = 0; i < params1.length; i++) {
                    if (params1[i] != params2[i])
                        return false;
                }
                return true;
            }
            return false;
        }

        public String toString() {
            if (signature == null) {
                StringBuilder b = new StringBuilder(returnType.getName());
                b.append(' ').append(name).append('(');
                boolean first = true;
                for (Class<?> cls : parameterTypes) {
                    if (first) {
                        first = false;
                    } else {
                        b.append(", ");
                    }
                    b.append(cls.getName());
                }
                b.append(')');
                signature = b.toString();
            }
            return signature;
        }
    }
}
