/*
 * Copyright 2019 Shell Xu
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
import java.security.GeneralSecurityException;

@SuppressWarnings({"squid:S2278", "squid:S1192"})
/*
    For VISA, CVN (Cryptogram Version Number) = 18, similar to MasterCard
    - Use session key to calculate APRC
    - Diversification = ATC + "000000000000", Unpredictable Number (9F37) is not needed
 */
class VisaCVN18ARQC extends MasterCardARQC {

    public VisaCVN18ARQC(AtallaSimulator simulator) {
        super(simulator);
    }

    protected String computeARPC(String code) throws CommandException {
        byte[] arqcBytes = Hex.toBytes(arqc);
        byte[] codeBytes = Hex.toBytes(code);

        int arcBytesLength = arqcBytes.length + codeBytes.length + 1;
        if (arcBytesLength % 8 != 0)
            arcBytesLength = (arcBytesLength / 8 + 1) * 8;

        byte[] arcBytes = new byte[arcBytesLength];
        System.arraycopy(arqcBytes, 0, arcBytes, 0, arqcBytes.length);
        System.arraycopy(codeBytes, 0, arcBytes, arqcBytes.length, codeBytes.length);
        arcBytes[arqcBytes.length + codeBytes.length] = (byte) 0x80;
        for (int i = arqcBytes.length + codeBytes.length + 1; i < arcBytesLength; i++)
            arcBytes[i] = (byte) 0x00;

        String cryptogram = computeCryptogram(arcBytes).substring(0, 8); // 4 bytes only
        return cryptogram;
    }
}
