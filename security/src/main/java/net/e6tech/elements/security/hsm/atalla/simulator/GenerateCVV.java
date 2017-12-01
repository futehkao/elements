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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.security.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S2278")
public class GenerateCVV extends Command {

    // <5D#Algorithm#Header,EMFK.E(KCVV),MAC#Reserved#Data#>
    // <6D#CVV#Check Digits##>
    public String doProcess() {
        if (!"3".equals(getField(1)))
            return "00#000100";

        try {
            AKB akb = new AKB(getField(2));
            byte[] kcvv = akb.decryptKey(simulator.masterKeyBytes());
            String cvv = generateVisaCVV(getField(4), kcvv, 8);
            return "6D#" + cvv + "#" + akb.checkDigit + "##";
        } catch (GeneralSecurityException e) {
            Logger.suppress(e);
        }
        return "00#000000";
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S2131"})
    public String generateVisaCVV (String verificationData, byte [] desKey, int length) throws GeneralSecurityException {
        if (length < 3)
            throw new GeneralSecurityException("length must be more than 3.");
        if (desKey.length != 16)
            throw new GeneralSecurityException("Must be a dual length DES key.");

        // create two blocks.
        StringBuilder builder = new StringBuilder();
        builder.append(verificationData);
        while (builder.length() < 32)
            builder.append("0");
        String verification = builder.toString();
        byte[] blockA = Hex.toBytes(verification.substring(0, 16));
        byte[] blockB = Hex.toBytes(verification.substring(16));

        // single des encrypt blockA and then xor with blockB
        SecretKeySpec key = new SecretKeySpec(desKey, 0, 8, "DES");
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(blockA);
        for (int i = 0 ; i < 8 ; i++)
            encrypted[i] =  (byte) (encrypted[i] ^ blockB[i]);

        // make a triple length key out of double length key.
        byte[] tdesKey = new byte[24];
        System.arraycopy(desKey, 0, tdesKey, 0, 16);
        System.arraycopy(desKey, 0, tdesKey, 16, 8);
        key = new SecretKeySpec(tdesKey, "DESede");
        cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        encrypted = cipher.doFinal(encrypted);

        String result  = Hex.toString(encrypted);

        // use the same algorithm as Visa PVV to decimalize.
        builder = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c >= '0' && c <= '9')
                builder.append(c);
        }
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c >= 'A' && c <= 'F')
                builder.append("" + (c - 'A')); // A becomes 0, B 1, C 2 etc.
        }
        return builder.toString().substring(0, length);
    }
}
