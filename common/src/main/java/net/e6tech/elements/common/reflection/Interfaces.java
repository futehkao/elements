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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("squid:S3776")
public class Interfaces {

    private Interfaces() {
    }

    public static Type getGenericType(Class cls, Class intf, int n) {
        TypeInfo head = getGenericType(cls, intf);
        if (head == null)
            return null;
        return head.getSourceType(n);
    }

    private static TypeInfo getGenericType(Class cls, Class intf) {
        if (!intf.isInterface())
            throw new IllegalArgumentException(intf.getName() + " is not an interface");
        TypeInfo head = null;
        Class clazz = cls;
        List<ParameterizedType> list = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            if (clazz.getGenericSuperclass() instanceof ParameterizedType)
                list.add((ParameterizedType) clazz.getGenericSuperclass());
            for (Type type : clazz.getGenericInterfaces()) {
                if (type instanceof ParameterizedType) {
                    ParameterizedType ptype = (ParameterizedType) type;
                    if (intf.isAssignableFrom((Class) ptype.getRawType())) {
                        list.add(ptype);
                        tracePath((Class)ptype.getRawType(), intf, list);
                        Collections.reverse(list);
                        ParameterizedType root = list.remove(0);
                        head = new TypeInfo(root);
                        TypeInfo prev = head;
                        for (ParameterizedType p : list) {
                            prev = prev.linkPrevious(p);
                        }
                        prev.close();
                        break;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return head;
    }

    @SuppressWarnings("squid:S135")
    private static void tracePath(Class start, Class end, List<ParameterizedType> list) {
        for (Type type : start.getGenericInterfaces()) {
            if (!(type instanceof ParameterizedType))
                continue;
            ParameterizedType ptype = (ParameterizedType) type;
            if (ptype.getRawType().equals(end)) {
                list.add(ptype);
                break;
            } else if (end.isAssignableFrom((Class) ptype.getRawType())) {
                list.add(ptype);
                tracePath((Class) ptype.getRawType(), end, list);
                break;
            }
        }
    }

    private static class TypeInfo {
        private ParameterizedType type;
        private List jumpTable = new ArrayList();
        private TypeInfo previousInfo;

        public TypeInfo(ParameterizedType type) {
            this.type = type;
        }

        public TypeInfo linkPrevious(ParameterizedType previous) {
            Class cls = (Class) previous.getRawType();
            for (Type t : type.getActualTypeArguments()) {
                if (t instanceof Class) {
                    jumpTable.add(t);
                } else if (t instanceof TypeVariable){
                    TypeVariable var = (TypeVariable) t;
                    TypeVariable[] previousVars = cls.getTypeParameters();
                    Object jump = var;
                    for (int i = 0 ; i < previousVars.length; i++) {
                        if (var.getName().equals(previousVars[i].getName())) {
                            jump = new Integer(i);
                            break;
                        }
                    }
                    jumpTable.add(jump);
                }
            }
            previousInfo = new TypeInfo(previous);
            return previousInfo;
        }

        public void close() {
            for (Type t : type.getActualTypeArguments()) {
                jumpTable.add(t);
            }
        }

        public Type getSourceType(int n) {
            Object obj = jumpTable.get(n);
            if (obj == null)
                return null;
            if (obj instanceof Integer && previousInfo != null) {
                return previousInfo.getSourceType((Integer)obj);
            } else if (obj instanceof Type) {
                return (Type) obj;
            }
            return null;
        }
    }
}
