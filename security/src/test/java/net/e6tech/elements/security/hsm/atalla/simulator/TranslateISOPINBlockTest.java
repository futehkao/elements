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
import net.e6tech.elements.security.hsm.atalla.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TranslateISOPINBlockTest extends CommandTest<TranslateISOPINBlock> {

    @Test
    public void basic() throws Exception {
        String clearKpeIn ="1PUNE000,0123456789ABCDEFFEDCBA9876543210";
        String clearKpeOut ="1PUNE000,123456789ABCDEF00FEDCBA987654321";
        AKB kpeIn = simulator.asAKB(clearKpeIn);
        AKB kpeOut = simulator.asAKB(clearKpeOut);
        AnsiPinBlock pinBlock = new AnsiPinBlock("123456789012", "1234");

        String[] fields = new String[12];
        fields[0] = "3E";
        fields[1] = "1";  // reserved
        fields[2] = "1"; // ansi block type
        fields[3] = kpeIn.getKeyBlock();;  // reserved
        fields[4] = "M"; // ansi block type
        fields[5] = ""; // incoming KSN
        fields[6] = kpeOut.getKeyBlock(); // KSN
        fields[7] = "M";
        fields[8] = ""; // outgoing KSN;
        fields[9] = Hex.toString(simulator.encrypt(kpeIn, pinBlock.getEncoding()));
        fields[10] = "123456789012";
        fields[11] = "012345678912";

        getCommand().setFields(fields);
        Message message = getCommand().process();
        String output = message.getField(1);

        byte[] pinBlockBytes = simulator.decrypt(kpeOut, output);
        AnsiPinBlock result = new AnsiPinBlock(pinBlockBytes, fields[11]);
        assertEquals("1234", result.getPIN());
    }
}
