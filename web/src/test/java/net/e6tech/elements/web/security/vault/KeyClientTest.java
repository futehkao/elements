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

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.security.SymmetricCipher;
import net.e6tech.elements.security.vault.*;
import net.e6tech.elements.web.cxf.JaxRSServer;
import net.e6tech.elements.web.security.vault.client.KeyClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class KeyClientTest {

    static KeyClient client;
    static JaxRSServer server;
    static Credential user1 = new Credential("user1", "1234567890123456789012345678901234567890".toCharArray());
    static String appKey;

    @BeforeAll
    static void startKeyServer() throws Exception {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);
        Resources res = rm.open(null);
        VaultManager manager = new VaultManager();
        DualEntry dualEntry = new DualEntry(user1.getUser(), user1.getPassword(), "user2", "password2".toCharArray());
        String tmpVaultFilename = "/tmp/test-" + System.currentTimeMillis() + ".vault";

        FileStore keyDataStore = new FileStore();
        keyDataStore.setFileName(tmpVaultFilename);
        FileStore userLocalStore = new FileStore();
        tmpVaultFilename = "/tmp/test-" +(System.currentTimeMillis() + 1) + ".vault";
        userLocalStore.setFileName(tmpVaultFilename);

        manager.setKeyDataStore(keyDataStore);
        manager.setUserLocalStore(userLocalStore);

        userLocalStore.manage(VaultManager.USER_VAULT, VaultManager.LOCAL_VAULT);
        keyDataStore.manage(VaultManager.KEY_VAULT, VaultManager.DATA_VAULT);

        manager.open(dualEntry);
        res.bind(VaultManager.class, manager);

        // set up a passphrase lock item
        ClearText ct = new ClearText();
        ct.setBytes("db-password".getBytes(StandardCharsets.UTF_8));
        ct.setProperty("username", "user");
        manager.passphraseLock(dualEntry, "db", ct);
       // manager.getUserLocalStore().save();

        // set up a secret
        ClearText clearText = new ClearText();
        clearText.setBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        clearText.setProperty("test", "test");
        manager.addSecretData(dualEntry, "secret", clearText);

        // set up a key
        appKey = manager.generateKey(dualEntry);

        server = res.newInstance(JaxRSServer.class);
        List<Map<String, Object>> resources = new ArrayList<>();
        Map<String, Object> resource = new HashMap<>();
        resource.put("class", "net.e6tech.elements.web.security.vault.KeyServer");
        resource.put("name", "KeyServer");
        resource.put("singleton", "true");
        resources.add(resource);
        server.setResources(resources);
        List<String> addresses = new ArrayList<>();
        addresses.add("http://0.0.0.0:1111/restful/");
        server.setAddresses(addresses);
        server.initialize(res);
        server.start();

        client = new KeyClient();
        client.setAddress("http://localhost:1111/restful/keyserver/v1");
        client.setCredential(user1);
        client.start();

        res.close();
    }

    @Test
    public void basic() throws Exception {
        ClearText ct = client.getSecret("secret");
        assertTrue(ct.getProperty("test").equals("test"));
        assertTrue(Arrays.equals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, ct.getBytes()));

        ct = client.passwordUnlock("db");
        assertTrue(ct.getProperty("username").equals("user"));
        assertTrue(new String(ct.getBytes(), StandardCharsets.UTF_8).equals("db-password"));

        String iv = SymmetricCipher.getInstance("AES").generateIV();
        String encrypted = client.encrypt(appKey, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, iv);

        byte[] bytes = client.decrypt(appKey, encrypted, iv);
        assertTrue(Arrays.equals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, bytes));
    }

}
