/*
 * Copyright 2020 Episode Six
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

import java.security.GeneralSecurityException;

/**
 * @author Shell Xu
 */
public class EMVGenerateMAC extends Command {
    @Override
    protected String doProcess() throws CommandException {
        Request request = this.new Request();
        Response response = this.new Response();
        response.setMacLengthType(request.getMacLengthType());

        // 0: Visa CVN 22
        // 1: Visa CVN 10 and CVN 18
        if ("0".equals(request.getDerivationType()) || "1".equals(request.getDerivationType())) {
            byte[] iccMasterSmMacBytes;
            byte[] sessionSmMacBytes;
            try {
                String imkSmMacCheckDigits = CryptoUtil.calculateCheckDigits(simulator, request.getImkSmMac(), 6);
                response.setImkSmMacCheckDigits(imkSmMacCheckDigits);
                iccMasterSmMacBytes = CryptoUtil.derivativeICCMasterKeyBytes(simulator, new AKB(request.getImkSmMac()), request.getPan(), request.getPanSequenceNumber());
            } catch (GeneralSecurityException e) {
                throw new CommandException(2, e);
            }
            try {
                sessionSmMacBytes = CryptoUtil.deriveSessionKeyBytes(iccMasterSmMacBytes, request.getDiversificationData());
                String sessionSmMacCheckDigits = CryptoUtil.calculateKeyBytesCheckDigits(sessionSmMacBytes, 6);
                response.setSessionSmMacCheckDigits(sessionSmMacCheckDigits);
                String mac = CryptoUtil.mac(sessionSmMacBytes, Hex.toBytes(request.getPaddedData()), request.getMacLength());
                response.setMac(mac);
            } catch (GeneralSecurityException e) {
                throw new CommandException(8, e);
            }

            // == set response
            return response.output();
        }

        return "000100"; // only supports 1
    }

    class Request {
        // 1, Derivation Type
        private String derivationType;
        // 2, Header,EMFK.E(IMK),MAC
        private String imkSmMac;
        // 3, [Application PAN]
        // - the Primary Account Number for the application. This field is also used to indicate the Master Key derivation method.
        //  If this field contains the letter “B” followed by 17 to 19 digits, method B will be used, otherwise method A will be used.
        private String pan;
        // 4, Application PAN Sequence Number
        private String panSequenceNumber;
        // 5, Diversification Data
        // - the value of this field depends on the derivation type specified in field 1.
        // - For the common session derivation algorithm (if the derivation type, Field 1, is 0 or 8) this field contains a 16 byte hexadecimal value as defined in EMV SU-46.
        // - For all other derivation types, this field contains the four hexadecimal characters (2 bytes) of the Application Transaction Counter.
        private String diversificationData;
        // 6, MAC Length
        // - 0, More data expected; no MAC verified 0
        // - 1, 32 bits
        // - 2, 48 bits
        // - 3, 64 bits
        //   A 32 bit MAC is expressed as eight hexadecimal digits (0-9, A-F) and written as two groups of four digits, separated by a space.
        //   A 48 bit or 64 bit MAC is expressed as three or four groups of four hexadecimal digits, separated by a space
        private String macLengthType;
        int macLength;
        // 7, [Header,EMFK.E(Continuation-IV),MAC]
        // - contains the continuation-IV, only if the MAC calculation is continued from a previous command.
        // - It must not be present in the first command of a multiple command sequence.
        // - This field contains either a 74 byte value, or is empty.
        // - Only continuation-IVs having the following header are supported in this field: 1IDNE000.
        private String continuationIV;
        // #8, Padded Data
        // - the data used to calculate the MAC
        // - Per the EMV specification, the data should be right-padded with a single byte (expressed as two hexadecimal characters “80”),
        //   followed by a variable number of binary zeros bytes (expressed as two hexadecimal characters “00”) to make the total data length a multiple of 8 bytes (16 hexadecimal characters)
        private String paddedData;

        // These next three fields are used only if the derivation type is 9.
        // #][H
        // - the height value used for EMV-Tree derivation
        // #IV
        // - the clear Initialization Vector used for EMV-Tree derivation
        // #Index
        // - the index value used for EMV-Tree derivation
        // #]>

        public Request() {
            // 1, Derivation Type
            derivationType = getField(1);
            // 2, Header,EMFK.E(IMK),MAC
            imkSmMac = getField(2);
            // 3, [Application PAN]
            pan = getField(3);
            // 4, Application PAN Sequence Number
            panSequenceNumber = getField(4);
            // 5, Diversification Data
            diversificationData = getField(5);
            // 6, MAC Length
            macLengthType = getField(6);
            switch (macLengthType) {
                case "1" : macLength = 8;  break; // 8 digital hex string
                case "2" : macLength = 12;  break; // 12 digital hex string
                case "3" : macLength = 16;  break; // 16 digital hex string
                default:
                    macLength = 0;
            }
            // 7, [Header,EMFK.E(Continuation-IV),MAC]
            continuationIV = getField(7);
            // #8, Padded Data
            paddedData = getField(8);
        }

        // getter and setter
        public String getDerivationType() {
            return derivationType;
        }

        public void setDerivationType(String derivationType) {
            this.derivationType = derivationType;
        }

        public String getImkSmMac() {
            return imkSmMac;
        }

        public void setImkSmMac(String imkSmMac) {
            this.imkSmMac = imkSmMac;
        }

        public String getPan() {
            return pan;
        }

        public void setPan(String pan) {
            this.pan = pan;
        }

        public String getPanSequenceNumber() {
            return panSequenceNumber;
        }

        public void setPanSequenceNumber(String panSequenceNumber) {
            this.panSequenceNumber = panSequenceNumber;
        }

        public String getDiversificationData() {
            return diversificationData;
        }

        public void setDiversificationData(String diversificationData) {
            this.diversificationData = diversificationData;
        }

        public String getMacLengthType() {
            return macLengthType;
        }

        public void setMacLengthType(String macLengthType) {
            this.macLengthType = macLengthType;
        }

        public int getMacLength() {
            return macLength;
        }

        public void setMacLength(int macLength) {
            this.macLength = macLength;
        }

        public String getContinuationIV() {
            return continuationIV;
        }

        public void setContinuationIV(String continuationIV) {
            this.continuationIV = continuationIV;
        }

        public String getPaddedData() {
            return paddedData;
        }

        public void setPaddedData(String paddedData) {
            this.paddedData = paddedData;
        }
    }

    class Response {
        private String commandIdentifier = "452";
        // 1, MAC Length, This field contains the value of field 6 in the command.
        String macLengthType;
        // 2, MAC or Header,EMFK.E(Continuation-IV),MAC
        String mac;
        // 3, KMAC Check Digits
        String sessionSmMacCheckDigits;
        // 4, Issuer Master Key Check Digits
        String imkSmMacCheckDigits;

        public String output() {
            StringBuilder response = new StringBuilder(commandIdentifier);
            // 1, MAC Length, This field contains the value of field 6 in the command.
            response.append("#" + macLengthType);
            // 2, MAC or Header,EMFK.E(Continuation-IV),MAC
            response.append("#" + mac.replaceAll("(.{" + 4 + "})", "$1 ").trim());
            // 3, KMAC Check Digits
            response.append("#" + sessionSmMacCheckDigits);
            // 4, Issuer Master Key Check Digits
            response.append("#" + imkSmMacCheckDigits);
            // #>
            response.append("#");

            return response.toString();
        }

        // getter and setter
        public String getCommandIdentifier() {
            return commandIdentifier;
        }

        public void setCommandIdentifier(String commandIdentifier) {
            this.commandIdentifier = commandIdentifier;
        }

        public String getMacLengthType() {
            return macLengthType;
        }

        public void setMacLengthType(String macLengthType) {
            this.macLengthType = macLengthType;
        }

        public String getMac() {
            return mac;
        }

        public void setMac(String mac) {
            this.mac = mac;
        }

        public String getSessionSmMacCheckDigits() {
            return sessionSmMacCheckDigits;
        }

        public void setSessionSmMacCheckDigits(String sessionSmMacCheckDigits) {
            this.sessionSmMacCheckDigits = sessionSmMacCheckDigits;
        }

        public String getImkSmMacCheckDigits() {
            return imkSmMacCheckDigits;
        }

        public void setImkSmMacCheckDigits(String imkSmMacCheckDigits) {
            this.imkSmMacCheckDigits = imkSmMacCheckDigits;
        }
    }
}
