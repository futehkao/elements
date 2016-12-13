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

import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.cache.CacheFacade;
import net.e6tech.elements.network.restful.RestfulClient;
import net.e6tech.elements.security.AsymmetricCipher;
import net.e6tech.elements.security.SymmetricCipher;
import net.e6tech.elements.security.vault.ClearText;
import net.e6tech.elements.security.vault.Credential;

import javax.crypto.SecretKey;
import javax.ws.rs.NotAuthorizedException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh.
 */
public class KeyClient implements Startable {

    private AsymmetricCipher asym = AsymmetricCipher.getInstance("RSA");
    private SymmetricCipher sym = SymmetricCipher.getInstance("AES");
    private String clientKey;
    private SecretKey secretKey;
    private PublicKey publicKey;
    private RestfulClient client;
    private String address = "http://localhost:10000/restful/keyserver/v1";
    private Credential credential;
    private String user;
    private String obfuscatedPassword;
    private long renewInterval = 11 * 60000L;
    private boolean started;
    private boolean remoteEncryption = true;
    private CacheFacade<String, SecretKey> cachedSecretKeys;
    private CacheFacade<String, ClearText> cachedSecrets;
    private String clusterAddress;

    private String token;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public long getRenewInterval() {
        return renewInterval;
    }

    public void setRenewInterval(long renewInterval) {
        this.renewInterval = renewInterval;
    }

    public boolean isRemoteEncryption() {
        return remoteEncryption;
    }

    public void setRemoteEncryption(boolean remoteEncryption) {
        this.remoteEncryption = remoteEncryption;
    }

    public String getClusterAddress() {
        return clusterAddress;
    }

    public void setClusterAddress(String clusterAddress) {
        this.clusterAddress = clusterAddress;
    }

    public void start() {
        if (started) return;
        started = true;

        cachedSecretKeys = new CacheFacade<String, SecretKey>("secretKeys") {};
        cachedSecretKeys.initPool();

        cachedSecrets = new CacheFacade<String, ClearText>("secrets") {};
        cachedSecrets.initPool();

        initialAuthorization();
        startRenewalThread();
    }

    private void initialAuthorization() {
        client = new RestfulClient();
        client.setAddress(address);
        client.setClusterAddress(clusterAddress);

        try {
            net.e6tech.elements.network.restful.Response response = client.get("publicKey");
            SharedKey sharedKey = response.read(SharedKey.class);
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(sharedKey.getModulus(), sharedKey.getPublicExponent());
            secretKey = sym.generateKeySpec();
            publicKey = asym.getKeyFactory().generatePublic(publicKeySpec);
            clientKey = asym.encrypt(publicKey, secretKey.getEncoded());
        } catch (Exception e) {
            throw new NotAuthorizedException("Unable to authenticate with keyserver at " + address);
        }

        ServerSocket serverSocket = null;
        try{
            if (credential == null) {
                credential = new Credential();
            }
            credential.run("Authenticating key client");
            user = credential.getUser();
            byte[] buffer = new byte[credential.getPassword().length * 2];
            char[] chars = credential.getPassword();
            for(int i=0;i< chars.length;i++) {
                buffer[i*2] = (byte) (chars[i] >> 8);
                buffer[i*2+1] = (byte) chars[i];
            }
            obfuscatedPassword = sym.encrypt(secretKey, buffer, null);
            authorize(user, credential.getPassword());
        } catch(Exception ex){
            throw new RuntimeException(ex);
        } finally {
            if (serverSocket != null) try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startRenewalThread() {
        Thread thread = new Thread(()->{
            while (true) {
                try {
                    Thread.sleep(renewInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    renew();
                } catch (GeneralSecurityException e) {
                    reAuthorize();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    protected void reAuthorize() {
        try {
            byte[] bytes = sym.decrypt(secretKey, obfuscatedPassword, null);
            char[] chars = new char[bytes.length/2];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = (char) (((bytes[i*2] & 0xff) << 8) + (bytes[i*2+1] & 0xff));
            }
            authorize(user, chars);
        } catch (GeneralSecurityException e1) {
            throw new RuntimeException(e1);
        }
    }

    public void authorize(String user, char[] password) throws GeneralSecurityException {
        Authenticate auth = new Authenticate();
        auth.setUserName(user);
        auth.setPassword(password);
        String ret = submit(auth);
        byte[] result = decryptResult(ret);
        try {
            token = new String(result, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }
    }

    public boolean isAuthorized() {
        return (token != null);
    }

    public void renew() throws GeneralSecurityException {
        checkToken();
        Renew request = new Renew();
        String ret = submit(request);
        byte[] result = decryptResult(ret);
        try {
            token = new String(result, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }
    }

    // for remote calls
    public ClearText getSecret(String alias) throws GeneralSecurityException {
        checkToken();
        return cachedSecrets.get(alias, ()-> {
            GetSecret request = new GetSecret();
            request.setAlias(alias);
            String ret = submit(request);
            return decryptResult(ret, ClearText.class);
        });
    }

    // for remote calls
    public ClearText passwordUnlock(String alias) throws GeneralSecurityException {
        checkToken();
        PasswordUnlock request = new PasswordUnlock();
        request.setAlias(alias);
        String ret = submit(request);
        return decryptResult(ret, ClearText.class);
    }


    // encrypt data with key. key is encrypted with master key.
    public String encrypt(String key, byte[] data, String iv) throws GeneralSecurityException {
        if (remoteEncryption) {
            checkToken();
            Encrypt request = new Encrypt();
            request.setKeyBlock(key);
            request.setData(data);
            request.setIv(iv);
            String ret = submit(request);
            try {
                return new String(decryptResult(ret), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new GeneralSecurityException(e);
            }
        } else {
            SecretKey secretKey = getSecretKey(key);
            return sym.encrypt(secretKey, data, iv);
        }
    }

    // for decrypt data
    public byte[] decrypt(String key, String secret, String iv) throws GeneralSecurityException {
        if (remoteEncryption) {
            checkToken();
            Decrypt request = new Decrypt();
            request.setKeyBlock(key);
            request.setSecret(secret);
            request.setIv(iv);
            String ret = submit(request);
            return decryptResult(ret);
        } else {
            SecretKey secretKey = getSecretKey(key);
            return sym.decrypt(secretKey, secret, iv);
        }
    }

    private SecretKey getSecretKey(String key) throws GeneralSecurityException {
        return cachedSecretKeys.get(key, ()-> {
            byte[] keyBytes = decrypt(key);
            return sym.getKeySpec(keyBytes);
        });
    }

    // for decrypt keys
    public byte[] decrypt(String key) throws GeneralSecurityException {
        checkToken();
        Decrypt request = new Decrypt();
        request.setSecret(key);
        String ret = submit(request);
        return decryptResult(ret);
    }

    private String submit(Action action) throws GeneralSecurityException {
        Request request = new Request();
        request.setAction(action.getType());
        if (!(action instanceof Authenticate)) action.setToken(token);
        String encryptedData = null;
        try {
            encryptedData = sym.encrypt(secretKey, mapper.writeValueAsString(action).getBytes("UTF-8"), null);
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
        request.setEncryptedData(encryptedData);
        request.setClientKey(clientKey);

        net.e6tech.elements.network.restful.Response response = null;
        try {
            response = client.post("request", request);
            int code = response.getResponseCode();
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED && !(action instanceof Authenticate)) {
                reAuthorize();
                action.setToken(token);
                response = client.post("request", request);
            } else if (code < 200 || code > 202) {
                throw new GeneralSecurityException();
            }
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }

        return response.getResult();
    }

    private byte[] decryptResult(String ret) throws GeneralSecurityException {
        return sym.decrypt(secretKey, ret, null);
    }

    private <T> T decryptResult(String ret, Class<T> cls) throws GeneralSecurityException {
        byte[] result = sym.decrypt(secretKey, ret, null);
        try {
            String value = new String(result, "UTF-8");
            return mapper.readValue(value, cls);
        } catch (UnsupportedEncodingException e) {
            // impossible
            return null;
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    private void checkToken() throws GeneralSecurityException {
        if (token == null) throw new GeneralSecurityException("Not authenticated");
    }

}
