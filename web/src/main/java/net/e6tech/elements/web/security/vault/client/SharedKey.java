/*
Copyright 2015-2019 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.web.security.vault.client;

import java.math.BigInteger;

/**
 * Created by futeh.
 */
public class SharedKey {

    BigInteger modulus;
    BigInteger publicExponent;

    public BigInteger getModulus() {
        return this.modulus;
    }

    public void setModulus(BigInteger mod) {
        this.modulus = mod;
    }

    public BigInteger getPublicExponent() {
        return this.publicExponent;
    }

    public void setPublicExponent(BigInteger exp) {
        this.publicExponent = exp;
    }
}
