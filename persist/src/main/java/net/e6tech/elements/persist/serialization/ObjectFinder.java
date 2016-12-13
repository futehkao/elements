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
package net.e6tech.elements.persist.serialization;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.DefaultObjectFinder;
import net.e6tech.elements.common.serialization.ObjectReference;

import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Created by futeh.
 */
public class ObjectFinder extends DefaultObjectFinder {

    private static Logger logger = Logger.getLogger();

    @Override
    public Object replaceObject(Object obj) {
        return obj;
    }

    @Override
    public boolean hasObjectReference(Resources resources, Class cls) {
        if (super.hasObjectReference(resources, cls)) return true;
        return hasEntityManagerObjectReference(resources, cls);
    }

    @Override
    public ObjectReference toReference(Resources resources, Object object) {
        if (object == null) return null;

        if (hasEntityManagerObjectReference(resources, object.getClass())) {
            EntityManager em = resources.getInstance(EntityManager.class);
            Class cls = object.getClass();
            Object id = null;
            Metamodel model = em.getMetamodel();
            try {
                EntityType type = null;
                try {
                    type = model.entity(cls);
                } catch (IllegalArgumentException ex) {
                }
                if (type == null) return null;
                SingularAttribute attr = type.getId(type.getIdType().getJavaType());
                Member member = attr.getJavaMember();
                if (member instanceof Field) {
                    ((Field) member).setAccessible(true);
                    id = ((Field) member).get(object);
                } else if (member instanceof Method) {
                    ((Method) member).setAccessible(true);
                    id = ((Method) member).invoke(object);
                } else {
                    return null;
                }
            } catch (Throwable th) {
                return null;
            }
            ObjectReference ref = new ObjectReference();
            ref.setId(id);
            ref.setType(cls.getName());
            return ref;
        } else {
            return super.toReference(resources, object);
        }
    }

    @Override
    public Object toObject(Resources resources, ObjectReference ref) {
        try {
            EntityManager em = resources.getInstance(EntityManager.class);
            Class cls = em.getClass().getClassLoader().loadClass(ref.getType());
            if (hasEntityManagerObjectReference(resources, cls)) {
                if (ref.getId() == null) return null;
                Object obj = em.find(cls, ref.getId());
                if (obj == null) return null;
                resources.inject(obj);
                return obj;
            } else {
                return super.toObject(resources, ref);
            }
        } catch (ClassNotFoundException e) {
            throw logger.runtimeException(e);
        } catch (Throwable th) {
            throw logger.runtimeException("Cannot locate instance using ObjectReference " + ref, th);
        }
    }

    protected boolean hasEntityManagerObjectReference(Resources resources, Class cls) {
        try {
            EntityManager em = resources.getInstance(EntityManager.class);
            Metamodel model = em.getMetamodel();
            try {
                return model.entity(cls) != null;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
    }
}