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

import java.security.GeneralSecurityException;

public class CheckDigits extends Command {

    public String doProcess() {
        if (!"S".equals(getField(1)))
            return "00#000100";

        try {
            AKB akb = new AKB(getField(3));
            simulator.decryptKey(akb);
            return "8E#" + getField(1) + "#" + akb.getCheckDigits() + "#";
        } catch (GeneralSecurityException e) {
            Logger.suppress(e);
        }
        return "00#000000";
    }
}
