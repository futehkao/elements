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

import net.e6tech.elements.security.AsymmetricCipher;
import net.e6tech.elements.security.SymmetricCipher;
import net.e6tech.elements.security.vault.ClearText;
import net.e6tech.elements.security.vault.DualEntry;
import net.e6tech.elements.security.vault.FileStore;
import net.e6tech.elements.security.vault.VaultManager;
import net.e6tech.elements.web.security.vault.client.*;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh.
 */
public class KeyServerTest {

    KeyServer keyServer = new KeyServer();
    AsymmetricCipher asym = AsymmetricCipher.getInstance("RSA");
    SymmetricCipher sym = SymmetricCipher.getInstance("AES");
    PublicKey publicKey;
    String clientKey;
    SecretKey secretKey;
    String token;

    @Before
    public void setup() throws Exception {
        VaultManager manager = new VaultManager();
        DualEntry dualEntry = new DualEntry("user1", "password1".toCharArray(), "user2", "password2".toCharArray());
        String tmpVaultFilename = "/tmp/test-" + System.currentTimeMillis() + ".vault";
        ((FileStore) manager.getUserLocalStore()).setFileName(tmpVaultFilename);
        manager.open(dualEntry);

        keyServer.vaultManager = manager;
        String pubKey = keyServer.getPublicKey();
        SharedKey sharedKey = mapper.readValue(pubKey, SharedKey.class);
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(sharedKey.getModulus(), sharedKey.getPublicExponent());
        publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
        secretKey = sym.generateKeySpec();
        clientKey = asym.encrypt(publicKey, secretKey.getEncoded());

        ClearText clearText = new ClearText();
        clearText.setBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        clearText.setProperty("test", "test");
        manager.addSecretData("secret", clearText, dualEntry);
    }

    @Test
    public void testRenew() throws Exception {
        authenticate();
        Renew renew = new Renew();
        renew.setToken(token);
        String ret = submit(renew);
        byte[] result = decryptResult(ret);
        token = new String(result, "UTF-8");
        System.out.println(token);
    }

    @Test
    public void testGetSecret() throws Exception {
        authenticate();
        GetSecret request = new GetSecret();
        request.setToken(token);
        request.setAlias("secret");
        String ret = submit(request);
        ClearText ct = decryptResult(ret, ClearText.class);
        System.out.println(ct);
    }

    private void authenticate() throws Exception {
        Authenticate auth = new Authenticate();
        auth.setUserName("user1");
        auth.setPassword("password1".toCharArray());
        String ret = submit(auth);
        byte[] result = decryptResult(ret);
        token = new String(result, "UTF-8");
    }

    private String submit(Action action) throws Exception {
        Request request = new Request();
        request.setAction(action.getType());
        request.setClientKey(clientKey);
        String encryptedData =  sym.encrypt(secretKey, mapper.writeValueAsString(action).getBytes("UTF-8"), null);
        request.setEncryptedData(encryptedData);
        return keyServer.request(request);
    }

    private byte[] decryptResult(String ret) throws GeneralSecurityException {
        return sym.decrypt(secretKey, ret, null);
    }

    private <T> T decryptResult(String ret, Class<T> cls) throws GeneralSecurityException, IOException {
        byte[] result = sym.decrypt(secretKey, ret, null);
        try {
            String value = new String(result, "UTF-8");
            return mapper.readValue(value, cls);
        } catch (UnsupportedEncodingException e) {
            // impossible
            return null;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}