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

import net.e6tech.elements.security.hsm.atalla.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyARQCTest extends CommandTest<VerifyARQC> {

    @Test
    void basic() throws Exception {
        String[] fields = new String[10];
        fields[0] = "350";
        fields[1] = "0";
        fields[2] = simulator.asAKB(simulator.IMK_ARQC).getKeyBlock();
        fields[3] = "9901234567890123";
        fields[4] = "45";
        fields[5] = "1234567890123456";
        fields[6] = "922F3E83125EB46B";
        fields[7] = "0123456789ABCDEF0123456789ABCDEF";
        fields[8] = "0000";
        fields[9] = "";
        getCommand().setFields(fields);
        Message message = getCommand().process();
        assertTrue(message.getField(1).equals("8AE6E836084B0E80"));

        fields[1] = "2";
        fields[8] = "0001";
        fields[9] = "0123";

        message = getCommand().process();
        assertTrue(message.getField(1).equals("FA4AD617384E5CEF"));
        assertTrue(message.getField(4).equals("Y"));

        fields[6] = "922F3E83125EB46C";
        message = getCommand().process();
        assertTrue(message.getField(4).equals("N"));

        // null out failure code so that ARPC is empty is not verified.
        fields[9] = "";
        message = getCommand().process();
        assertTrue(message.getField(1).length() == 0);
    }
}
