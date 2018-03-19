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

public class CheckDigitsTest extends CommandTest<CheckDigits>  {

    @Test
    void basic() throws Exception {
        AKB akb = simulator.asAKB("1mENE000,0123 4567 89AB CDEF FEDC BA98 7654 3210");
        String[] fields = new String[13];
        fields[0] = "7E";
        fields[1] = "S";
        fields[2] = "";
        fields[3] = akb.getKeyBlock();
        getCommand().setFields(fields);
        Message message = getCommand().process();
        assertTrue(akb.getCheckDigits().equals(message.getField(2)));
    }

    @Test
    void atallaSample() throws Exception {
        AKB akb = new AKB("1PUNE000,D3266EC69C61820019F4A9640A8F603DA14F78E154C7522D,55720A06F8964B8F");
        simulator.decryptKey(akb);
        String[] fields = new String[13];
        fields[0] = "7E";
        fields[1] = "S";
        fields[2] = "";
        fields[3] = akb.getKeyBlock();
        getCommand().setFields(fields);
        Message message = getCommand().process();
        assertTrue(akb.getCheckDigits().equals("3BAF"));
        assertTrue(message.getField(2).equals("3BAF"));
    }
}
