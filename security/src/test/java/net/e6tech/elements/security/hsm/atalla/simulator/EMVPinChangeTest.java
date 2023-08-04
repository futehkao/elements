/*
 * Copyright 2015-2023 Futeh Kao
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
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac;
import org.bouncycastle.crypto.paddings.ISO7816d4Padding;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by shell xu.
 */
public class EMVPinChangeTest {
    @Test
    void testISO7816d4PaddingTest() {
        iso7816d4PaddingTest("12","1280000000000000");
        iso7816d4PaddingTest("12345678901234", "1234567890123480");
        iso7816d4PaddingTest("1234567890123456", "12345678901234568000000000000000");
        iso7816d4PaddingTest("123456789012345622","12345678901234562280000000000000");
    }


    void iso7816d4PaddingTest(String beforePadded, String expectedPadded) {
        assertEquals(expectedPadded, Hex.toString(EMVPINChange.padDatablock(Hex.toBytes(beforePadded))));
    }

}
