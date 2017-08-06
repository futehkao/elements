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
import net.e6tech.elements.security.hsm.AnsiPinBlock;

import java.security.GeneralSecurityException;

/**
 * Created by futeh.
 */
public class ChangePIN extends Command {

    @Override
    protected String doProcess() throws CommandException {
        if (!"1".equals(getField(2)))
            return "000200"; // only supports ANSI block type

        if ("2".equals(getField(1))) { // ibm 3624
            if (getField(7).length() != 12)
                return "001200"; // validation data needs to be 12 digit partial pan.
            return ibm3624();
        } else  if ("3".equals(getField(1))) { // visa pvv
            return visaPVV();
        }

        return null;
    }

    protected String ibm3624() throws CommandException {
        String oldPinBlock = getField(3);
        String oldOffset = getField(6);
        String validation = getField(7); // need to match ansi pin block acct length
        String pad = getField(8);

        IBM3624PINOffset ibm = new IBM3624PINOffset();
        // decTab
        setDecimalizationTable(ibm, 5);

        byte[] pvvKey = decryptKey(10);

        // check old Pin block
        if (oldPinBlock.length() > 0) {
            AnsiPinBlock ansiPinBlock = getPinBlock(4, 3, 7);
            String offset = null;
            try {
                offset = ibm.generateOffset(pvvKey, validation, pad.charAt(0), ansiPinBlock.getPIN());
            } catch (GeneralSecurityException e) {
                throw new CommandException(0, e);
            }
            if (!oldOffset.equals(offset))
                return "47#N#";
        }

        // new pin block
        AnsiPinBlock ansiPinBlock = getPinBlock(4, 11, 7);
        String offset = null;
        try {
            offset = ibm.generateOffset(pvvKey, validation, pad.charAt(0), ansiPinBlock.getPIN());
        } catch (GeneralSecurityException e) {
            Logger.suppress(e);
            return "001000";
        }

        return "47#Y#" + offset ;
    }

    protected String visaPVV() throws CommandException {
        String oldPinBlock = getField(3);
        String oldOffset = getField(7);
        String pvkiStr = getField(8);
        String partialPan = getField(9); // partial pan 11 digits, pan dat ais 12 digits

        int pvki = Integer.parseInt(pvkiStr);

        byte[] pvvKey = decryptKey(5);

        VisaPVV visa = new VisaPVV();

        if (oldPinBlock.length() > 0) {
            AnsiPinBlock ansiPinBlock = getPinBlock(4, 3, 7);
            String offset = null;
            try {
                offset = visa.generatePVV(pvvKey, partialPan, pvki, ansiPinBlock.getPIN());
            } catch (GeneralSecurityException e) {
                throw new CommandException(0, e);
            }
            if (!oldOffset.equals(offset))
                return "47#N#";
        }

        // new pin block
        AnsiPinBlock ansiPinBlock = getPinBlock(4, 10, 11);
        String offset = null;
        try {
            offset = visa.generatePVV(pvvKey, partialPan, pvki, ansiPinBlock.getPIN());
        } catch (GeneralSecurityException e) {
            Logger.suppress(e);
            return "001000";
        }

        return "47#Y#" + offset ;
    }

}
