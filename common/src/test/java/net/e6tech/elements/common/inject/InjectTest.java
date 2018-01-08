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

package net.e6tech.elements.common.inject;

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InjectTest {

    @Test
    public void bindProperties() {
        ResourceManager resourceManager = new ResourceManager();
        resourceManager.getInstance(Provision.class)
                .preOpen(res -> {
                    BindPropA a = new BindPropA();
                    a.setDescription("descriptionA");
                    BindPropB b = new BindPropB();
                    b.setDescription("descriptionB");
                    a.setB(b);
                    res.bind(BindPropA.class, a);
                })
                .commit(Resources.class, res -> {
                    BindPropX x = res.newInstance(BindPropX.class);
                    assertTrue(x.getDescription().equals("descriptionB"));
                    assertTrue(x.getA() != null && x.getB() != null);
                });

        resourceManager.getInstance(Provision.class)
                .preOpen(res -> {
                    BindPropA a = new BindPropA();
                    a.setDescription("descriptionA");
                    res.bind(BindPropA.class, a);
                })
                .commit(Resources.class, res -> {
                    BindPropX x = res.newInstance(BindPropX.class);
                    assertTrue(x.getDescription() == null);
                    assertTrue(x.getB() == null);
                    assertTrue(x.getA() != null);
                });
    }

    /**
     * This test demonstrate how BindProperties can be used to auto-bind properties.
     * Note the properties would use the same name.
     */
    @Test
    public void bindNamedProperties() {
        ResourceManager resourceManager = new ResourceManager();
        resourceManager.getInstance(Provision.class)
                .preOpen(res -> {
                    BindPropA a = new BindPropA();
                    a.setDescription("named");
                    BindPropB b = new BindPropB();
                    b.setDescription("named");
                    a.setB(b);
                    res.bindNamedInstance(BindPropA.class, "A", a);

                    a = new BindPropA();
                    a.setDescription("unnamed");
                    b = new BindPropB();
                    b.setDescription("unnamed");
                    a.setB(b);
                    res.bind(BindPropA.class, a);
                })
                .commit(Resources.class, res -> {
                    BindPropNamedX x = res.newInstance(BindPropNamedX.class);
                    assertTrue(x.getA().getDescription().equals("named") && x.getB().getDescription().equals("unnamed"));
                });
    }
}
