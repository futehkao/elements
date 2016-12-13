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
package net.e6tech.elements.persist.hibernate;

import net.e6tech.elements.common.serialization.Uninitialized;
import net.e6tech.elements.persist.serialization.ObjectFinder;
import org.hibernate.Hibernate;
import org.hibernate.collection.internal.PersistentBag;
import org.hibernate.collection.internal.PersistentList;
import org.hibernate.collection.internal.PersistentMap;
import org.hibernate.collection.internal.PersistentSet;
import org.hibernate.collection.spi.PersistentCollection;

import java.util.*;

/**
 * Created by futeh.
 */
public class HibernateObjectFinder extends ObjectFinder {

    @Override
    public Object replaceObject(Object obj) {
        if (!Hibernate.isInitialized(obj)) return new Uninitialized();

        if (obj instanceof PersistentCollection) {
            // return
            if (obj instanceof PersistentBag) {
                List list = new ArrayList();
                list.addAll((PersistentBag) obj);
                return list;
            } else if (obj instanceof PersistentList) {
                List list = new ArrayList();
                list.addAll((PersistentList) obj);
                return list;
            } else if (obj instanceof PersistentSet) {
                Set set = new HashSet();
                set.addAll((PersistentSet) obj);
                return set;
            } else if (obj instanceof PersistentMap) {
                Map map = new HashMap<>();
                map.putAll((PersistentMap) obj);
                return map;
            }
        }

        return obj;
    }
}
