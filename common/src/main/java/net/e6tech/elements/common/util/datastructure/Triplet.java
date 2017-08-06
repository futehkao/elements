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

package net.e6tech.elements.common.util.datastructure;

import java.util.Objects;

/**
 * Created by futeh.
 */
public class Triplet<U, D, S>  {

    private U x;
    private D y;
    private S z;

    public Triplet(U up, D down, S strange) {
        this.x = up;
        this.y = down;
        this.z = strange;
    }

    public U x() {
        return x;
    }

    public D y() {
        return y;
    }

    public S z() {
        return z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof Triplet) {
            Triplet t = (Triplet) o;
            if (x != null ? !x.equals(t.x) : t.x != null)
                return false;
            if (y != null ? !y.equals(t.y) : t.y != null)
                return false;
            return !(z != null ? !z.equals(t.z) : t.z != null);
        }
        return false;
    }
}
