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

package net.e6tech.elements.common.inject;

import net.e6tech.elements.common.resources.BindClass;
import net.e6tech.elements.common.resources.BindProperties;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Created by futeh.
 */
public interface Module {

    default String[] getBindProperties(Class cls) {
        Objects.requireNonNull(cls);
        Class<?> c = cls;
        Set<String> set = new LinkedHashSet<>();
        while (c != null && !c.equals(Object.class)) {
            BindProperties annotation = c.getAnnotation(BindProperties.class);
            if (annotation != null) {
                Collections.addAll(set, annotation.value());
            }
            c = c.getSuperclass();
        }
        return set.toArray(new String[set.size()]);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:CommentedOutCodeLine", "squid:S3776"})
    default Type[] getBindClass(Class<?> cls) {
        Objects.requireNonNull(cls);
        Class<?> c = cls;
        Class prev = cls;
        Class bindClass = cls;
        List<Type> list = new ArrayList<>();
        list.add(cls);
        boolean found = false;
        while (c != null && !c.equals(Object.class)) {
            BindClass bind = c.getAnnotation(BindClass.class);
            if (bind != null) {
                if (bind.generics()) {
                    found = true;
                    list.add(prev.getGenericSuperclass());
                } else {
                    bindClass = bind.value();
                }
                break;
            }
            prev = c;
            c = c.getSuperclass();
        }

        if (!found) {
            if (bindClass.getGenericSuperclass() instanceof ParameterizedType
                    && bindClass.getTypeParameters().length == 0
                    && bindClass.isAnonymousClass()) {
                // this is for anonymous class
                // for example, new CacheFacade<String, SecretKey>(KeyServer, "clientKeys") {}
                list.add(bindClass.getGenericSuperclass());
            } else {
                if (!bindClass.equals(cls))
                    list.add(bindClass);
            }
        }

        Iterator<Type> iterator = list.iterator();
        while (iterator.hasNext()) {
            Type type = iterator.next();
            if (type instanceof Class && ((Class) type).isSynthetic())
                iterator.remove();
        }

        return list.toArray(new Type[list.size()]);
    }

    public ModuleFactory getFactory();

    public void add(Module module);

    void bindClass(Class cls, Class service);

    Class getBoundClass(Class cls);

    Object bindInstance(Class cls, Object instance);

    Object unbindInstance(Class cls);

    <T> T getBoundInstance(Class<T> cls);

    Object bindNamedInstance(Class cls, String name, Object instance);

    <T> T getBoundNamedInstance(Class<T> cls, String name);

    Injector build(Module... components);
}
