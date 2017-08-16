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

/**
 * field 0 - 11B
 * field 1 - variant, only 0 is supported
 * field 2 - working encrypted with kek
 * field 3 - AKB kek encrypted with master key
 *
 * return 21B, AKB of the working key encrypted with master key, check digits
 *
 * Created by futeh.
 */
public class ImportWorkingKey extends Command {
    @Override
    protected String doProcess() throws CommandException {
        try {
            AKB akb = simulator.importKey(new AKB(getField(3)), Hex.toBytes(getField(2)));
            return "21B#" + akb.getKeyBlock() + "#" + akb.getCheckDigit();
        } catch (Exception e) {
            AtallaSimulator.logger.error("ImportWorkingKey", e);
        }
        return "00#000000";
    }
}
