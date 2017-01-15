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

import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.hsm.AnsiPinBlock;

import java.security.GeneralSecurityException;

/**
 * Created by futeh.
 * Command 31
 *
 * 1 pin block format - 1 for ANSI
 * 2 kpe incoming
 * 3 kpe outgoing key
 * 4 encrypted pin block
 * 5 partial pan
 *
 */
public class TranslatePIN extends Command {
    @Override
    protected String doProcess() throws CommandException {
        if (!getField(1).equals("1")) throw new CommandException(1, new IllegalArgumentException("only ANSI pin block is supported"));
        AnsiPinBlock pinBlock = getPinBlock(2, 4, 5);
        try {
            AnsiPinBlock outgoing = new AnsiPinBlock(getField(5), pinBlock.getPIN());
            byte[] encrypted = simulator.encrypt(new AKB(getField(3)), outgoing.getEncoding());
            return "41#" + Hex.toString(encrypted) + "#Y#";
        } catch (GeneralSecurityException e) {
            throw new CommandException(3, e);
        }
    }
}

