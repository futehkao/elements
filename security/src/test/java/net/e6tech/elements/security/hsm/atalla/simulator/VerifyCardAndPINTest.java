/*
 * Copyright 2015-2020 Futeh Kao
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

public class VerifyCardAndPINTest extends CommandTest<VerifyCardAndPIN>{

    @Test
    public void basic() throws Exception {
        AKB kcvv = simulator.asAKB(AtallaSimulator.KCVV);
        Command command = Command.createInstance("<5D#3#" + kcvv.getKeyBlock() + "##41234567890123492511201#>", simulator);
        Message message = command.process();
        assertTrue(message.getField(1).equals("52630622"));
        assertTrue(message.getField(2).equals("08D7"));
    }

    @Test
    public void ibm3624() throws Exception {
        AKB decTab = simulator.asAKB(AtallaSimulator.DEC);
        AKB kpe = simulator.asAKB(AtallaSimulator.KPE_INTERCHANGE);
        AKB kpv = simulator.asAKB(AtallaSimulator.KPV_IBM3624);
        AKB kcvv = simulator.asAKB(AtallaSimulator.KCVV);

        String partialPan = "345678901234";
        AnsiPinBlock pinBlock = new AnsiPinBlock(partialPan, "4321");
        String[] fields = new String[14];
        fields[0] = "3A";
        fields[1] = "2,3"; // ibm
        fields[2] = "41234567890123492511201"; // cvvData
        fields[3] = "526"; // cvv
        fields[4] = kcvv.getKeyBlock();
        fields[5] = Hex.toString(simulator.encrypt(kpe, pinBlock.getEncoding()));
        fields[6] = kpe.getKeyBlock();
        fields[7] = decTab.getKeyBlock();
        fields[8] = "4121";  // offset
        fields[9] = partialPan;
        fields[10] = "F";
        fields[11] = "4";
        fields[12] = kpv.getKeyBlock();
        fields[13] = partialPan;

        getCommand().setFields(fields);
        Message message = getCommand().process();
        assertTrue(message.getField(1).equals("Y"));
        assertTrue(message.getField(2).equals("Y"));
    }

    @Test
    public void visa() throws Exception {
        AKB kpe = simulator.asAKB(AtallaSimulator.KPE_INTERCHANGE);
        AKB kpv = simulator.asAKB(AtallaSimulator.KPV_VISA);
        AKB kcvv = simulator.asAKB(AtallaSimulator.KCVV);

        String partialPan = "345678901234";
        AnsiPinBlock pinBlock = new AnsiPinBlock(partialPan, "4321");
        String[] fields = new String[13];
        fields[0] = "3A";
        fields[1] = "3,3"; // ibm
        fields[2] = "41234567890123492511201"; // cvvData
        fields[3] = "526"; // cvv
        fields[4] = kcvv.getKeyBlock();
        fields[5] = Hex.toString(simulator.encrypt(kpe, pinBlock.getEncoding()));
        fields[6] = kpe.getKeyBlock();
        fields[7] = kpv.getKeyBlock();
        fields[8] = "";  //
        fields[9] = "8823"; // pvv
        fields[10] = "7"; // pvki
        fields[11] = "45678901234";
        fields[12] = partialPan;

        getCommand().setFields(fields);
        Message message = getCommand().process();
        assertTrue(message.getField(1).equals("Y"));
        assertTrue(message.getField(2).equals("Y"));
    }
}
