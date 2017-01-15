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

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * Created by futeh.
 */
public class VisaPVV {

    public static void main(String ... args) throws Exception {
        VisaPVV visa = new VisaPVV();

        String result = visa.generatePVV(Hex.toBytes("0123456789ABCDEFFEDCBA9876543210"),
                "56789987654", 1, "1234");
        System.out.println(result); // should be 9365


        byte[] kpe = Hex.toBytes("1111 1111 1111 1111");
        byte[] kpv = Hex.toBytes("3333 3333 3333 3333 4444 4444 4444 4444");
        String partialPan = "12345612345";
        int pvki = 1;
        System.out.println(visa.generatePVV(kpv, partialPan, pvki, "4321"));  // should be 8449
    }

    public String generatePVV(byte[] pvvKeyBytes, String partialPan, int pvki, String pin) throws GeneralSecurityException {
        if (partialPan.length() != 11) throw new GeneralSecurityException("invalid partial pan length, must be 11");
        if (pin.length() != 4) throw new GeneralSecurityException("invalid pin length, must be 4");
        if (pvki > 9 || pvki < 0) throw new GeneralSecurityException("invalid pvki, must be greater than or equal to 0 and less than 10.");
        pvvKeyBytes = AKB.normalizeKey(pvvKeyBytes);
        SecretKey pvv = new SecretKeySpec(pvvKeyBytes, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, pvv);
        byte[] clearTex = Hex.toBytes(partialPan + pvki + pin);
        byte[] encrypted = cipher.doFinal(clearTex);

        String result  = Hex.toString(encrypted);

        // use the same algorithm as Visa PVV to decimalize.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c >= '0' && c <= '9') builder.append(c);
        }
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c >= 'A' && c <= 'F') builder.append("" + (c - 'A')); // A becomes 0, B 1, C 2 etc.
        }
        return builder.toString().substring(0, 4);
    }
}
