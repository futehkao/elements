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
package net.e6tech.elements.web.security.vault;

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.security.SymmetricCipher;
import net.e6tech.elements.security.vault.*;
import net.e6tech.elements.web.cxf.*;
import net.e6tech.elements.web.security.vault.client.KeyClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by futeh.
 */
public class KeyClientTest {

    static volatile KeyClient client;
    static JaxRSServer server;
    static Credential user1 = new Credential("user1", "1234567890123456789012345678901234567890".toCharArray());
    static String appKey;

    static void startKeyServer() throws Exception {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);
        Resources res = rm.open(null);
        VaultManager manager = new VaultManager();
        DualEntry dualEntry = new DualEntry(user1.getUser(), user1.getPassword(), "user2", "password2".toCharArray());
        String tmpVaultFilename = System.getProperty("java.io.tmpdir") + "/test-" + System.currentTimeMillis() + ".vault";

        FileStore keyDataStore = new FileStore();
        keyDataStore.setFileName(tmpVaultFilename);
        FileStore userLocalStore = new FileStore();
        tmpVaultFilename = System.getProperty("java.io.tmpdir") + "/test-" +(System.currentTimeMillis() + 1) + ".vault";
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

        res.close();
    }

    static void startKeyClient() {
        client = new KeyClient();
        client.setAddress("http://localhost:1111/restful/keyserver/v1");
        client.setCredential(user1);
        client.start();
    }

    @Test
    void basic() throws Exception {
        startKeyServer();
        startKeyClient();
        ClearText ct = client.getSecret("secret");
        assertEquals("test", ct.getProperty("test"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, ct.getBytes());

        ct = client.passwordUnlock("db");
        assertEquals("user", ct.getProperty("username"));
        assertEquals("db-password", new String(ct.getBytes(), StandardCharsets.UTF_8));

        String iv = SymmetricCipher.getInstance("AES").generateIV();
        String encrypted = client.encrypt(appKey, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, iv);

        byte[] bytes = client.decrypt(appKey, encrypted, iv);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, bytes);
    }

    @Test
    void delayStart() throws Exception {
        Thread thread = new Thread(() -> {
            while (client == null) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                startKeyServer();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        startKeyClient();

        ClearText ct = client.getSecret("secret");
        assertEquals("test", ct.getProperty("test"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, ct.getBytes());

    }

}
