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

package net.e6tech.elements.security.vault;

import net.e6tech.elements.common.util.SystemException;

import java.util.Arrays;

/**
 * Created by futeh.
 */
public class VaultManagerState implements Cloneable {

    private char[] password;
    private ClearText signature;

    @SuppressWarnings("squid:S2975")
    public VaultManagerState clone() {
        VaultManagerState state = null;
        try {
            state = (VaultManagerState) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new SystemException(e);
        }

        if (password != null)
            state.password = Arrays.copyOf(password, password.length);
        return state;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public ClearText getSignature() {
        return signature;
    }

    public void setSignature(ClearText signature) {
        this.signature = signature;
    }
}
