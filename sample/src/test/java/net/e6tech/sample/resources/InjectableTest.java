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

package net.e6tech.sample.resources;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Injectable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.sample.BaseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class InjectableTest extends BaseCase {

    @Test
    public void basic() {
        Resources resources =provision.getResourceManager().open(null);
        X x = new X();
        x.y.x = x;
        resources.inject(x);
        assertTrue(x.y.resources == resources);
        assertTrue(x.z.resources == resources);

        resources.inject(x);
    }

    @Injectable
    public static class X {
        Y y = new Y();
        @Injectable
        Z z = new Z();
    }

    @Injectable
    public static class Y {
        @Inject
        Resources resources;
        X x;
    }

    public static class Z {
        @Inject
        Resources resources;
    }
}
