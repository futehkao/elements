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
import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.security.Hex;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S2278")
public class IBM3624PINOffset {

    Map<Character, Character> decimalizationTable = new HashMap<>();

    public IBM3624PINOffset() {
        try {
            setDecimalizationTable("0123456789012345");
        } catch (GeneralSecurityException e) {
            // not happening
            Logger.suppress(e);
        }
    }

    public void setDecimalizationTable(String decString) throws GeneralSecurityException {
        if (decString.length() != 16)
            throw new GeneralSecurityException("Must be 16 digits");
        for (int i = 0; i < 16; i++) {
            char c = decString.charAt(i);
            if (c < '0' || c > '9')
                throw new GeneralSecurityException("Must be all digits");
        }
        String hexString = "0123456789ABCDEF";
        decimalizationTable.clear();
        for (int i = 0; i < decString.length(); i++) {
            decimalizationTable.put(hexString.charAt(i), decString.charAt(i));
        }
    }

    public String generateOffset(byte[] pvvKey, String acctNum, char pad, String pin) throws GeneralSecurityException {

        if (pin.length() < 4)
            throw new GeneralSecurityException("pin length too short");
        else if (pin.length() > 12)
            throw new GeneralSecurityException("pin length too long");
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9')
                throw new GeneralSecurityException("PIN must be all digits");
        }

        byte[] pvvKeyNormalized = pvvKey;
        pvvKeyNormalized = AKB.normalizeKey(pvvKeyNormalized);
        SecretKey pvv = new SecretKeySpec(pvvKeyNormalized, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");

        StringBuilder builder = new StringBuilder();
        String paddedAcctNum = StringUtil.padRight(acctNum, 16, pad);
        cipher.init(Cipher.ENCRYPT_MODE, pvv);
        byte[] acctNo = Hex.toBytes(paddedAcctNum);
        byte[] encrypted = cipher.doFinal(acctNo);
        String ipin = Hex.toString(encrypted);

        // decimalization ipin is the Intermediate PIN, or sometiems called natural pin.
        for (int i = 0; i < ipin.length(); i++) {
            builder.append(decimalizationTable.get(ipin.charAt(i)));
        }
        ipin = builder.toString();

        // customer pin - natural pin then modulo 10
        builder.setLength(0);
        for (int i = 0; i < pin.length(); i++) {
            int pinDidigit = Integer.parseInt(Character.toString(pin.charAt(i)));
            int ipinDigit = Integer.parseInt(Character.toString(ipin.charAt(i)));
            int offset = pinDidigit - ipinDigit;
            if (offset < 0)
                offset += 10;
            offset = offset % 10;
            builder.append(Integer.toString(offset));
        }
        return builder.toString();
    }

    @SuppressWarnings("all")
    public static void main(String ... args) throws Throwable {
        IBM3624PINOffset ibm = new IBM3624PINOffset();
        ibm.setDecimalizationTable("8351296477461538");
        byte[] pvvKeyBytes = Hex.toBytes("89B07B35A1B3F47E89B07B35A1B3F47E89B07B35A1B3F47E");
        String offset = ibm.generateOffset(pvvKeyBytes, "33333333", 'D', "361436");
        System.out.println("pin offset=" + offset + " should be 756694");

        ibm.setDecimalizationTable("0123456789012345");
        pvvKeyBytes = Hex.toBytes("3333 3333 3333 3333");
        offset = ibm.generateOffset(pvvKeyBytes, "123456123456", 'F', "1234");
        System.out.println("pin offset=" + offset + " should be 3053");
        offset = ibm.generateOffset(pvvKeyBytes, "123456123456", 'F', "4321");
        System.out.println("pin offset=" + offset + " should be 6140");
    }
}
