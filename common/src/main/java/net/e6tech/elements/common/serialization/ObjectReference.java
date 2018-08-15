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


import net.e6tech.elements.common.util.SystemException;

import java.io.Serializable;

/**
 * Created by futeh.
 */
public class ObjectReference implements Serializable {
    private static final long serialVersionUID = -3574550643843044934L;
    private String type;
    private Serializable id;

    public ObjectReference() {
    }

    public ObjectReference(Class cls, Serializable id) {
        this.type = cls.getName();
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Class getTypeClass() {
        try {
            return ObjectReference.class.getClassLoader().loadClass(getType());
        } catch (ClassNotFoundException e) {
            throw new SystemException(e);
        }
    }

    public Object getId() {
        return id;
    }

    public void setId(Serializable id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object ref) {
        if (!(ref instanceof ObjectReference))
            return false;

        ObjectReference oref = (ObjectReference) ref;
        return oref.type.equals(type) && oref.id.equals(id);
    }

    @SuppressWarnings("squid:S2589")
    @Override
    public int hashCode() {
        if (type == null && id == null)
            return 0;
        if (type != null && id == null)
            return type.hashCode();
        if (type == null && id != null)
            return id.hashCode();
        return type.hashCode() & id.hashCode();
    }

    public String toString() {
        return "ObjectReference: type=" + type + " id=" + id ;
    }
}
