/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.security.hsm.thales;

public class ChangePIN_IBM extends Command {
    private String keyType = "001";  // ZPK
    private String pinBlockKey;
    private String pvk;
    private String currentPinBlock;
    private String pinBlockFormat = "01";
    private String checkLength = "04";  // minimum pin length
    private String partialPan;          // 12 N
    private String decimalization;      // 16 H (encrypted) or 16 N (plain)
    private String validation;          // 12 N should be same as partialPan for ANSI PIN block
    private String currentOffset;       // 12 H left justified padded with 'F'
    private String newPinBlock;

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getPinBlockKey() {
        return pinBlockKey;
    }

    public void setPinBlockKey(String pinBlockKey) {
        this.pinBlockKey = pinBlockKey;
    }

    public String getPvk() {
        return pvk;
    }

    public void setPvk(String pvk) {
        this.pvk = pvk;
    }

    public String getCurrentPinBlock() {
        return currentPinBlock;
    }

    public void setCurrentPinBlock(String currentPinBlock) {
        this.currentPinBlock = currentPinBlock;
    }

    public String getPinBlockFormat() {
        return pinBlockFormat;
    }

    public void setPinBlockFormat(String pinBlockFormat) {
        this.pinBlockFormat = pinBlockFormat;
    }

    public String getCheckLength() {
        return checkLength;
    }

    public void setCheckLength(String checkLength) {
        this.checkLength = checkLength;
    }

    public String getPartialPan() {
        return partialPan;
    }

    public void setPartialPan(String partialPan) {
        this.partialPan = partialPan;
    }

    public String getDecimalization() {
        return decimalization;
    }

    public void setDecimalization(String decimalization) {
        this.decimalization = decimalization;
    }

    public String getValidation() {
        return validation;
    }

    public void setValidation(String validation) {
        this.validation = validation;
    }

    public String getCurrentOffset() {
        return currentOffset;
    }

    public void setCurrentOffset(String currentOffset) {
        this.currentOffset = currentOffset;
    }

    public String getNewPinBlock() {
        return newPinBlock;
    }

    public void setNewPinBlock(String newPinBlock) {
        this.newPinBlock = newPinBlock;
    }


    public ChangePIN_IBM keyType(String keyType) {
        this.keyType = keyType;
        return this;
    }

    public ChangePIN_IBM pinBlockKey(String pinBlockKey) {
        this.pinBlockKey = pinBlockKey;
        return this;
    }

    public ChangePIN_IBM pvk(String pvk) {
        this.pvk = pvk;
        return this;
    }

    public ChangePIN_IBM currentPinBlock(String currentPinBlock) {
        this.currentPinBlock = currentPinBlock;
        return this;
    }

    public ChangePIN_IBM pinBlockFormat(String pinBlockFormat) {
        this.pinBlockFormat = pinBlockFormat;
        return this;
    }

    public ChangePIN_IBM checkLength(String checkLength) {
        this.checkLength = checkLength;
        return this;
    }

    public ChangePIN_IBM partialPan(String partialPan) {
        this.partialPan = partialPan;
        return this;
    }

    public ChangePIN_IBM decimalization(String decimalization) {
        this.decimalization = decimalization;
        return this;
    }

    public ChangePIN_IBM validation(String validation) {
        this.validation = validation;
        return this;
    }

    public ChangePIN_IBM currentOffset(String currentOffset) {
        this.currentOffset = currentOffset;
        return this;
    }

    public ChangePIN_IBM newPinBlock(String newPinBlock) {
        this.newPinBlock = newPinBlock;
        return this;
    }

    @Override
    protected void packFields() {
        pack(keyType, pinBlockKey, pvk, currentPinBlock, pinBlockFormat, checkLength, partialPan, decimalization, validation, currentOffset,
                newPinBlock);
    }
}
