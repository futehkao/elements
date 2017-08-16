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
import net.e6tech.elements.security.hsm.atalla.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class TransactionPINBlockTest extends CommandTest<TranslatePINBlock> {

    private Message message;

    @Test
    public void basic() throws Exception {
        String clearKpeIn ="1PUNE000,0123456789ABCDEFFEDCBA9876543210";
        String clearKpeOut ="1PUNE000,123456789ABCDEF00FEDCBA987654321";
        AKB kpeIn = simulator.asAKB(clearKpeIn);
        AKB kpeOut = simulator.asAKB(clearKpeOut);
        AnsiPinBlock pinBlock = new AnsiPinBlock("123456789012", "1234");

        String[] fields = new String[10];
        fields[0] = "335";
        fields[1] = "";  // reserved
        fields[2] = "1"; // ansi block type
        fields[3] = "";  // reserved
        fields[4] = "1"; // ansi block type
        fields[5] = kpeIn.getKeyBlock();
        fields[6] = kpeOut.getKeyBlock();
        fields[7] = Hex.toString(simulator.encrypt(kpeIn, pinBlock.getEncoding()));
        fields[8] = "123456789012";
        fields[9] = "012345678912";

        getCommand().setFields(fields);
        Message message = getCommand().process();
        String output = message.getField(1);

        byte[] pinBlockBytes = simulator.decrypt(kpeOut, output);
        AnsiPinBlock result = new AnsiPinBlock(pinBlockBytes, fields[9]);
        assertTrue(result.getPIN().equals("1234"));
    }
}
