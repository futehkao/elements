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

import java.lang.reflect.Method;

public class MethodSignature extends Signature<Method> {
    private Class returnType;
    private Class<?>[] parameterTypes;
    private String signature;
    private Integer hashCode;

    public MethodSignature(Method method) {
        super(method.getName(), method);
        returnType = method.getReturnType();
        parameterTypes = method.getParameterTypes();
     }

     @Override
    public boolean equals(Object obj) {
        if (obj instanceof MethodSignature) {
            MethodSignature other = (MethodSignature) obj;
            if (getName().equals(other.getName())) {
                if (!returnType.equals(other.returnType))
                    return false;
                return equalParamTypes(parameterTypes, other.parameterTypes);
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (hashCode == null) {
            hashCode = getName().hashCode() ^ returnType.hashCode();
        }
        return hashCode;
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

    @Override
    public String toString() {
        if (signature == null) {
            StringBuilder b = new StringBuilder(returnType.getName());
            b.append(' ').append(getName()).append('(');
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
