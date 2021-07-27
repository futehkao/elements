/*
 * Copyright 2016 Futeh Kao
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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by futeh.
 */
public class ResourceManagerTest {

    @Test
    public void basic() throws Exception{
        ResourceManager resourceManager = new ResourceManager();
        resourceManager.load("src/test/conf/simple.groovy");
        resourceManager.getAtoms();
    }

    @Test
    public void loadFromClassPath() throws Exception {
        ResourceManager resourceManager = new ResourceManager();
        resourceManager.load("classpath://net/e6tech/elements/common/resources/FX Trader Joe's.groovy");
        resourceManager.getAtoms();
    }

    @Test
    void getNamedInstance() {
        ResourceManager resourceManager = new ResourceManager();
        resourceManager.rebindNamedInstance(X.class, "x", new X());
        UnitOfWork unitOfWork = new UnitOfWork(resourceManager);
        unitOfWork.preOpen(res -> {
            X x = res.getNamedInstance(X.class, "x");
            assertNotNull(x);
        });

        unitOfWork.accept(Resources.class, res -> {
            X x = res.getNamedInstance(X.class, "x");
            assertNotNull(x);
        });

        Map<String, Object> map = resourceManager.getModule().listBindings(X.class);
        assertTrue(!map.isEmpty());
        Map<Type, Map<String, Object>> bindings = resourceManager.getModule().listBindings();
    }

    @Test
    public void eval() throws Exception{
        ResourceManager resourceManager = new ResourceManager();
        resourceManager.load("src/test/conf/simple.groovy");
        resourceManager.getScripting().eval("quit= { println 'exit'}");
        Object quit = resourceManager.nullableVar("quit");
        resourceManager.getScripting().eval("{XX=123; YY='Hello World'}");
        resourceManager.getScripting().eval("quit();");
        resourceManager.getScripting().eval("binding.removeVariable('quit')");
        assertThrows(Exception.class, () -> {
            resourceManager.getScripting().eval("quit();");
        });
        resourceManager.getScripting().put("quit", quit);
        resourceManager.getScripting().eval("quit()");

        assertEquals(123, (int) resourceManager.nullableVar("XX"));
        assertEquals("Hello World", resourceManager.nullableVar("YY"));
    }

    public static class X {

    }
}
