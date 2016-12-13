/*
 * Copyright 2015 Futeh Kao
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
import org.junit.Before;
import org.junit.Test;

/**
 * Created by futeh.
 */
public class ProvisionTestCase {

    public static Provision provision;

    protected LaunchController createLaunchController() {
        return new LaunchController().launchScript("classpath://net/e6tech/elements/common/resources/ResourcesTestCase.groovy")
                .addLaunchListener((p)-> provision = p);
    }

    @Before
    public void boot() {
        if (provision == null) {
            createLaunchController().launch();
        }
    }

    @Test
    public void load() {
        provision.getResourceManager().getAtoms();
    }
}
