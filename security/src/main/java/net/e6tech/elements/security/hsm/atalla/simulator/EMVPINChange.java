/*
 * Copyright 2020 Futeh Kao
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

import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.hsm.AnsiPinBlock;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * @author Shell Xu
 */
public class EMVPINChange extends Command {

    @Override
    protected String doProcess() throws CommandException {
        Request request = this.new Request();
        Response response = this.new Response();

        if ("0".equals(request.getDerivationType())) {
            // Common Core Definition PIN block - Specification Update Bulletin 46 derivation with ISO format 2 PIN block with mandatory padding
            return processCommonSession(request, response, false);
        } else if ("1".equals(request.getDerivationType())) {
            // Legacy VISA derivation technique with VISA PIN block
            return processDerivationType1(request, response);
        } else if ("8".equals(request.getDerivationType())) {
            // Common Core Definition PIN block - Specification Update Bulletin 46 derivation with ISO format 2 PIN block with mandatory padding
            return processCommonSession(request, response, true);
        }

        return "000100"; // only supports Derivation Type 1 for now
    }

    protected String processDerivationType1(Request request, Response response) throws CommandException {
        // == Using the UDK-A, the issuer creates a 16-hexadecimal digit PIN block as .
        // - a. Input bytes based on UDK-A
        // Create a 16-hexadecimal digit block of data by extracting the eight right-most digits of the card application's Unique DEA Key A (UDK-A) and zero-filling it on the left with '00 00 00 00'
        byte[] inputBytesBasedOnUDKA = new byte[8];
        Arrays.fill(inputBytesBasedOnUDKA, 0, 4, (byte) 0x00);
        try {
            String kpeDigits = CryptoUtil.calculateCheckDigits(simulator, request.getKpe(), 6);
            response.setKpeDigits(kpeDigits);
            byte[] iccMasterKeyBytes = CryptoUtil.derivativeICCMasterKeyBytes(simulator, new AKB(request.getKpe()), request.getPan(), request.getPanSequenceNumber());
            System.arraycopy(iccMasterKeyBytes, 4, inputBytesBasedOnUDKA, 4, 4);
        } catch (GeneralSecurityException e) {
            throw new CommandException(4, e);
        }
        // - b. Padded new pin
        // Create the second 16-hexadecimal digit block of data by taking the new PIN and adding a pad character of '0' followed by the length of the new PIN to the left of the PIN.
        // The length N represents the number of digits (in hexadecimal) for the PIN. N is expressed as one hexadecimal digit. Right-fill the remaining bytes with 'F's.
        byte[] inputPINBytes = new byte[8];
        try {
            byte[] newPINBlockByte = simulator.decrypt(new AKB(request.getKpe()), Hex.toBytes(request.getNewPINBlock()));
            AnsiPinBlock ansiPinBlock = new AnsiPinBlock(newPINBlockByte, request.getPinBlockBlockData());
            String newPIN = ansiPinBlock.getPIN();
            inputPINBytes[0] = (byte) newPIN.length();
            byte[] newPINBytes = Hex.toBytes(newPIN);
            System.arraycopy(newPINBytes, 0, inputPINBytes, 1, newPINBytes.length);
            Arrays.fill(inputPINBytes, 1 + newPINBytes.length, inputPINBytes.length, (byte) 0xFF);
        } catch (GeneralSecurityException e) {
            throw new CommandException(8, e);
        }

        // - c, a exclusive-OR b
        byte[] plainPINBytes = new byte[8];
        for (int i = 0; i < plainPINBytes.length; i++) {
            plainPINBytes[i] = (byte) (inputBytesBasedOnUDKA[i] ^ inputPINBytes[i]);
        }

        // if old pin comes, right padded with 0s then exclusive-OR c
        if (!StringUtil.isNullOrEmpty(request.getOldPINBlockBlockData())) {
            try {
                byte[] oldPINBlockBytes = simulator.decrypt(new AKB(request.getKpe()), Hex.toBytes(request.getOldPINBlockBlockData()));
                AnsiPinBlock ansiPinBlock = new AnsiPinBlock(oldPINBlockBytes, request.getPinBlockBlockData());
                String oldPIN = ansiPinBlock.getPIN();
                String paddedOldPIN = StringUtil.padRight(oldPIN, 16, '0');
                byte[] paddedOldPINBytes = Hex.toBytes(paddedOldPIN);
                for (int i = 0; i < plainPINBytes.length; i++) {
                    plainPINBytes[i] = (byte) (paddedOldPINBytes[i] ^ inputPINBytes[i]);
                }
            } catch (GeneralSecurityException e) {
                throw new CommandException(15, e);
            }
        }

        // == Add Length of plaintext data (LD = '08') and Padding ('80 00 00 00 00 00 00'), then divide into 8-byte data blocks D1 and D2
        byte[] plainPaddedPINBytesD1 = new byte[8];
        byte[] plainPaddedPINBytesD2 = new byte[8];
        plainPaddedPINBytesD1[0] = (byte) 0x08;
        System.arraycopy(plainPINBytes, 0, plainPaddedPINBytesD1, 1, 7);
        plainPaddedPINBytesD2[0] = plainPINBytes[7];
        plainPaddedPINBytesD2[1] = (byte) 0x80;
        Arrays.fill(plainPaddedPINBytesD2, 2, 8, (byte) 0x00);

        // == Encipher the data blocks D1 and D2 to Enciphered D1 and Enciphered D2
        byte[] encipheredPaddedPINBytesD1;
        byte[] encipheredPaddedPINBytesD2;
        byte[] sessionKeySmEncKeyBytes = deriveSessionKeySmEncKeyBytes(request, response);
        try {
            encipheredPaddedPINBytesD1 = CryptoUtil.encrypt(sessionKeySmEncKeyBytes, plainPaddedPINBytesD1);
            encipheredPaddedPINBytesD2 = CryptoUtil.encrypt(sessionKeySmEncKeyBytes, plainPaddedPINBytesD2);
        } catch (GeneralSecurityException e) {
            throw new CommandException(12, e);
        }
        // == Concatenate enciphered data blocks
        byte[] encipheredPaddedPINBytes = Hex.concat(encipheredPaddedPINBytesD1, encipheredPaddedPINBytesD2);
        response.setEncipheredPaddedPINBytes(encipheredPaddedPINBytes);

        // == Add MAC
        addMac(request, response, encipheredPaddedPINBytes);

        // ImkACCheckDigits
        calculateImkACCheckDigits(request, response);

        // == set response
        response.setSanityCheck("Y");

        return response.output();
    }

    protected String processCommonSession(Request request, Response response, boolean withMandatoryPadding) throws CommandException {
        // == KPE check digits
        try {
            String kpeDigits = CryptoUtil.calculateCheckDigits(simulator, request.getKpe(), 6);
            response.setKpeDigits(kpeDigits);
        } catch (GeneralSecurityException e) {
            throw new CommandException(4, e);
        }
        // == Create padded new pin. ISO format 2 PIN block with mandatory padding
        // PIN Block format (nibble)
        //   C N P P P P P/F P/F P/F P/F P/F P/F P/F P/F F F
        // - C: 2
        // - N: Pin length
        // - P: Pin Digit
        // - F: 'F'
        // If with mandatory padding, pad with hexadecimal digits 80 followed by 14 zero
        byte[] paddedPINBytes = new byte[withMandatoryPadding ? 16 : 8];
        try {
            byte[] newPINBlockByte = simulator.decrypt(new AKB(request.getKpe()), Hex.toBytes(request.getNewPINBlock()));
            AnsiPinBlock ansiPinBlock = new AnsiPinBlock(newPINBlockByte, request.getPinBlockBlockData());
            String newPIN = ansiPinBlock.getPIN();
            paddedPINBytes[0] = (byte) (0x20 + newPIN.length());
            if (newPIN.length() % 2 == 1)
                newPIN = newPIN + "F";
            byte[] newPINBytes = Hex.toBytes(newPIN);
            System.arraycopy(newPINBytes, 0, paddedPINBytes, 1, newPINBytes.length);
            Arrays.fill(paddedPINBytes, 1 + newPINBytes.length, 8, (byte) 0xFF);
            if (withMandatoryPadding)
                paddedPINBytes[8] = (byte)0x80;
            // the rest are already 0x00
        } catch (GeneralSecurityException e) {
            throw new CommandException(8, e);
        }

        // == Encipher the data blocks
        byte[] encipheredPaddedPINBytes;
        byte[] sessionKeySmEncKeyBytes = deriveSessionKeySmEncKeyBytes(request, response);
        try {
            encipheredPaddedPINBytes = CryptoUtil.encrypt(sessionKeySmEncKeyBytes, paddedPINBytes);
            response.setEncipheredPaddedPINBytes(encipheredPaddedPINBytes);
        } catch (GeneralSecurityException e) {
            throw new CommandException(12, e);
        }

        // == Add MAC
        addMac(request, response, encipheredPaddedPINBytes);

        // ImkACCheckDigits
        calculateImkACCheckDigits(request, response);

        // == set response
        response.setSanityCheck("Y");

        return response.output();
    }

    private byte[] deriveSessionKeySmEncKeyBytes(Request request, Response response) throws CommandException {
        byte[] sessionKeySmEncKeyBytes;
        byte[] iccMasterKeySmEncKeys;
        try {
            String imkSmEncKeyCheckDigits = CryptoUtil.calculateCheckDigits(simulator, request.getImkSmEnc(), 6);
            response.setImkSmEncKeyCheckDigits(imkSmEncKeyCheckDigits);
            iccMasterKeySmEncKeys = CryptoUtil.derivativeICCMasterKeyBytes(simulator, new AKB(request.getImkSmEnc()), request.getPan(), request.getPanSequenceNumber());
        } catch (GeneralSecurityException e) {
            throw new CommandException(5, e);
        }
        try {
            sessionKeySmEncKeyBytes = CryptoUtil.deriveSessionKeyBytes(iccMasterKeySmEncKeys, request.getDiversificationData());
            String sessionKeySmEncKeyCheckDigits = CryptoUtil.calculateKeyBytesCheckDigits(sessionKeySmEncKeyBytes, 6);
            response.setSessionKeySmEncKeyCheckDigits(sessionKeySmEncKeyCheckDigits);
        } catch (GeneralSecurityException e) {
            throw new CommandException(12, e);
        }
        return sessionKeySmEncKeyBytes;
    }

    protected void addMac(Request request, Response response, byte[] encipheredPaddedPINBytes) throws CommandException {
        byte[] dataToCalculateMac = Hex.toBytes(request.getApplicationData() + Hex.toString(encipheredPaddedPINBytes));
        byte[] paddedDataToCalculateMac = padDatablock(dataToCalculateMac);
        byte[] iccMasterSmMacBytes;
        byte[] sessionSmMacBytes;
        try {
            String imkSmMacCheckDigits = CryptoUtil.calculateCheckDigits(simulator, request.getImkSmMac(), 6);
            response.setImkSmMacCheckDigits(imkSmMacCheckDigits);
            iccMasterSmMacBytes = CryptoUtil.derivativeICCMasterKeyBytes(simulator, new AKB(request.getImkSmMac()), request.getPan(), request.getPanSequenceNumber());
        } catch (GeneralSecurityException e) {
            throw new CommandException(6, e);
        }
        try {
            sessionSmMacBytes = CryptoUtil.deriveSessionKeyBytes(iccMasterSmMacBytes, request.getDiversificationData());
            SecretKey sessionSmMac = CryptoUtil.createSecretKey(sessionSmMacBytes);
            String sessionSmMacCheckDigits = CryptoUtil.calculateCheckDigits(sessionSmMac, 6);
            response.setSessionSmMacCheckDigits(sessionSmMacCheckDigits);
            String mac = CryptoUtil.iso9797Alg3Mac(sessionSmMacBytes, paddedDataToCalculateMac, 16);
            response.setMac(mac);
        } catch (GeneralSecurityException e) {
            throw new CommandException(12, e);
        }
    }

    public static byte[] padDatablock(byte[] data) {
        int paddedEndCharCountExcept80 = (8 - (data.length + 1) % 8) % 8;
        byte[] result = new byte[data.length + 1 + paddedEndCharCountExcept80];
        System.arraycopy(data, 0, result, 0, data.length);
        result[data.length] = (byte)0x80;
        Arrays.fill(result, data.length + 1, result.length, (byte)0x00);
        return result;
    }

    protected void calculateImkACCheckDigits(Request request, Response response) throws CommandException {
        if (StringUtil.isNullOrEmpty(request.getImkAC()))
            response.setImkACCheckDigits("");
        else {
            try {
                String imkACCheckDigits = CryptoUtil.calculateCheckDigits(simulator, request.getImkAC(), 6);
                response.setImkACCheckDigits(imkACCheckDigits);
            } catch (GeneralSecurityException e) {
                throw new CommandException(7, e);
            }
        }
    }

    class Request {
        // 1, Derivation Type
        // - 0, Common Session (EMV 4.1 and Specification Update Bulletin 46) derivation with ISO format 2 PIN block, Europay/MasterCard MChip4 version 1.1
        // - 1, Visa derivation with Visa PIN block, Visa CVN 10 and CVN 18.
        // - 2, Visa legacy derivation withVisa8 PIN block
        // - 3, EMV2000-Tree with IS0 format 2 PIN block
        // - 4, EMV2000-Tree with Visa PIN block
        // - 5, EMV2000-Tree with Visa8 PIN block
        // - 6, Proprietary - similar to UnionPay International except exclusive Or Session Key Left with PIN block
        // - 7, UnionPay International
        // - 8, Common Core Definition PIN block - Specification Update Bulletin 46 derivation with ISO format 2 PIN block with mandatory padding, JCB CVN 04, and Visa CVN 22
        private String derivationType;
        // 2, Incoming PIN Block type
        // - 0, ISO format 1
        // - 1, ANSI
        // - L, Lloyds
        //    PIN pads for VisaNet transactions must use ANSI format 0 to create the PIN block before
        //    encryption. American National Standards Institute (ANSI) PIN block format 0 (zero) and Visa PIN block format 1 are identical.
        private String incomingPINBlockType;
        // 3, [Reserved]
        // 4, Header,EMFK.E(KPE),MAC
        private String kpe;
        // 5, Header,EMFK.E(IMKENC),MAC
        private String imkSmEnc;
        // 6, Header,EMFK.E(IMKMAC),MAC
        private String imkSmMac;
        // 7, [Header,EMFK.E(IMKAC),MAC]
        // - the Issuer Master Key for Application Cryptogram generation (IMKAC) encrypted under the MFK
        // - When field 1 contains the number 0,3, or 8 this field must be empty. When field 1 contains a value of either 1 or 2 this field must contain
        // - The IMKAC must be 2key-3DES (double-length), if present. The header of IMKAC must be 1mENE000 or 1mENN000.
        private String imkAC;
        // 8, EKPE(new PIN Block)
        private String newPINBlock;
        // 9, [PIN Issue Number]
        // - This field is optional. If field 2 contains the value 'L' this field will contain a decimal value between 000-255. Otherwise this field should be empty
        private String pinIssueNumber;
        // 10, Application PAN
        // - the Primary Account Number for the application. This field is also used to indicate the Master Key derivation method.
        //  If this field contains the letter "B" followed by 17 to 19 digits, method B will be used, otherwise method A will be used.
        private String pan;
        // 11, PAN Sequence Number
        String panSequenceNumber;
        // 12, Diversification Data
        // - the value of this field depends on the derivation type specified in field 1.
        // - For the common session derivation algorithm (if the derivation type, Field 1, is 0 or 8) this field contains a 16 byte hexadecimal value as defined in EMV SU-46.
        // - For all other derivation types, this field contains the four hexadecimal characters (2 bytes) of the Application Transaction Counter.
        private String diversificationData;
        // 13, [Application data]
        // - the APP Data field may contain the 5-byte EMV command message header (CLA, INS, P1, P2, and Lc) followed by other optional items such as the Application Transaction Counter (ATC), or the Application Cryptogram (ARQC).
        private String applicationData;
        // 14, [PIN Block Data]
        // - Its contents depend on the PIN block type. If field 2 contains '0' or 'L' this field must be empty; otherwise it must contain the 12-digit PAN used to create the ANSI PIN block.
        private String pinBlockBlockData;
        // 15, [EKPE(old PIN Block)
        private String oldPINBlockBlockData;
        // These next three fields are used only if the derivation type is 8 or 9.
        // 16, H
        // - the height value used for EMV-Tree derivation
        // 17, IV
        // - the clear Initialization Vector used for EMV-Tree derivation
        // 18, Index
        // - the index value used for EMV-Tree derivation

        public Request() {
            // 1, Derivation Type
            derivationType = getField(1);
            // 2, Incoming PIN Block type
            incomingPINBlockType = getField(2);
            // 3, [Reserved]
            // 4, Header,EMFK.E(KPE),MAC
            kpe = getField(4);
            // 5, Header,EMFK.E(IMKENC),MAC
            imkSmEnc = getField(5);
            // 6, Header,EMFK.E(IMKMAC),MAC
            imkSmMac = getField(6);
            // 7, [Header,EMFK.E(IMKAC),MAC]
            imkAC = getField(7);
            // 8, EKPE(new PIN Block)
            newPINBlock = getField(8);
            // 9, [PIN Issue Number]
            pinIssueNumber = getField(9);
            // 10, Application PAN
            pan = getField(10);
            // 11, PAN Sequence Number
            panSequenceNumber = getField(11);
            // 12, Diversification Data
            diversificationData = getField(12);
            // 13, [Application data]
            applicationData = getField(13);
            // 14, [PIN Block Data]
            pinBlockBlockData = getField(14);
            // 15, [EKPE(old PIN Block)
            if (fields.length > 15)
                oldPINBlockBlockData = getField(15);
        }

        // getter and setter
        public String getDerivationType() {
            return derivationType;
        }

        public void setDerivationType(String derivationType) {
            this.derivationType = derivationType;
        }

        public String getIncomingPINBlockType() {
            return incomingPINBlockType;
        }

        public void setIncomingPINBlockType(String incomingPINBlockType) {
            this.incomingPINBlockType = incomingPINBlockType;
        }

        public String getKpe() {
            return kpe;
        }

        public void setKpe(String kpe) {
            this.kpe = kpe;
        }

        public String getImkSmEnc() {
            return imkSmEnc;
        }

        public void setImkSmEnc(String imkSmEnc) {
            this.imkSmEnc = imkSmEnc;
        }

        public String getImkSmMac() {
            return imkSmMac;
        }

        public void setImkSmMac(String imkSmMac) {
            this.imkSmMac = imkSmMac;
        }

        public String getImkAC() {
            return imkAC;
        }

        public void setImkAC(String imkAC) {
            this.imkAC = imkAC;
        }

        public String getNewPINBlock() {
            return newPINBlock;
        }

        public void setNewPINBlock(String newPINBlock) {
            this.newPINBlock = newPINBlock;
        }

        public String getPinIssueNumber() {
            return pinIssueNumber;
        }

        public void setPinIssueNumber(String pinIssueNumber) {
            this.pinIssueNumber = pinIssueNumber;
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

        public String getApplicationData() {
            return applicationData;
        }

        public void setApplicationData(String applicationData) {
            this.applicationData = applicationData;
        }

        public String getPinBlockBlockData() {
            return pinBlockBlockData;
        }

        public void setPinBlockBlockData(String pinBlockBlockData) {
            this.pinBlockBlockData = pinBlockBlockData;
        }

        public String getOldPINBlockBlockData() {
            return oldPINBlockBlockData;
        }

        public void setOldPINBlockBlockData(String oldPINBlockBlockData) {
            this.oldPINBlockBlockData = oldPINBlockBlockData;
        }
    }

    class Response {
        private String commandIdentifier = "451";
        // 1, Sanity Check
        private String sanityCheck;
        // 2, Encrypted PIN block
        byte[] encipheredPaddedPINBytes;
        // 3, MAC
        String mac;
        // 4, KPE Check Digits
        String kpeDigits;
        // 5, IMKENC Check Digits
        String imkSmEncKeyCheckDigits;
        // 6, IMKMAC Check Digits
        String imkSmMacCheckDigits;
        // 7, [IMKAC Check Digits]
        String imkACCheckDigits;
        // 8, [SKENC Check Digits]
        String sessionKeySmEncKeyCheckDigits;
        // 9, [SKMAC Check Digits]
        String sessionSmMacCheckDigits;

        public String output() {
            StringBuilder response = new StringBuilder(commandIdentifier);
            // 1, Sanity Check
            response.append("#" + sanityCheck)
                    // 2, Encrypted PIN block
                    .append("#").append(Hex.toString(encipheredPaddedPINBytes))
                    // 3, MAC
                    .append("#").append(mac)
                    // 4, KPE Check Digits
                    .append("#" + kpeDigits)
                    // 5, IMKENC Check Digits
                    .append("#" + imkSmEncKeyCheckDigits)
                    // 6, IMKMAC Check Digits
                    .append("#" + imkSmMacCheckDigits)
                    // 7, [IMKAC Check Digits]
                    .append("#" + imkACCheckDigits)
                    // 8, [SKENC Check Digits]
                    .append("#" + sessionKeySmEncKeyCheckDigits)
                    // 9, [SKMAC Check Digits]
                    .append("#" + sessionSmMacCheckDigits)
                    // #>
                    .append("#");

            return response.toString();
        }

        // getter and setter
        public void setCommandIdentifier(String commandIdentifier) {
            this.commandIdentifier = commandIdentifier;
        }

        public void setSanityCheck(String sanityCheck) {
            this.sanityCheck = sanityCheck;
        }

        public void setEncipheredPaddedPINBytes(byte[] encipheredPaddedPINBytes) {
            this.encipheredPaddedPINBytes = encipheredPaddedPINBytes;
        }

        public void setMac(String mac) {
            this.mac = mac;
        }

        public void setKpeDigits(String kpeDigits) {
            this.kpeDigits = kpeDigits;
        }

        public void setImkSmEncKeyCheckDigits(String imkSmEncKeyCheckDigits) {
            this.imkSmEncKeyCheckDigits = imkSmEncKeyCheckDigits;
        }

        public void setImkSmMacCheckDigits(String imkSmMacCheckDigits) {
            this.imkSmMacCheckDigits = imkSmMacCheckDigits;
        }

        public void setImkACCheckDigits(String imkACCheckDigits) {
            this.imkACCheckDigits = imkACCheckDigits;
        }

        public void setSessionKeySmEncKeyCheckDigits(String sessionKeySmEncKeyCheckDigits) {
            this.sessionKeySmEncKeyCheckDigits = sessionKeySmEncKeyCheckDigits;
        }

        public void setSessionSmMacCheckDigits(String sessionSmMacCheckDigits) {
            this.sessionSmMacCheckDigits = sessionSmMacCheckDigits;
        }
    }
}