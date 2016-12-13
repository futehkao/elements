/*
Copyright 2015 Futeh Kao

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

/**
 * Created by futeh.
 */
public class Decrypt extends Action {
    String keyBlock;  // format is <iv>$<encrypted key>
    String secret;    // secret needs to be in <iv>$<encrypted data> format if keyBlock is null.
                      // When keyBlock is null, it means use the master key to decrypt.
    String iv;

    public String getKeyBlock() {
        return keyBlock;
    }

    public void setKeyBlock(String keyBlock) {
        this.keyBlock = keyBlock;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }
}
