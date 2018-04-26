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

package net.e6tech.elements.security.hsm.atalla.simulator;

import net.e6tech.elements.common.util.SystemException;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.ParameterizedType;

/**
 * Created by futeh.
 */
public abstract class CommandTest<T extends Command> {

    protected AtallaSimulator simulator;
    protected T command;

    @BeforeEach
    public void setup() throws Exception {
        simulator = new AtallaSimulator();
        initCommand();
    }

    protected void initCommand() {
        ParameterizedType type = (ParameterizedType) getClass().getGenericSuperclass();
        Class cls = (Class) type.getActualTypeArguments()[0];
        try {
            command = (T) cls.getDeclaredConstructor().newInstance();
            command.simulator = simulator;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    protected T getCommand() {
        return command;
    }
}
