/*
 * Copyright 2022 Futeh Kao
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.GeneralSecurityException;

@SuppressWarnings("squid:S2278")
public class GenerateHMAC extends Command {

    // <39B#Message Authentication Key#[Message Authentication Key Part2]#Algorithm#Data#>
    // <49B#MAC#Check Digits#[Check Digits]#>
    public String doProcess() {
        try {
            AKB akb = simulator.getKey("KMAC");
            byte[] kmac = akb.decryptKey(simulator.masterKeyBytes());
            String mac = generateMAC(getField(4), kmac);
            return "49B#" + mac + "#" + akb.checkDigits + "##";
        } catch (GeneralSecurityException e) {
            Logger.suppress(e);
        }
        return "00#000000";
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S2131"})
    public String generateMAC(String verificationData, byte[] key) throws GeneralSecurityException {
        if (key.length != 16)
            throw new GeneralSecurityException("Must be a dual length DES key.");

        final String algorithm = getField(3);
        String hmacAlg;
        if(algorithm.equalsIgnoreCase("h")){
            hmacAlg = "HmacSHA1";
        } else if(algorithm.equals("2")){
            hmacAlg = "HmacSHA256";
        } else {
            throw new GeneralSecurityException("Unsupported HMAC algorithm");
        }
        final Mac sha256HMAC = Mac.getInstance(hmacAlg);
        final SecretKeySpec secretkey = new
                SecretKeySpec(key, hmacAlg);
        sha256HMAC.init(secretkey);
        final byte[] mac =
                sha256HMAC.doFinal(DatatypeConverter.parseHexBinary(verificationData));

        return DatatypeConverter.printHexBinary(mac);
    }
}