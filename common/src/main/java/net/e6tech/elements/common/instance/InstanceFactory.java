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

import com.google.inject.Inject;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.resources.Resources;

import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Created by futeh.
 */
public class InstanceFactory {

    @Inject(optional = true)
    private Interceptor interceptor;

    private Map<Class, Instance> instances = new Hashtable<>();

    public List<Field> getDelegateFields(Class cls) {
        Instance instance = instances.get(cls);
        if (instance != null) {
            return instance.getDelegateFields();
        }
        return Instance.getDelegateFields(cls);
    }

    public <T> T newInstance(Resources resources, Class<T> implClass, Object ... delegates) {
        T inst = resources.newInstance(implClass);
        if (inst != null) implClass = (Class) inst.getClass();

        Instance instance = instances.get(implClass);
        if (instance == null) {
            if (interceptor == null) interceptor = Interceptor.getInstance();
            instance = new Instance(implClass, interceptor);
            instances.put(implClass, instance);
        }
        return instance.newInstance(resources, delegates);
    }

}
