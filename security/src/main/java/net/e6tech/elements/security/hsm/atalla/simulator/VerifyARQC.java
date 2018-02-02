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

public class VerifyARQC extends Command {

    @Override
    protected String doProcess() throws CommandException {
        if ("0".equals(getField(1)) // legacy MasterCard
                || "2".equals(getField(1))) { // EMV 4.1
            MasterCardARQC mc = new MasterCardARQC(simulator);
            return mc.imk(new AKB(getField(2)))
                    .derivationType(Integer.valueOf(getField(1)))
                    .pan(getField(3))
                    .cardSequence(getField(4))
                    .diversification(getField(5))
                    .arqc(getField(6))
                    .dataBlock(getField(7))
                    .arc(getField(8))
                    .failureCode(length() > 9 ? getField(9) : null)
                    .process();
        }

        return "000100"; // only supports MasterCard or EMV 4.1 for now
    }
}
