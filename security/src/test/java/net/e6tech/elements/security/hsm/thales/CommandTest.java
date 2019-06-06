/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.security.hsm.thales;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandTest {

    @Test
    void encode() {
        GenerateCVV command = new GenerateCVV();
        command.setBufferSegmentSize(16);
        command.setServiceCode("123");
        command.setExpiry("2009");
        command.setCvk("12345678901234561234567890123456");
        command.setAccountNumber("1234567890123456");
        byte[] encoded = command.encode();

        GenerateCVV c2 = new GenerateCVV();
        c2.decode(encoded);
        byte[] encoded2 = c2.encode();

        assertTrue(Arrays.equals(encoded, encoded2));
        assertEquals(c2.getHeader(), command.getHeader());
        assertEquals(c2.getCommand(), command.getCommand());
        assertEquals(c2.getTrailer(), command.getTrailer());
    }
}
