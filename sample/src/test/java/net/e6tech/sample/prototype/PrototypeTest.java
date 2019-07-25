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

package net.e6tech.sample.prototype;

import net.e6tech.sample.BaseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by futeh.
 */
public class PrototypeTest extends BaseCase {

    @Test
    public void basic() {
        Owner owner = (Owner) provision.getResourceManager().getAtom("concrete").get("_owner");
        assertTrue(owner.getName().equals("concrete_owner"));
        assertTrue(owner.getDependent().getName().equals("dependent"));
        assertEquals(owner.getDependent().getPreInit(), 2);
        assertEquals(owner.getDependent().getPostInit(), 1);
        assertEquals(owner.getDependent().getAfter(), 1);
        assertEquals(owner.getDependent().getStarted(), 1);
        assertEquals(owner.getDependent().getInitialized(), 1);
        assertEquals(owner.getDependent().getLaunched(), 1);
        assertEquals(owner.getDependent().getOther(), "default");

        assertTrue(provision.getResourceManager().getAtom("base") == null);
    }
}
