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

public class GenerateCVV extends Command {
    private String cvk;  //  32H or 1A + 32H
    private String accountNumber; //
    private String expiry; // 4N yymm
    private String serviceCode; // 3N

    public String getCvk() {
        return cvk;
    }

    public void setCvk(String cvk) {
        this.cvk = cvk;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public GenerateCVV cvk(String cvk) {
        this.cvk = cvk;
        return this;
    }

    public GenerateCVV accountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    public GenerateCVV expiry(String expiry) {
        this.expiry = expiry;
        return this;
    }

    public GenerateCVV serviceCode(String serviceCode) {
        this.serviceCode = serviceCode;
        return this;
    }

    @Override
    protected void packFields() {
        pack(cvk, accountNumber, ";", expiry, serviceCode);
    }

    @Override
    protected void unpackFields() {
        cvk = unpackKey();
        accountNumber = unpackDelimited(';');
        expiry = unpackString(4);
        serviceCode = unpackString(3);
    }
}
