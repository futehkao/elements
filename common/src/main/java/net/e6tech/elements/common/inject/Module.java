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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by futeh.
 */
public interface Module {

    default Type[] getBindClass(Class cls) {
        Class c = cls;
        Class prev = cls;
        Class bindClass = cls;
        while (c != null && !c.equals(Object.class)) {
            BindClass bind = (BindClass) c.getAnnotation(BindClass.class);
            if (bind != null) {
                if (bind.generics()) {
                    return new Type[] {cls, prev.getGenericSuperclass()};
                } else {
                    bindClass = bind.value();
                }
                break;
            }
            prev = c;
            c = c.getSuperclass();
        }

        if (bindClass.getGenericSuperclass() instanceof ParameterizedType
                && bindClass.getTypeParameters().length == 0
                && bindClass.isAnonymousClass()) {
            // this is for anonymous class
            // for example, new CacheFacade<String, SecretKey>(KeyServer, "clientKeys") {}
            return new Type[]{cls, bindClass.getGenericSuperclass()};
        } else {
            if (bindClass.equals(cls)) return new Type[] {cls};
            return new Type[] {cls, bindClass};
        }
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
