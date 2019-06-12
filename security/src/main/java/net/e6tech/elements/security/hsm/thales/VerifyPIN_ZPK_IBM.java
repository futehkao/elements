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

import net.e6tech.elements.common.util.StringUtil;

@SuppressWarnings("squid:S00101")
public class VerifyPIN_ZPK_IBM extends Command {
    private String zpk;                     // encrypted by LMK 06-07, this is the KPE
    private String pvk;                     // encrypted by LMK 14-15, equivalent to KPV
    private String maxPinLength = "12";
    private String pinBlock;                // encrypted by zpk
    private String pinBlockFormat = "01";   // 2 N
    private String minPinLength = "04";     // 2 N
    private String partialPan;              // 12 N  right most digits excluding the check digits.
    private String decimalization;          // 16 H (encrypted) or 16 N (plain)
    private String validation;              // 12 A, same as partialPan if pinBlockFormat i s '01'
    private String offset;                  // 12 H left justified and padded with 'F'

    public String getZpk() {
        return zpk;
    }

    public void setZpk(String zpk) {
        this.zpk = zpk;
    }

    public String getPvk() {
        return pvk;
    }

    public void setPvk(String pvk) {
        this.pvk = pvk;
    }

    public String getMaxPinLength() {
        return maxPinLength;
    }

    public void setMaxPinLength(String maxPinLength) {
        this.maxPinLength = maxPinLength;
    }

    public String getPinBlock() {
        return pinBlock;
    }

    public void setPinBlock(String pinBlock) {
        this.pinBlock = pinBlock;
    }

    public String getPinBlockFormat() {
        return pinBlockFormat;
    }

    public void setPinBlockFormat(String pinBlockFormat) {
        this.pinBlockFormat = pinBlockFormat;
    }

    public String getMinPinLength() {
        return minPinLength;
    }

    public void setMinPinLength(String minPinLength) {
        this.minPinLength = minPinLength;
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

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = StringUtil.padRight(offset, 12, 'F');
    }

    public VerifyPIN_ZPK_IBM zpk(String zpk) {
        this.zpk = zpk;
        return this;
    }

    public VerifyPIN_ZPK_IBM pvk(String pvk) {
        this.pvk = pvk;
        return this;
    }

    public VerifyPIN_ZPK_IBM maxPinLength(String maxPinLength) {
        this.maxPinLength = maxPinLength;
        return this;
    }

    public VerifyPIN_ZPK_IBM pinBlock(String pinBlock) {
        this.pinBlock = pinBlock;
        return this;
    }

    public VerifyPIN_ZPK_IBM pinBlockFormat(String pinBlockFormat) {
        this.pinBlockFormat = pinBlockFormat;
        return this;
    }

    public VerifyPIN_ZPK_IBM minPinLength(String minPinLength) {
        this.minPinLength = minPinLength;
        return this;
    }

    public VerifyPIN_ZPK_IBM partialPan(String partialPan) {
        this.partialPan = partialPan;
        return this;
    }

    public VerifyPIN_ZPK_IBM decimalization(String decimalization) {
        this.decimalization = decimalization;
        return this;
    }

    public VerifyPIN_ZPK_IBM validation(String validation) {
        this.validation = validation;
        return this;
    }

    public VerifyPIN_ZPK_IBM offset(String offset) {
        this.offset = offset;
        return this;
    }

    @Override
    protected void packFields() {
        pack(zpk, pvk, maxPinLength, pinBlock, pinBlockFormat, minPinLength, partialPan, decimalization, validation, offset);
    }

}
