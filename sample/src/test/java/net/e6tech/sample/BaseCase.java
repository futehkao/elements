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

package net.e6tech.sample;

import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by futeh.
 */
public class BaseCase {

    public static Provision provision;

    @BeforeEach
    public void launch() {
        LaunchController controller = new LaunchController();
        controller.launchScript("conf/provisioning/sample.groovy")
                .addLaunchListener(p -> provision = p.getInstance(Provision.class))
                .launch();
    }

    @Test
    void basic() {

    }
}
