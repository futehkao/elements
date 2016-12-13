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
package net.e6tech.elements.common.serialization;

import com.google.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Resources;

import java.lang.reflect.Method;


/**
 * Created by futeh.
 */
public class DefaultObjectFinder implements ObjectFinder {

    private static Logger logger = Logger.getLogger();

    @Override
    public Object replaceObject(Object obj) {
        return obj;
    }

    @Override
    public boolean hasObjectReference(Resources resources, Class cls) {
        return objectReferenceGet(cls) != null && objectReferenceSet(cls) != null;
    }

    @Override
    public ObjectReference toReference(Resources resources, Object object) {
        // if object has a getter or setter
        if (object == null) return null;

        if (hasObjectReference(resources, object.getClass())) {
            Method method = objectReferenceGet(object.getClass());
            try {
                return (ObjectReference) method.invoke(object);
            } catch (Throwable e) {
                throw logger.runtimeException(e);
            }
        }
        return null;
    }

    @Override
    public Object toObject(Resources resources, ObjectReference ref) {
        if (ref == null) return null;
        try {
            Object obj = resources.newInstance(ref.getTypeClass());
            // set ObjectReference
            Method method = objectReferenceSet(obj.getClass());
            if (method != null) {
                try {
                    method.invoke(obj, ref);
                } catch (Throwable e) {
                    logger.warn(e.getMessage(), e);
                }
            }
            return obj;
        } catch (Exception e) {
            throw logger.runtimeException(e);
        }
    }

    protected Method objectReferenceGet(Class cls) {
        try {
            Method method = cls.getMethod("getObjectReference");
            if (ObjectReference.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        } catch (NoSuchMethodException e) {
            return null;
        }
        return null;
    }

    protected Method objectReferenceSet(Class cls) {
        try {
            return cls.getMethod("setObjectReference", ObjectReference.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
