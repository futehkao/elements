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
import net.e6tech.elements.security.hsm.atalla.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class GenerateCVVTest {
    private AtallaSimulator simulator;
    private GenerateCVV generate;

    @BeforeEach
    public void setup() throws Exception {
        simulator = new AtallaSimulator();
        generate = new GenerateCVV();
        generate.simulator = simulator;
    }

    @Test
    public void basic() throws Exception {
        String[] headerAndKey = simulator.KCVV.split(",");
        String header = headerAndKey[0];
        byte[] key = Hex.toBytes(headerAndKey[1]);

        AKB akb = simulator.asAKB(header, key);
        String[] fields = new String[5];
        fields[0] = "5D";
        fields[1] = "3";
        fields[2] = akb.getKeyBlock();
        fields[3] = "";
        fields[4] = "41234567890123458701101";
        generate.setFields(fields);
        Message message = generate.process();
        assertTrue(message.getField(1).equals("56149820"));
        assertTrue(message.getField(2).equals("08D7"));
    }
}
