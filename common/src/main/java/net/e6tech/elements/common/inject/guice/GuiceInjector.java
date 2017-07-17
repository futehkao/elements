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

package net.e6tech.elements.common.inject.guice;

import com.google.inject.ConfigurationException;
import net.e6tech.elements.common.inject.Injector;

/**
 * Created by futeh.
 */
class GuiceInjector implements Injector {

    com.google.inject.Injector injector;
    GuiceModule module;

    public GuiceInjector(com.google.inject.Injector injector) {
        this.injector = injector;
    }

    @Override
    public void inject(Object object) {
        injector.injectMembers(object);
    }

    @Override
    public <T> T getInstance(Class<T> cls) {
        try {
            return injector.getInstance(cls);
        } catch (ConfigurationException ex) {
            return null;
        }
    }

    @Override
    public <T> T getNamedInstance(Class<T> cls, String name) {
        if (name == null) {
            try {
                return injector.getInstance(cls);
            } catch (ConfigurationException ex) {
                return null;
            }
        }
        return (T) module.getBoundNamedInstance(cls, name);
    }
}
