/*
 * Copyright 2015-2020 Futeh Kao
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

import net.e6tech.elements.security.hsm.atalla.Message;

public class VerifyCardAndPIN extends Command {

    @Override
    protected String doProcess() throws CommandException {
        // IBM
        // <32#2#PIN Block Format#EKPE(PIN Block)#Header,EMFK.E(KPE),MAC
        // # Conversion Table#Offset#Validation Data#Pad#
        // Check-Length Parameter#Header,EMFK.E(KPV),MAC#
        //PIN Block Data#>
        // Visa
        // <32#3#PIN Block Format#EKPE(PIN Block)#Header,EMFK.E(KPE),MAC# Header,EMFK.E(KPV),MAC
        // #Reserved#PVV#PVKI#PAN#PIN Block Data#>

        String pinAndCardAlgo = getField(1);
        String[] tokens = pinAndCardAlgo.split(",");
        String pinAlgo = tokens[0];

        String cvvData = getField(2);
        String cvv = getField(3);
        String kcvv = getField(4);
        String pinBlock = getField(5);
        String kpe = getField(6);


        String[] fields = null;
        String pinIndicator = "N";
        if (pinAlgo.equals("2")) {
            String conversion = getField(7);
            String offset = getField(8);
            String validation = getField(9);
            String pad = getField(10);
            String checkLen = getField(11);
            String kpv = getField(12);
            String pinBlockData = getField(13);
            fields = new String[] {
                    "32", "2", "1", pinBlock, kpe, conversion, offset, validation, pad, checkLen, kpv, pinBlockData
            };
        } else  if (pinAlgo.equals("3")) {
            String kpv = getField(7);
            String pvv = getField(9);  // field 8 is empty
            String pvki = getField(10);
            String pan = getField(11);
            String pinBlockData = getField(12);
            fields = new String[] {
                    "32", "3", "1", pinBlock, kpe, kpv, "", pvv, pvki, pan, pinBlockData
            };
        }
        StringBuilder builder = new StringBuilder();
        if (fields != null) {
            builder.append("<");
            for (String f : fields) {
                builder.append(f).append("#");
            }
            builder.append(">");

            Message result = createInstance(builder.toString(), simulator).process();
            pinIndicator = result.getField(1);
        }

        Message result = createInstance("<5D#3#" + kcvv + "##" + cvvData +"#>", simulator).process();
        String expectedCvv = result.getField(1);
        String cvvCheckDigit = result.getField(2);
        String cvvIndicator = expectedCvv.startsWith(cvv) ? "Y" : "N";
        builder.setLength(0);
        return builder.append("4A#").append(cvvIndicator).append("#")
                .append(pinIndicator).append("#")
                .append(cvvCheckDigit).append("#")
                .toString();
    }
}
