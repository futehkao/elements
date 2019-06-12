/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.launch.LaunchController;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class AtomTestCase {

    public static Provision provision;

    protected LaunchController createLaunchController() {
        return new LaunchController().launchScript("classpath://net/e6tech/elements/common/resources/AtomTestCase.groovy")
                .addLaunchListener((p)-> provision = p);
    }

    @BeforeEach
    public void boot() {
        if (provision == null) {
            createLaunchController().launch();
        }

        if (void.class == Void.class) {
            System.out.println("Void is void");
        }
    }

    @Test
    public void load() {
        AtomTestSample2 sample2 = provision.getResourceManager().getAtomResource("sample2", "_sample2");
        assertTrue(sample2.getSampleName().equals("sample"));
        assertTrue(sample2.getSample().initialized == 1);
        assertTrue(sample2.getSample().started == 1);

        assertTrue(sample2.getSampleName2().equals("sample"));
        assertTrue(sample2.getSample2().initialized == 1);
        assertTrue(sample2.getSample2().started == 1);
    }
}
