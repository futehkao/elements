/*
 * Copyright 2015-2022 Futeh Kao
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

/**
 * Created by futeh.
 *
 * Translate one PIN block with different encryption key and partial pan
 * Command 3E
 *
 * 1. Incoming PIN block format. Only 1 (ANSI or ISO-0) is supported.
 * 2. Outgoing PIN block format. Only 1 (ANSI or ISO-0) is supported.
 * 3. Incoming KPE.
 * 4. Incoming key management.  Only M is supported
 * 5. Incoming KSN. For DUKPT, not supported.
 * 6. Outgoing KPE
 * 7. Outgoing key management. Only M is supported
 * 8. incoming partial pan
 * 9. outgoing partial pan
 */
public class TranslateISOPINBlock extends Command {
    @Override
    protected String doProcess() throws CommandException {
        if (!"1".equals(getField(1)))
            throw new CommandException(1, new IllegalArgumentException("only ANSI pin block is supported"));
        if (!"1".equals(getField(2)))
            throw new CommandException(2, new IllegalArgumentException("only ANSI pin block is supported"));
        if (!"M".equals(getField(4)))
            throw new CommandException(2, new IllegalArgumentException("only M, master session, is supported for incoming key management"));
        if (!"M".equals(getField(7)))
            throw new CommandException(2, new IllegalArgumentException("only M, master session, is supported for outgoing key management"));

        // getting pinBlock so that we can get the plain pin
        AnsiPinBlock pinBlock = getPinBlock(3, 9, 10);
        AnsiPinBlock outgoing = new AnsiPinBlock(getField(11), pinBlock.getPIN());
        AKB kpeIn = new AKB(getField(3));
        AKB kpeOut = new AKB(getField(6));
        run(3, () -> simulator.decryptKey(kpeIn));
        run(6, () -> simulator.decryptKey(kpeOut));
        byte[] encrypted = run(6, () -> simulator.encrypt(new AKB(getField(6)), outgoing.getEncoding()));
        return "4E#" + Hex.toString(encrypted) + "#Y#" + kpeIn.getCheckDigits() + "#" + kpeOut.getCheckDigits() + "##";
    }
}