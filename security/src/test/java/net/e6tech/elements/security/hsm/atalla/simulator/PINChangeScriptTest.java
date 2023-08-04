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
public class PINChangeScriptTest {
    static String MASTER_KEY = "2ABC3DEF4567018998107645FED3CBA20123456789ABCDEF";
    private byte[] masterKey = Hex.toBytes(MASTER_KEY); // triple des is 24 bytes,

    static String IMK_ARQC = "1mENE000,0123 4567 89AB CDEF FEDC BA98 7654 3210"; // 0123456789ABCDEFFEDCBA9876543210
    static String IMK_SM_MAC = "1mENE00M,ABCD ABCD EF01 EF01 10FE 10FE DCBA DCBA"; // ABCDABCDEF01EF0110FE10FEDCBADCBA
    static String IMK_SM_ENC = "1mENE00E,1234 1234 5678 5678 8765 8765 4321 4321"; // 12341234567856788765876543214321

    @Test
    void testEncryptPIN() throws GeneralSecurityException {
        AtallaSimulator simulator = new AtallaSimulator();
        AKB akbImkArqc = simulator.asAKB(IMK_ARQC);
        assertEquals("0123456789ABCDEFFEDCBA9876543210", Hex.toString(akbImkArqc.decryptKey(masterKey)));
        AKB akbImkSmEnc = simulator.asAKB(IMK_SM_ENC);
        assertEquals("12341234567856788765876543214321", Hex.toString(akbImkSmEnc.decryptKey(masterKey)));
        AKB akbImkSmMac = simulator.asAKB(IMK_SM_MAC);
        assertEquals("ABCDABCDEF01EF0110FE10FEDCBADCBA", Hex.toString(akbImkSmMac.decryptKey(masterKey)));

        String pan = "5413629900000155";
        String panSequenceNumber = "01";
        String pin = "1234";
        String diversifiedKeyMAC = "1C51FBB5BA7C2A1A4C1AC2AE7557F42F";
        String diversifiedKeyENC = "702A15193B8398D30DC73201DA4576A2";

        String cryptogram = "D17B15836FAB99A2";
        String commandPrefix = "84240002";
        String commandPrefixWithLc = "8424000210";
        String atc = "0016";
        String paddedATC = atc  + "000000000000";
        String diversificaitonData = cryptogram;
        String encryptedPIN = "AB926022634A4AA1";

        String computedMAC = "0E90902E98339EBB";

        // Confirm ARQC key
        // Application Cryptogram Verification: D17B15836FAB99A2
        //  Generate Session Key: 50D12807D33E8810FBE05314C43299D6
        //  Unpredictable number: 01020304
        //  ATC: 0016
        //  Diversified Key AC: 230B91EFFD6B7FB5F8DCBC257F649DDC
        byte[]iccMasterKeyAqrcKeys = CryptoUtil.derivativeICCMasterKeyBytes(simulator, akbImkArqc, pan, panSequenceNumber);
        assertEqualsDESKeyIgoreCheckDigital("230B91EFFD6B7FB5F8DCBC257F649DDC", Hex.toString(iccMasterKeyAqrcKeys));


        // Confirm ENC key
        // Generate Session KeyENC: FBA3F15604B0CEDBF75D270B16AEAF7A
        //   Cryptogram: D17B15836FAB99A2
        //   Diversified Key ENC: 702A15193B8398D30DC73201DA4576A2
        byte[]iccMasterKeySmEncKeys = CryptoUtil.derivativeICCMasterKeyBytes(simulator, akbImkSmEnc, pan, panSequenceNumber);
        assertEqualsDESKeyIgoreCheckDigital("702A15193B8398D30DC73201DA4576A2", Hex.toString(iccMasterKeySmEncKeys));
        byte[] sessionKeySmEncKeys = CryptoUtil.deriveSessionKeyBytes(iccMasterKeySmEncKeys, diversificaitonData);
        assertEquals("FBA3F15604B0CEDBF75D270B16AEAF7A", Hex.toString(sessionKeySmEncKeys));

        // Confirm MAC key
        // Generate Session KeyMAC: 9A7740FBC0F992D2828630328D7933F5
        //   Cryptogram: D17B15836FAB99A2
        //   Diversified Key MAC: 1C51FBB5BA7C2A1A4C1AC2AE7557F42F
        byte[]iccMasterKeySmMacKeys = CryptoUtil.derivativeICCMasterKeyBytes(simulator, akbImkSmMac, pan, panSequenceNumber);
        assertEqualsDESKeyIgoreCheckDigital("1C51FBB5BA7C2A1A4C1AC2AE7557F42F", Hex.toString(iccMasterKeySmMacKeys));
        byte[] sessionKeySmMacKeys = CryptoUtil.deriveSessionKeyBytes(iccMasterKeySmMacKeys, diversificaitonData);
        assertEquals("9A7740FBC0F992D2828630328D7933F5", Hex.toString(sessionKeySmMacKeys));

        // Calculate MAC
//        byte[] paddedDataToCalculateMac =
//                CryptoUtil.padDatablock(
//                        Hex.toBytes(
//                                // commandPrefixWithLc + atc + cryptogram + encryptedPIN
//                                commandPrefixWithLc + cryptogram + encryptedPIN
//                        )
//                        , (byte) 0x80, (byte) 0x00);
//        assertEquals(computedMAC, CryptoUtil.mac(CryptoUtil.createSecretKey(sessionKeySmMacKeys), paddedDataToCalculateMac, 16));
        // Calculate with bouncycastle
        // The following applicaiton data is matched
        //   - commandPrefixWithLc + atc + cryptogram + encryptedPIN
        byte[] paddedDataToCalculateMac = Hex.toBytes(commandPrefixWithLc + atc + cryptogram + encryptedPIN);
        assertEquals(computedMAC, emvMac(sessionKeySmMacKeys, paddedDataToCalculateMac, 16));
        assertEquals(computedMAC, CryptoUtil.iso9797Alg3Mac(sessionKeySmMacKeys, EMVPINChange.padDatablock(paddedDataToCalculateMac), 16));
    }

    private void assertEqualsDESKeyIgoreCheckDigital(String keyHex1, String keyHex2) {
        assertEquals(keyHex1.length(), keyHex2.length());
        byte[] keyBytes1 = Hex.toBytes(keyHex1);
        byte[] keyBytes2 = Hex.toBytes(keyHex2);
        for (int i = 0; i <  keyBytes1.length; i ++) {
            // the latest digital is check digital, we haven't set adjust it in deriveSessionKeyBytes right now
            assertTrue(  Math.abs(keyBytes1[i] - keyBytes2[i]) <= 1);
        }
    }

    public String emvMac(byte[] macKey, byte[] data, int resultLength) throws GeneralSecurityException {
        return Hex.toString(getMAC(macKey, data)).substring(0, resultLength);
    }

    public byte[] getMAC(byte[] key, byte[] data) {
        BlockCipher cipher = new DESEngine();
        Mac mac = new ISO9797Alg3Mac(cipher, 64, new ISO7816d4Padding());

        KeyParameter keyP = new KeyParameter(key);
        mac.init(keyP);
        mac.update(data, 0, data.length);

        byte[] out = new byte[8];

        mac.doFinal(out, 0);

        return out;
    }

}
