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

import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.Constructor;

/**
 * Created by futeh.
 */
public class ModuleFactory {

    private static ModuleFactory instance = new ModuleFactory(net.e6tech.elements.common.inject.spi.ModuleImpl.class);
    private Class<? extends Module> implementation = net.e6tech.elements.common.inject.spi.ModuleImpl.class;

    public ModuleFactory( Class<? extends Module> implementation) {
        this.implementation = implementation;
    }

    public static ModuleFactory getInstance() {
        return instance;
    }

    public static void setInstance(ModuleFactory factory) {
        instance = factory;
    }

    public Class<? extends Module> getImplementation() {
        return implementation;
    }

    public Module create() {
        try {
            Constructor<? extends Module> constructor = implementation.getConstructor(ModuleFactory.class);
            return constructor.newInstance(this);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }
}
