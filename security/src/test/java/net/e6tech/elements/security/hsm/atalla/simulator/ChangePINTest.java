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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by futeh.
 */
public class ChangePINTest {
    private AtallaSimulator simulator;
    private ChangePIN generate;

    @Before
    public void setup() throws Exception {
        simulator = new AtallaSimulator();
        generate = new ChangePIN();
        generate.simulator = simulator;
    }

    @Test
    public void ibm3624() throws Exception {
        AKB decTab = simulator.importKey(simulator.DEC);
        AKB kpe = simulator.importKey(simulator.KPE_INTERCHANGE);
        AKB kpv3 = simulator.importKey("1V3NE000,3333 3333 3333 3333");

        String partialPan = "123456123456";
        AnsiPinBlock oldPinBlock = new AnsiPinBlock(partialPan, "1234");
        AnsiPinBlock pinBlock = new AnsiPinBlock(partialPan, "4321");
        String[] fields = new String[13];
        fields[0] = "37";
        fields[1] = "2"; // ibm
        fields[2] = "1"; // ansi pin block
        fields[3] = Hex.toString(simulator.encrypt(kpe, oldPinBlock.getEncoding()));  // old pin block (opt)
        fields[4] = kpe.getKeyBlock();  // kpe
        fields[5] = decTab.getKeyBlock();  // decTab
        fields[6] = "3053";  // offset (opt)
        fields[7] = partialPan;  // partial pan
        fields[8] = "F"; // pad
        fields[9] = "4"; // check length 4
        fields[10] = kpv3.getKeyBlock(); // kpv_3
        fields[11] = Hex.toString(simulator.encrypt(kpe, pinBlock.getEncoding())); // new pin block
        fields[12] = partialPan; // partial pan
        generate.fields = fields;
        Message message = generate.process();
        assertTrue("6140".equals(message.getField(2)));
    }
}
