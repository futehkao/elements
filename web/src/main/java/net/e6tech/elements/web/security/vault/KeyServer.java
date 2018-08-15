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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.vault.ClearText;
import net.e6tech.elements.security.vault.Credential;
import net.e6tech.elements.security.vault.VaultManager;
import net.e6tech.elements.web.security.vault.client.*;

import javax.crypto.BadPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.login.LoginException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.spec.RSAPublicKeySpec;
import java.util.concurrent.TimeUnit;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh.
 */
@Path("/keyserver/v1")
public class KeyServer {

    private static Logger logger = Logger.getLogger();
    private VaultManager vaultManager;
    private Provision provision;
    private LoadingCache<String, SecretKey> clientKeys = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .initialCapacity(20)
            .expireAfterWrite(60 * 60 * 1000L, TimeUnit.MILLISECONDS)
            .concurrencyLevel(Provision.cacheBuilderConcurrencyLevel)
            .build(new CacheLoader<String, SecretKey>() {
                public SecretKey load(String clientKey) throws Exception {
                    byte[] decrypted = vaultManager.decryptPrivate(clientKey);
                    return new SecretKeySpec(decrypted, vaultManager.getSymmetricCipher().getAlgorithm());
                }
            });

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    @Inject
    public void setVaultManager(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public LoadingCache<String, SecretKey> getClientKeys() {
        return clientKeys;
    }

    public void setClientKeys(LoadingCache<String, SecretKey> clientKeys) {
        this.clientKeys = clientKeys;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("publicKey")
    public String getPublicKey() {
        try {
            RSAPublicKeySpec keySpec = vaultManager.getPublicKey();
            if (keySpec == null)
                return null;
            SharedKey sharedKey = new SharedKey();
            sharedKey.setModulus(keySpec.getModulus());
            sharedKey.setPublicExponent(keySpec.getPublicExponent());
            return mapper.writeValueAsString(sharedKey);
        } catch (BadPaddingException ex) {
           logger.error("bad vault");
            throw new SystemException(ex);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("request")
    public String request(Request request){
        String clsName = Action.class.getPackage().getName() + "." + request.getAction();
        Action action = null;
        SecretKey clientKey = null;
        try {
            clientKey = clientKeys.get(request.getClientKey());
            byte[] decrypted = vaultManager.getSymmetricCipher().decrypt(clientKey, request.getEncryptedData(), null);
            String encoded = new String(decrypted, "UTF-8");
            Class requestClass = getClass().getClassLoader().loadClass(clsName);
            action = (Action) mapper.readValue(encoded, requestClass);
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
        }

        String value = null;
        try {
            if (action instanceof Authenticate) {
                Authenticate auth = (Authenticate) action;
                value = vaultManager.authorize(new Credential(auth.getUserName(), auth.getPassword()));
            } else if (action instanceof Renew) {
                Renew renew = (Renew) action;
                value = vaultManager.renew(renew.getToken());
            } else if (action instanceof GetSecret) {
                GetSecret getSecret = (GetSecret) action;
                ClearText ct = vaultManager.getSecretData(getSecret.getToken(), getSecret.getAlias());
                value = mapper.writeValueAsString(ct);
            } else if (action instanceof PasswordUnlock) {
                PasswordUnlock unlock = (PasswordUnlock) action;
                ClearText ct = vaultManager.passphraseUnlock(unlock.getToken(), unlock.getAlias());
                value = mapper.writeValueAsString(ct);
            } else if (action instanceof Encrypt) {
                Encrypt encrypt = (Encrypt) action;
                value = vaultManager.encrypt(encrypt.getToken(), encrypt.getKeyBlock(), encrypt.getData(), encrypt.getIv());
            } else if (action instanceof Decrypt) {
                Decrypt decrypt = (Decrypt) action;
                byte[] result;
                if (decrypt.getKeyBlock() == null) {
                    result = vaultManager.decrypt(decrypt.getToken(), decrypt.getSecret());
                } else {
                    result = vaultManager.decrypt(decrypt.getToken(), decrypt.getKeyBlock(), decrypt.getSecret(), decrypt.getIv());
                }
                return vaultManager.getSymmetricCipher().encrypt(clientKey, result, null);
            } else {
                throw new SystemException("Unsupported action " + action);
            }

            return vaultManager.getSymmetricCipher().encrypt(clientKey, value.getBytes("UTF-8"), null);
        } catch (LoginException ex) {
            logger.warn("" + action, ex);
            throw new NotAuthorizedException(Response.status(Response.Status.UNAUTHORIZED).build());
        } catch(RuntimeException ex) {
            throw ex;
        }  catch (Exception ex) {
            logger.warn("" + action, ex);
            throw new SystemException(ex);
        }
    }
}
