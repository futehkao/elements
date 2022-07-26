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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.restful.RestfulClient;
import net.e6tech.elements.security.AsymmetricCipher;
import net.e6tech.elements.security.SymmetricCipher;
import net.e6tech.elements.security.vault.ClearText;
import net.e6tech.elements.security.vault.Credential;

import javax.crypto.SecretKey;
import javax.ws.rs.NotAuthorizedException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh.
 */
public class KeyClient implements Startable {

    private static Logger logger = Logger.getLogger();
    private Cache<String, SecretKey> cachedSecretKeys = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .initialCapacity(50)
            .expireAfterWrite(600, TimeUnit.SECONDS)
            .build();
    private Cache<String, ClearText> cachedSecrets = CacheBuilder.newBuilder()
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .initialCapacity(50)
            .expireAfterWrite(600, TimeUnit.SECONDS)
            .build();
    private AsymmetricCipher asym = AsymmetricCipher.getInstance("RSA");
    private SymmetricCipher sym = SymmetricCipher.getInstance("AES");
    private String clientKey;
    private SecretKey secretKey;
    private RestfulClient client;
    private String address = "http://localhost:10000/restful/keyserver/v1";
    private Credential credential;
    private String authorization;
    private boolean started;
    private boolean remoteEncryption = true;

    private int connectionRetries = 12;

    private long connectionRetryWait = 10000L;

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

    public boolean isRemoteEncryption() {
        return remoteEncryption;
    }

    public void setRemoteEncryption(boolean remoteEncryption) {
        this.remoteEncryption = remoteEncryption;
    }

    public int getConnectionRetries() {
        return connectionRetries;
    }

    public void setConnectionRetries(int connectionRetries) {
        if (connectionRetries < 0)
            throw new IllegalArgumentException("Invalid retry number: " + connectionRetries);
        this.connectionRetries = connectionRetries;
    }

    public long getConnectionRetryWait() {
        return connectionRetryWait;
    }

    public void setConnectionRetryWait(long connectionRetryWait) {
        this.connectionRetryWait = connectionRetryWait;
    }

    public void start() {
        if (started)
            return;
        started = true;

        initCredential();
    }

    @SuppressWarnings("squid:S1181")
    private void initCredential() {
        client = new RestfulClient();
        client.setAddress(address);
        PublicKey publicKey;
        net.e6tech.elements.network.restful.Response response = null;
        int numRetries = connectionRetries;
        boolean connected = false;
        while (!connected) {
            try {
                if (numRetries != connectionRetries) {
                    logger.info("Retrying connection to key server at " + address);
                }

                response = client.get("publicKey");
                connected = true;
            } catch (IOException e) {
                if (numRetries <= 0)
                    throw new SystemException("Unable to connect with key server at " + address, e);
                if (numRetries == connectionRetries) {
                    logger.info("Key server is down.  Will retry connection to " + address + " " + numRetries + " times.");
                }
                numRetries--;
                logger.info("Will retry connection to key server at " + address + " after " + connectionRetryWait + "ms.");
                try {
                    Thread.sleep(connectionRetryWait);
                } catch (InterruptedException ex) {
                    Logger.suppress(e);
                }
            } catch (Throwable e) {
                logger.warn("Cannot connect to key server at " + address, e);
                throw new SystemException("Unable to authenticate with key server at " + address, e);
            }
        }

        logger.info("Connected to key server at " + address);
        try {
            SharedKey sharedKey = response.read(SharedKey.class);
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(sharedKey.getModulus(), sharedKey.getPublicExponent());
            secretKey = sym.generateKeySpec();
            publicKey = asym.getKeyFactory().generatePublic(publicKeySpec);
            clientKey = asym.encrypt(publicKey, secretKey.getEncoded());
        } catch (IOException e) {
            logger.warn("Unable to read shared key server at " + address, e);
            throw new SystemException("Unable to read shared key from key server at " + address, e);
        } catch (GeneralSecurityException e) {
            logger.warn("Unable to generate public key for server at " + address, e);
            throw new SystemException("Unable to generate public key for server at " + address, e);
        }

        try{
            if (credential == null) {
                credential = new Credential();
            }
            credential.run("Authenticating key client");

            String encryptedData = asym.encrypt(publicKey, mapper.writeValueAsString(credential).getBytes(StandardCharsets.UTF_8));
            authorization = sym.encrypt(secretKey, sym.toBytes(encryptedData), null);
        } catch(Exception ex){
            throw new SystemException(ex);
        }
    }


    public boolean isAuthorized() {
        return (authorization != null);
    }

    // for remote calls
    public ClearText getSecret(String alias) throws GeneralSecurityException {
        checkAuthorize();
        try {
            return cachedSecrets.get(alias, ()-> {
                GetSecret request = new GetSecret();
                request.setAlias(alias);
                String ret = submit(request);
                return decryptResult(ret, ClearText.class);
            });
        } catch (ExecutionException e) {
            throw new GeneralSecurityException(e.getCause());
        }
    }

    // for remote calls
    public ClearText passwordUnlock(String alias) throws GeneralSecurityException {
        checkAuthorize();
        PasswordUnlock request = new PasswordUnlock();
        request.setAlias(alias);
        String ret = submit(request);
        return decryptResult(ret, ClearText.class);
    }

    // encrypt data with key. key is encrypted with master key.
    public String encrypt(String key, byte[] data, String iv) throws GeneralSecurityException {
        if (remoteEncryption) {
            checkAuthorize();
            Encrypt request = new Encrypt();
            request.setKeyBlock(key);
            request.setData(data);
            request.setIv(iv);
            String ret = submit(request);
            return new String(decryptResult(ret), StandardCharsets.UTF_8);
        } else {
            SecretKey skey = getSecretKey(key);
            return sym.encrypt(skey, data, iv);
        }
    }

    // for decrypt data
    public byte[] decrypt(String key, String secret, String iv) throws GeneralSecurityException {
        if (remoteEncryption) {
            checkAuthorize();
            Decrypt request = new Decrypt();
            request.setKeyBlock(key);
            request.setSecret(secret);
            request.setIv(iv);
            String ret = submit(request);
            return decryptResult(ret);
        } else {
            SecretKey skey = getSecretKey(key);
            return sym.decrypt(skey, secret, iv);
        }
    }

    private SecretKey getSecretKey(String key) throws GeneralSecurityException {
        try {
            return cachedSecretKeys.get(key, ()-> {
                byte[] keyBytes = decrypt(key);
                return sym.getKeySpec(keyBytes);
            });
        } catch (ExecutionException e) {
            throw new GeneralSecurityException(e.getCause());
        }
    }

    // for decrypt keys
    public byte[] decrypt(String key) throws GeneralSecurityException {
        checkAuthorize();
        Decrypt request = new Decrypt();
        request.setSecret(key);
        String ret = submit(request);
        return decryptResult(ret);
    }

    @SuppressWarnings("squid:S1181")
    private String submit(Action action) throws GeneralSecurityException {
        Request request = new Request();
        request.setAction(action.getType());

        request.setAuthorization(authorization);
        String encryptedData = null;
        try {
            encryptedData = sym.encrypt(secretKey, mapper.writeValueAsString(action).getBytes(StandardCharsets.UTF_8), null);
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
        request.setEncryptedData(encryptedData);
        request.setClientKey(clientKey);

        net.e6tech.elements.network.restful.Response response = null;
        try {
            response = client.post("request", request);
            int code = response.getResponseCode();
            if (code < 200 || code > 202) {
                throw new GeneralSecurityException();
            }
        } catch (Throwable e) {
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
            String value = new String(result, StandardCharsets.UTF_8);
            return mapper.readValue(value, cls);
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    private void checkAuthorize() throws GeneralSecurityException {
        if (authorization == null)
            throw new GeneralSecurityException("Not authenticated");
    }

}
