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

package net.e6tech.elements.common.resources.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPathsTest {

    @Test
    @SuppressWarnings("unchecked")
    void equalsTest() {
        PluginPaths p1 = PluginPaths.of(PluginPath.of(String.class, "A").and(Long.class, "1"))
                .add(PluginPath.of(Iterable.class, "S").and(Long.class, "2"));

        PluginPaths p2 = PluginPaths.of(PluginPath.of(String.class, "A").and(Long.class, "1"))
                .add(PluginPath.of(Iterable.class, "S").and(Long.class, "2"));

        assertTrue(p1.equals(p2));
        assertTrue(p1.hashCode() == p2.hashCode());

        p2 = PluginPaths.of(PluginPath.of(String.class, "A").and(Long.class, "1"))
                .add(PluginPath.of(Iterable.class, "S").and(Long.class, "1"));

        assertFalse(p1.equals(p2));
        assertFalse(p1.hashCode() == p2.hashCode());

    }
}
