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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class AtallaSimulatorTest {

    private AtallaSimulator simulator;

    @BeforeEach
    public void setup() throws Exception {
        simulator = new AtallaSimulator();
    }

    @Test
    public void kek() throws Exception {
        AKB akb = simulator.importKey(simulator.KEK_KPE);
        byte[] recovered = simulator.decryptKey(akb);
        String plainKey = simulator.KEK_KPE.split(",")[1];
        assertTrue(Hex.toString(Hex.toBytes(plainKey)).equals(Hex.toString(recovered)));
    }

    @Test
    public void kcvv() throws Exception {
        AKB akb = simulator.importKey(simulator.KCVV);
        byte[] recovered = simulator.decryptKey(akb);
        String plainKey = simulator.KCVV.split(",")[1];
        assertTrue(Hex.toString(Hex.toBytes(plainKey)).equals(Hex.toString(recovered)));
        assertTrue(akb.getKeyBlock().equals("1CDNE000,4F7EFE3F44984427CF46B823CE4BDE1839E35E6F46EB2814,26A5545D383FA701"));
    }

    @Test
    public void dec() throws Exception {
        String decTab = "8351296477461538";
        AKB akb = simulator.importKey("1nCNE000," + decTab);
        String recovered = Hex.toString(simulator.decryptKey(akb));
        assertTrue(recovered.equals(decTab));
        assertTrue(akb.getKeyBlock().equals("1nCNE000,2162CD77E8293FE4DC328EAB53BC3A2B0A3AFE1B299F07D2,111746BEB588C65B"));
    }

}
