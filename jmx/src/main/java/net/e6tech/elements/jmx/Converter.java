/*
Copyright 2015-2019 Futeh Kao

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

package net.e6tech.elements.jmx;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class Converter {

    private Converter() {
    }

    public static List convert(TabularData table) {
        List rows = new ArrayList<>();
        Set<List<?>> keys = (Set<List<?>>) table.keySet();
        for (List<?> key : keys) {
            Map<String, Object> row = convert(table.get(key.toArray()));
            if (row == null) {
                // do nothing
            } else if (row.size() == 2 && row.containsKey("key") && row.get("value") instanceof Map) {
                LinkedHashMap m = new LinkedHashMap();
                m.put("key", row.get("key"));
                m.putAll((Map)row.get("value"));
                rows.add(m);
            } else {
                rows.add(row);
            }
        }
        return rows;
    }

    public static Map<String, Object> convert(CompositeData composite) {
        if (composite == null)
            return null;
        Map<String, Object> map = new LinkedHashMap<>();
        CompositeType type = composite.getCompositeType();
        Set<String> keys = type.keySet();
        for (String key : keys) {
            Object value = composite.get(key);
            if (value != null) {
                map.put(key, convert(value));
            }
        }
        return map;
    }

    public static Object convert(Object obj) {
        if (obj == null)
            return null;
        Object object = obj;
        if (object.getClass().isArray()) {
            List list = new LinkedList();
            int length = Array.getLength(object);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(object, i);
                list.add(convert(item));
            }
            object = list.toArray();
        } else if (object instanceof TabularData)
            return convert((TabularData) object);
        else if (object instanceof CompositeData)
            return convert((CompositeData) object);
        return object;
    }
}
