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

import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class Primitives {
    public static Map<String, Class> primitives;

    static {
        primitives =new HashMap<>();
        primitives.put(Boolean.TYPE.getName(),Boolean.TYPE);
        primitives.put(Character.TYPE.getName(),Character.TYPE);
        primitives.put(Byte.TYPE.getName(),Byte.TYPE);
        primitives.put(Short.TYPE.getName(),Short.TYPE);
        primitives.put(Integer.TYPE.getName(),Integer.TYPE);
        primitives.put(Long.TYPE.getName(),Long.TYPE);
        primitives.put(Float.TYPE.getName(),Float.TYPE);
        primitives.put(Double.TYPE.getName(),Double.TYPE);
    }

    public static boolean isPrimitive(String name) {
        return primitives.containsKey(name);
    }

    public static boolean isPrimitive(Class cls) {
        return primitives.containsKey(cls.getName());
    }

    public static Class get(String name) {
        return primitives.get(name);
    }

    public static Object defaultValue(Class cls) {
        if (isPrimitive(cls)) {
            if (cls == Boolean.TYPE) return false;
            else if (cls == Character.TYPE) return (char) 0;
            else if (cls == Byte.TYPE) return (byte) 0;
            else if (cls == Short.TYPE) return (short) 0;
            else if (cls == Integer.TYPE) return (int) 0;
            else if (cls == Long.TYPE) return 0L;
            else if (cls == Float.TYPE) return 0f;
            else if (cls == Double.TYPE) return 0d;
        } else {
            if (cls == Boolean.class) return false;
            else if (cls == Character.class) return (char) 0;
            else if (cls == Byte.class) return (byte) 0;
            else if (cls == Short.class) return (short) 0;
            else if (cls == Integer.class) return (int) 0;
            else if (cls == Long.class) return 0L;
            else if (cls == Float.class) return 0f;
            else if (cls == Double.class) return 0d;
        }
        return null;
    }
}
