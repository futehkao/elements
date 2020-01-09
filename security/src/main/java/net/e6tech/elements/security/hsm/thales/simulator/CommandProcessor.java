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

package net.e6tech.elements.security.hsm.thales.simulator;

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.hsm.thales.Command;
import net.e6tech.elements.security.hsm.thales.Echo;
import net.e6tech.elements.security.hsm.thales.GenerateCVV;

import java.util.HashMap;
import java.util.Map;

public class CommandProcessor<T extends Command> {

    private static Map<Class<? extends Command>, Class<? extends CommandProcessor>> processors = new HashMap<>();

    static {
        processors.put(Echo.class, CommandProcessor.class);  // this need to be a subclass of CommandProcessor
        processors.put(GenerateCVV.class, CommandProcessor.class);
    }

    private T command;

    @SuppressWarnings("unchecked")
    public static CommandProcessor forCommand(Command command) {
        Class<? extends CommandProcessor> cls = processors.get(command.getClass());
        try {
            CommandProcessor processor = cls.getDeclaredConstructor().newInstance();
            processor.setCommand(command);
            return processor;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public T getCommand() {
        return command;
    }

    public void setCommand(T command) {
        this.command = command;
    }
}
