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

package net.e6tech.elements.cassandra.etl;

import java.util.Arrays;
import java.util.Objects;

public class PrimaryKey {
    private static final Object[] EMPTY = new Object[0];
    private Object[] keys;

    public PrimaryKey(Object... keys) {
        if (keys == null) {
            this.keys = EMPTY;
        } else {
            this.keys = new Object[keys.length];
            this.keys = Arrays.copyOf(keys, keys.length);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PrimaryKey))
            return false;
        return Arrays.equals(keys, ((PrimaryKey) obj).keys);
    }

    public Object[] getKeys() {
        return keys;
    }

    public <T> T get(int i) {
        return (T) keys[i];
    }

    public int length() {
        return keys.length;
    }

    public PrimaryKey flip(int a, int b) {
        PrimaryKey k = new PrimaryKey(keys);
        Object tmp = k.keys[a];
        k.keys[a] = k.keys[b];
        k.keys[b] = tmp;
        return k;
    }
}
