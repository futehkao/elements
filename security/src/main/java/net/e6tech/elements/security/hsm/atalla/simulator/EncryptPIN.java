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
import net.e6tech.elements.security.hsm.AnsiPinBlock;

import java.security.GeneralSecurityException;

/**
 * Created by futeh.
 */
public class EncryptPIN extends Command {

    public String doProcess() {
        try {
            AKB akb = new AKB(getField(1));
            AnsiPinBlock pinBlock = new AnsiPinBlock(fields[3], fields[2]);
            byte[] encrypted = simulator.encrypt(akb, pinBlock.getEncoding());
            return "40#" + Hex.toString(encrypted) + "#";
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return "00#000000";
    }
}
