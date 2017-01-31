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
package net.e6tech.elements.web.security.vault;

import net.e6tech.elements.security.vault.ClearText;
import net.e6tech.elements.security.vault.Credential;
import net.e6tech.elements.web.security.vault.client.KeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

/**
 * Created by futeh.
 */
public class KeyClientTest {

    KeyClient client;

    @BeforeEach
    public void setup() throws GeneralSecurityException {
        client = new KeyClient();
        Credential se = new Credential("user1", "password1".toCharArray());
        client.setCredential(se);
        client.start();
    }

    @Test
    public void testGetSecret() throws Exception {
        ClearText ct = client.getSecret("h3-db");
        System.out.println(new String(ct.getBytes(), "UTF-8"));
    }

}
