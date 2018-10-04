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

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by barry.
 */
public class SecurityAnnotationEngine {
    private static Logger logger = Logger.getLogger();

    private static final Set<MethodSignature> SKIP_METHODS;

    static {
        SKIP_METHODS = new HashSet<>();
        for (Method method : Object.class.getDeclaredMethods()) {
            SKIP_METHODS.add(new MethodSignature(method));
        }
    }

    private Map<Object, Class> securityProviders = new HashMap<>();
    private Map<String, Map<MethodSignature, Set<String>>> scannedClassMap = new HashMap<>();

    public Map<Object, Class> getSecurityProviders() {
        return securityProviders;
    }

    public void setSecurityProviders(Map<Object, Class> securityProviders) {
        this.securityProviders = securityProviders;
    }

    public <T> Class<? extends T> getSecurityProvider(Class<T> cls) {
        Class roleProvider = securityProviders.get(cls);
        if (roleProvider == null)
            roleProvider = securityProviders.get(cls.getName());
        if (roleProvider == null)
            roleProvider = cls;
        return roleProvider;
    }

    public SecurityAnnotationEngine register(Class cls) {
        Class roleProvider = getSecurityProvider(cls);
        // populate from most specific to least specific.  Once a method signature
        // acquires a role set, it won't be re-populated.
        Map<MethodSignature, Set<String>> methodMap = scannedClassMap.computeIfAbsent(cls.getName(), key -> new HashMap<>());
        scanRoles(roleProvider, methodMap, RolesAllowed.class);
        scanRoles(roleProvider, methodMap, DenyAll.class);
        scanRoles(roleProvider, methodMap, PermitAll.class);

        if (cls != roleProvider) {
            scanRoles(cls, methodMap, RolesAllowed.class);
            scanRoles(cls, methodMap, DenyAll.class);
            scanRoles(cls, methodMap, PermitAll.class);
        }

        if (methodMap.isEmpty()) {
            logger.warn("The roles map is empty, the service object is not protected: " + cls.getName());
        }

        scannedClassMap.put(cls.getName(), methodMap);
        return this;
    }


    public boolean hasAccess(Object instance, Method method, Object[] args, String userRole) {
        if (userRole == null) {
            return hasAccess(instance, method, args, Collections.emptySet());
        }
        Set<String> roles = new HashSet<>();
        roles.add(userRole);
        return hasAccess(instance, method, args, roles);
    }

    public boolean hasAccess(Object instance, Method method, Object[] args, Set<String> userRoles) {
        Set<String> value = lookupRoles(instance, method, args);
        if (value == null) {
            if (logger.isWarnEnabled())
                logger.warn("no security map entry found: class: {} method:{}",
                        instance.getClass().getName(), createMethodSig(method));
            return true;
        }

        if (userRoles.contains("ReadOnly") && method.getAnnotation(GET.class) == null && method.getAnnotation(ReadOnly.class) == null) {
            return false;
        }

        if (userRoles.contains("PermitAll")) {
            return true;
        } else if (value.contains(DenyAll.class.getSimpleName())) {
            return false;
        } else if (value.contains(PermitAll.class.getSimpleName())) {
            return true;
        } else {
            for (String userRole : userRoles) {
                if (value.contains(userRole))
                    return true;
            }
            return false;
        }
    }

    @SuppressWarnings("squid:S1172")
    public Set<String> lookupRoles(Object instance, Method method, Object[] args) {
        Class<?> cls = ClassHelper.getRealClass(instance);
        return lookupRoles(cls, method);
    }

    public Set<String> lookupRoles(Class cls, Method method) {
        MethodSignature methodSig = createMethodSig(method);
        logger.trace("lookupRole: class: {} method:{}", cls.getName(), methodSig);

        Map<MethodSignature, Set<String>> methodMap = scannedClassMap.get(cls.getName());
        if (methodMap == null)
            return Collections.emptySet();

        Set<String> roles = methodMap.get(methodSig);
        logger.trace("==> cls:{} m:{} roles:{}", cls, methodSig, roles);
        if (roles == null)
            roles = Collections.emptySet();
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
                    logger.trace(" method:{} roles:{}", signature, roles);
                }
            }
        }
    }

    private void scanRoles(Class<?> cls, Map<MethodSignature, Set<String>> rolesMap, Class<? extends Annotation> annotationClass) {
        if (cls == null || cls == Object.class)
            return;

        Set<String> classRoles = getRoles(cls, annotationClass);
        for (Method m : cls.getDeclaredMethods()) {
            MethodSignature signature = createMethodSig(m);
            if (SKIP_METHODS.contains(signature)
                    || rolesMap.get(signature) != null) { // already populated
                continue;
            }

            Set<String> methodRoles = getRoles(m, annotationClass);
            Set<String> resultRoles = !methodRoles.isEmpty() ? methodRoles : classRoles;
            if (!resultRoles.isEmpty()) {
                rolesMap.put(signature, resultRoles);
            }
        }

        scanRoles(cls.getSuperclass(), rolesMap, annotationClass);
    }

    @SuppressWarnings({"squid:S134", "squid:S3776", "squid:S3878"})
    private Set<String> getRoles(AnnotatedElement element, Class<? extends Annotation> annotationClass) {
        if (annotationClass.equals(RolesAllowed.class)) {
            RolesAllowed rolesAllowed = element.getAnnotation(RolesAllowed.class);
            if (rolesAllowed != null) {
                Set<String> set = new HashSet<>();
                Collections.addAll(set, rolesAllowed.value());
                return set;
            } else {
                return Collections.emptySet();
            }
        } else {
            Set<String> set = new HashSet<>();
            Annotation annotation = element.getAnnotation(annotationClass);
            if (annotation != null) {
                set.add(annotationClass.getSimpleName());
                return set;
            } else {
                return Collections.emptySet();
            }
        }
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
                MethodSignature other = (MethodSignature) obj;
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
