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
package net.e6tech.elements.security.vault;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.AsymmetricCipher;
import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.RNG;
import net.e6tech.elements.security.SymmetricCipher;

import javax.crypto.SecretKey;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.Set;

import static net.e6tech.elements.security.vault.Constants.*;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S1192", "squid:RedundantThrowsDeclarationCheck", "squid:S00100"})
public class VaultManager {
    private static Logger logger = Logger.getLogger();

    public static final String KEY_VAULT = "key-vault";
    public static final String USER_VAULT = "user-vault";
    public static final String DATA_VAULT = "data-vault";
    public static final String LOCAL_VAULT = "local-vault";

    private SymmetricCipher symmetricCipher;
    private AsymmetricCipher asymmetricCipher;
    private long authorizationDuration = 15 * 60000L;

    private VaultStore userLocalStore;     // file based user password protected
    private boolean userLocalOpened = false;
    private VaultStore keyDataStore;      // versioned secret password protected
    private boolean keyDataOpened = false;

    private PasswordProtected pwd = new PasswordProtected();
    private KeyProtected keyEncryption = new KeyProtected();

    private VaultManagerState state = new VaultManagerState();
    private Random random = new Random();

    public VaultManager() {
        symmetricCipher = SymmetricCipher.getInstance("AES");
        symmetricCipher.setBase64(false);
        asymmetricCipher = AsymmetricCipher.getInstance("RSA");
        keyDataStore = new FileStore();
        userLocalStore = keyDataStore;

        userLocalStore.manage(USER_VAULT, LOCAL_VAULT);
        keyDataStore.manage(KEY_VAULT, DATA_VAULT);
    }

    public long getAuthorizationDuration() {
        return authorizationDuration;
    }

    public void setAuthorizationDuration(long authorizationDuration) {
        this.authorizationDuration = authorizationDuration;
    }

    public VaultStore getKeyDataStore() {
        return keyDataStore;
    }

    public void setKeyDataStore(VaultStore keyDataStore) {
        this.keyDataStore = keyDataStore;
    }

    public VaultStore getUserLocalStore() {
        return userLocalStore;
    }

    public void setUserLocalStore(VaultStore userLocalStore) {
        this.userLocalStore = userLocalStore;
    }

    public SymmetricCipher getSymmetricCipher() {
        return symmetricCipher;
    }

    public AsymmetricCipher getAsymmetricCipher() {
        return asymmetricCipher;
    }

    private ClearText generateInternalKeyPair(String alias) throws GeneralSecurityException {
        KeyPair keyPair = asymmetricCipher.generateKeySpec();
        KeyFactory fact = asymmetricCipher.getKeyFactory();
        RSAPublicKeySpec pub = fact.getKeySpec(keyPair.getPublic(), RSAPublicKeySpec.class);
        RSAPrivateKeySpec priv = fact.getKeySpec(keyPair.getPrivate(), RSAPrivateKeySpec.class);

        ClearText ct = new ClearText();
        ct.alias(alias);
        BigInteger mod = priv.getModulus();
        BigInteger exp = priv.getPrivateExponent();
        String encoded = mod.toString(16) + "$" + exp.toString(16);
        try {
            ct.setBytes(encoded.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
        }
        ct.setProperty(TYPE, KEY_PAIR_TYPE);
        ct.setProperty(ALGORITHM, asymmetricCipher.getAlgorithm());
        ct.setProperty(ClearText.PUBLIC_KEY_MOD, pub.getModulus().toString(16));
        ct.setProperty(ClearText.PUBLIC_KEY_EXP, pub.getPublicExponent().toString(16));
        ct.protect();
        return ct;
    }

    private ClearText generateSignature() throws GeneralSecurityException {
        ClearText ct = new ClearText();
        byte[] bytes = RNG.generateSeed(16);
        ct.setBytes(bytes);
        ct.alias(SIGNATURE);
        ct.setProperty(SIGNATURE, Hex.toString(bytes));
        ct.setProperty(TYPE, SIGNATURE_TYPE);
        ct.setProperty(SIGNATURE_FORMAT, SIGNATURE_FORMAT_VERSION);
        ct.protect();
        return ct;
    }

    private ClearText getPassphrase() throws GeneralSecurityException {
        Secret secret = getLocal(PASSPHRASE, null);
        return pwd.unsealUserOrPassphrase(secret, state.getPassword());
    }

    private ClearText getPassphrase(Secret secret) throws GeneralSecurityException {
        String[] components =secret.getSecret().split("\\$");
        Secret passphraseSecrect;
        if (components.length >= 5) {
            String keyVersion = components[4];
            passphraseSecrect = getLocal(PASSPHRASE, keyVersion);
        } else {
            passphraseSecrect = getLocal(PASSPHRASE, null);
        }
        return pwd.unsealUserOrPassphrase(passphraseSecrect, state.getPassword());
    }

    private ClearText generatePassphrase(char[] password) throws GeneralSecurityException {
        ClearText ct = new ClearText();
        try {
            ct.setBytes(new String(password).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
        }
        ct.alias(PASSPHRASE);
        ct.setProperty(TYPE, PASSPHRASE_TYPE);
        ct.protect();
        return ct;
    }

    private ClearText generateInternalKey(String alias) throws GeneralSecurityException {
        SecretKey secretKey = symmetricCipher.generateKeySpec();
        ClearText ct = new ClearText();
        ct.setBytes(secretKey.getEncoded());
        ct.alias(alias);
        ct.setProperty(TYPE, KEY_TYPE);
        ct.setProperty(ALGORITHM, "AES");
        ct.protect();
        return ct;
    }

    private ZonedDateTime setCreationTimeVersion(ClearText ct) {
        ZonedDateTime now = ZonedDateTime.now();
        String dateTime = now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        if (ct.version() == null) {
            ct.setProperty(CREATION_DATE_TIME, dateTime);
            ct.setProperty(CREATION_TIME, Long.toString(now.toInstant().toEpochMilli()));
            ct.version(Long.toString(now.toInstant().toEpochMilli()));
        }
        return now;
    }

    public RSAPublicKeySpec getPublicKey() throws GeneralSecurityException {
        KeyFactory fact = asymmetricCipher.getKeyFactory();
        ClearText key = getKey(ASYMMETRIC_KEY_ALIAS, null);
        if (key == null)
            return null;
        return fact.getKeySpec(key.asKeyPair().getPublic(), RSAPublicKeySpec.class);
    }

    // not to be exposed to remote calls
    public boolean validateUser(String user, char[] password) {
        try {
            if (user == null || password == null)
                return false;
            ClearText ct1 = getUser(new Credential(user, password));
            if (ct1 == null)
                return false;
            return ct1.getProperty(TYPE).equals(USER_TYPE);
        } catch (Exception th) {
            Logger.suppress(th);
            return false;
        }
    }

    public String authorize(Credential credential) throws GeneralSecurityException {
        checkAccess(credential);
        long now = System.currentTimeMillis();
        String token = "" + Long.toString(now) + "-" + random.nextInt(1000000);
        try {
            return _encrypt(AUTHORIZATION_KEY_ALIAS, token.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
            return null;
        }
    }

    public String renew(String token) throws GeneralSecurityException {
        checkToken(token);
        long now = System.currentTimeMillis();
        String nextToken = "" + Long.toString(now) + "-" + random.nextInt(1000000);
        try {
            return _encrypt(AUTHORIZATION_KEY_ALIAS, nextToken.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
            return null;
        }
    }

    private void checkToken(String token) throws GeneralSecurityException {
        if (token == null)
            throw new LoginException();
        byte[] decrypted = _decrypt(token);
        try {
            String plain = new String(decrypted, "UTF-8");
            int index = plain.indexOf('-');
            if (index < 0)
                throw new LoginException("Invalid toke format");
            long time = Long.parseLong(plain.substring(0, index));
            if (System.currentTimeMillis() - time > authorizationDuration) {
                Date date = new Date(time);
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd HHmmss");
                throw new LoginException("Token issued on " + format.format(date) + " expired.  Current time is " + format.format(new Date()));
            }
        } catch (UnsupportedEncodingException e) {
            Logger.suppress(e);
            throw new LoginException();
        }
    }

    /**
     * Adding a public private key.  The key is treated as if it is regular data and store in the
     * data vault.  As always, data vault items are encrypted with m-key
     *
     * @param alias alias of the key
     * @param dualEntry dual entry containing authentication info for two users.
     * @throws GeneralSecurityException general exception
     */
    public void addKeyPair(String alias, DualEntry dualEntry) throws GeneralSecurityException {
        checkAccess(dualEntry);
        ClearText ct = generateInternalKeyPair(alias);
        addData(ct);
    }

    /**
     * Adding secret to the data vault.  It will be encrypted by m-key when stored.
     *
     * @param alias alias of the secret data
     * @param dualEntry dual entry containing authentication info for two users.
     * @throws GeneralSecurityException general exception
     */
    public void addSecretData(String alias, ClearText ct, DualEntry dualEntry) throws GeneralSecurityException {
        checkAccess(dualEntry);
        ct.alias(alias);
        ct.setProperty(TYPE, SECRET_TYPE);
        ct.setProtectedProperty(TYPE, SECRET_TYPE);
        addData(ct);
    }

    // for remote calls
    public ClearText getSecretData(String token, String alias) throws GeneralSecurityException {
        return getSecretData(token, alias, null);
    }

    public ClearText getSecretData(String token, String alias, String version) throws GeneralSecurityException {
        checkToken(token);
        Secret secret = getData(alias, version);
        if (secret == null)
            return null;
        String[] components = encryptedComponents(secret.getSecret());
        String keyAlias = components[2];
        String keyVersion = components[3];
        return keyEncryption.unseal(secret, getKey(keyAlias, keyVersion));
    }

    public ClearText getSecretData(Credential credential, String alias) throws GeneralSecurityException {
        return getSecretData(credential, alias, null);
    }

    public ClearText getSecretData(Credential credential, String alias, String version) throws GeneralSecurityException {
        checkAccess(credential);
        Secret secret = getData(alias, version);
        if (secret == null)
            return null;
        String[] components = encryptedComponents(secret.getSecret());
        String keyAlias = components[2];
        String keyVersion = components[3];
        return keyEncryption.unseal(secret, getKey(keyAlias, keyVersion));
    }

    private String[] encryptedComponents(String encoded) {
        String[] components = encoded.split("\\$");
        if (components.length != 4) {
            throw new IllegalStateException("Invalid encryption format");
        }
        return components;
    }

    public String generateKey(DualEntry dualEntry) throws GeneralSecurityException {
        byte[] plain = symmetricCipher.generateKeySpec().getEncoded();
        checkAccess(dualEntry);
        return _encrypt(MASTER_KEY_ALIAS, plain);
    }

    // encrypt data with key. key is encrypted with master key.
    public String encrypt(String token, String key, byte[] data, String iv) throws GeneralSecurityException {
        checkToken(token);
        byte[] keyBytes =  _decrypt(key);
        SecretKey secretKey = symmetricCipher.getKeySpec(keyBytes);
        return symmetricCipher.encrypt(secretKey, data, iv);
    }

    // for decrypt data
    public byte[] decrypt(String token, String key, String secret, String iv) throws GeneralSecurityException {
        checkToken(token);
        byte[] keyBytes =  _decrypt(key);
        SecretKey secretKey = symmetricCipher.getKeySpec(keyBytes);
        return symmetricCipher.decrypt(secretKey, secret, iv);
    }

    // for decrypt keys
    public byte[] decrypt(String token, String secret) throws GeneralSecurityException {
        checkToken(token);
        return _decrypt(secret);
    }

    public String encryptPublic(byte[] data) throws GeneralSecurityException {
        ClearText key = getKey(ASYMMETRIC_KEY_ALIAS, null);
        if (key == null)
            throw new GeneralSecurityException("Public/Private key not set up");
        return asymmetricCipher.encrypt(key.asKeyPair().getPublic(), data);
    }

    public byte[] decryptPrivate(String secret) throws GeneralSecurityException {
        ClearText key = getKey(ASYMMETRIC_KEY_ALIAS, null);
        if (key == null)
            throw new GeneralSecurityException("Public/Private key not set up");
        return asymmetricCipher.decrypt(key.asKeyPair().getPrivate(), secret);
    }

    private ClearText getUser(Credential credential) throws GeneralSecurityException {
        Secret user = getUser(credential.getUser(), null);
        if (user == null)
            return null;
        // need to get passphrase
        return pwd.unsealUserOrPassphrase(user, credential.getPassword());
    }

    private void newUser(byte[] component, Credential credential, String group) throws GeneralSecurityException {
        Secret user = getUser(credential.getUser(), null);
        if (user != null)
            throw new GeneralSecurityException("User exists: " + credential.getUser());
        ClearText ct1 = new ClearText();
        ct1.alias(credential.getUser());
        ct1.setBytes(component);
        ct1.setProperty(USERNAME, credential.getUser());
        ct1.setProperty(GUARDIAN, group);
        ct1.setProperty(TYPE, USER_TYPE);
        setCreationTimeVersion(ct1);
        ct1.protect();
        addUser(pwd.sealUser(ct1, credential.getPassword()));
    }

    public void addUser(Credential newUser, Credential existingUser) throws GeneralSecurityException {
        checkAccess(existingUser);
        ClearText ct1 = getUser(existingUser);
        if (ct1 == null)
            throw new GeneralSecurityException("Existing user not found: " + existingUser.getUser());
        newUser(ct1.getBytes(), newUser, ct1.getProperty(GUARDIAN));
    }

    public void changePassword(String user, char[] oldPwd, char[] newPwd) throws GeneralSecurityException {
        Vault userVault = userLocalStore.getVault(USER_VAULT);
        Set<Long> versions = userVault.versions(user);

        for (Long version : versions) {
            Secret secret = getUser(user, "" + version);
            ClearText ct = pwd.unsealUserOrPassphrase(secret, oldPwd);
            addUser(pwd.sealUser(ct, newPwd));
        }
    }

    // Encrypting ClearText with dualEntry's associated passphrase and store it in local, ie file.
    public void passphraseLock(String alias, ClearText ct, DualEntry dualEntry) throws GeneralSecurityException {
        ct.alias(alias);
        ClearText clearPassphrase;
        if (alias.equals(PASSPHRASE)) {
            clearPassphrase = generatePassphrase(getPassphrase(dualEntry));
            clearPassphrase.version("0");
        } else {
            Secret passphrase = getLocal(PASSPHRASE, null);
            clearPassphrase = pwd.unsealUserOrPassphrase(passphrase, getPassphrase(dualEntry));
        }
        addLocal(ct, clearPassphrase);
    }

    public ClearText passphraseUnlock(String alias, Credential credential) throws GeneralSecurityException {
        checkAccess(credential);
        Secret secret = getLocal(alias, null);
        if (secret == null)
            return null;
        return pwd.unseal(secret, getPassphrase());
    }

    public ClearText passphraseUnlock(String token, String alias) throws GeneralSecurityException {
        checkToken(token);
        Secret secret = getLocal(alias, null);
        if (secret == null)
            return null;
        return pwd.unseal(secret, getPassphrase());
    }

    public void newMasterKey(DualEntry dualEntry) throws GeneralSecurityException {
        getPassphrase(dualEntry);
        addKey(generateInternalKey(MASTER_KEY_ALIAS));
    }

    private void checkAccess(Credential credential) throws GeneralSecurityException {
        ClearText ct = getUser(credential);
        if (ct == null)
            throw new GeneralSecurityException("Cannot find user " + credential.getUser());
        if (!ct.getProperty(GUARDIAN).equals(GROUP_1) || !ct.getProperty(GUARDIAN).equals(GROUP_2))
            new GeneralSecurityException("User " + ct.getProperty(USERNAME) + " is not a guardian");

        if (ct.getProperty(GUARDIAN).equals(ct.getProtectedProperty(GUARDIAN))) {
            new GeneralSecurityException("User " + ct.getProperty(USERNAME) + " guardian property has been tempered");
        }
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void checkAccess(DualEntry dualEntry) throws GeneralSecurityException {
        ClearText ct1 = getUser(dualEntry.getUser1());
        ClearText ct2  = getUser(dualEntry.getUser2());

        if (ct1 == null)
            throw new GeneralSecurityException("Bad user name " + dualEntry.getUser1());
        if (ct2 == null)
            throw new GeneralSecurityException("Bad user name " + dualEntry.getUser2());

        if (ct1.getProperty(GUARDIAN) == null ||
                !(ct1.getProperty(GUARDIAN).equals(GROUP_1) || ct1.getProperty(GUARDIAN).equals(GROUP_2))) {
            throw new SystemException("User " + ct1.getProperty(USERNAME) + " is not a guardian");
        }
        if (ct2.getProperty(GUARDIAN) == null ||
                !(ct2.getProperty(GUARDIAN).equals(GROUP_1) || ct2.getProperty(GUARDIAN).equals(GROUP_2))) {
            throw new GeneralSecurityException("User " + ct2.getProperty(USERNAME) + " is not a guardian");
        }
        if (ct1.getProperty(GUARDIAN).equals(ct2.getProperty(GUARDIAN))) {
            throw new SystemException("User1 " + ct1.getProperty(USERNAME) + " and user 2 "
                    + ct2.getProperty(USERNAME) + " cannot be in the same group");
        }

        if (ct1.getProperty(GUARDIAN).equals(ct1.getProtectedProperty(GUARDIAN))) {
            new GeneralSecurityException("User " + ct1.getProperty(USERNAME) + " guardian property has been tempered");
        }

        if (ct2.getProperty(GUARDIAN).equals(ct2.getProtectedProperty(GUARDIAN))) {
            new GeneralSecurityException("User " + ct2.getProperty(USERNAME) + " guardian property has been tempered");
        }
    }

    private char[] getPassphrase(DualEntry dualEntry) throws GeneralSecurityException {
        ClearText ct1 = getUser(dualEntry.getUser1());
        if (ct1 == null)
            throw new GeneralSecurityException("Cannot find user " + dualEntry.getUser1());
        ClearText ct2  = getUser(dualEntry.getUser2());
        if (ct2 == null)
            throw new GeneralSecurityException("Cannot find user " + dualEntry.getUser2());

        checkAccess(dualEntry);
        byte[] comp1 = ct1.getBytes();
        byte[] comp2 = ct2.getBytes();
        byte[] pwdBytes = new byte[comp1.length];
        for (int i = 0; i< comp1.length; i++)
            pwdBytes[i] = (byte)((comp1[i] ^ comp2[i]) & 0x000000ff);
        return Hex.toString(pwdBytes).toCharArray();
    }

    /**
     * Change secret password.  We need to take the following steps:
     * 1. check user access.
     * 2. generate new components for user 1 and 2.
     * 3. remove all other users
     * 4. re-encrypt everything in local vault
     * 5. re-encrypt keys in the database.  We need to iterate through all version for each alias.
     * @param dualEntry dual entry containing authentication info for two users.
     */
    public void changePassphrase(DualEntry dualEntry) throws GeneralSecurityException {
        checkAccess(dualEntry);

        // save password and signature
        VaultManagerState oldState = state.clone();

        // make backup of file and database
        String currentVersion = state.getSignature().version();
        try {
            userLocalStore.backup(currentVersion);
            keyDataStore.backup(currentVersion);
        } catch (IOException ex) {
            throw new GeneralSecurityException(ex);
        }

        try {
            byte[] comp1 = RNG.generateSeed(16);
            byte[] comp2 = RNG.generateSeed(16);

            ClearText ct1 = getUser(dualEntry.getUser1());
            ClearText ct2 = getUser(dualEntry.getUser2());

            // change components for the users
            ct1.setBytes(comp1);
            ct2.setBytes(comp2);

            addUser(pwd.sealUser(ct1, dualEntry.getUser1().getPassword()));
            addUser(pwd.sealUser(ct2, dualEntry.getUser2().getPassword()));

            // add the new passphrase
            ClearText passphrase = generatePassphrase(getPassphrase(dualEntry));
            passphraseLock(PASSPHRASE, passphrase, dualEntry);

            // re-encrypt local vault
            Vault localVault = userLocalStore.getVault(LOCAL_VAULT);

            // remove existing signature.  This needs to be done before we re-encrypt local vault
            localVault.removeSecret(SIGNATURE, null);

            // ok, really re-encrypting all local vault
            Set<String> aliases = localVault.aliases();
            for (String alias : aliases) {
                Set<Long> versions = localVault.versions(alias);
                for (Long version : versions) {
                    Secret secret = getLocal(alias, "" + version);
                    ClearText ct = pwd.unseal(secret, passphrase);
                    addLocal(ct, passphrase);
                }
            }

            // re-encrypt database's key-vault
            Vault keyVault = keyDataStore.getVault(KEY_VAULT);
            aliases = keyVault.aliases();
            for (String alias : aliases) {
                Set<Long> versions = keyVault.versions(alias);
                for (Long version : versions) {
                    ClearText ct = getKey(alias, "" + version);
                    Secret secret = pwd.seal(ct, passphrase);
                    keyVault.addSecret(secret);
                }
            }

            // delete database signature
            keyDataStore.getVault(DATA_VAULT).removeSecret(SIGNATURE, null);

            // remove all other users
            aliases = listUsers();
            for (String alias : aliases) {
                if (!dualEntry.getUser1().getUser().equals(alias)
                        && !dualEntry.getUser2().getUser().equals(alias)) removeUser(alias, null);
            }

            // add new signature
            ClearText newSignature = generateSignature();
            addLocal(newSignature, passphrase);

            // need to start using the new password since we just finished re-encrypting
            state.getCachedKeys().clear();
            state.setPassword((new String(passphrase.getBytes(), "UTF-8")).toCharArray());
            addData(newSignature);
            state.setSignature(newSignature);

        } catch (Exception th) {
            // restore file and database
            state = oldState;
            try {
                userLocalStore.restore(currentVersion);
                keyDataStore.restore(currentVersion);
            } catch (IOException ex) {
                throw new GeneralSecurityException(ex);
            }
            throw new GeneralSecurityException(th);
        }

        // commit file changes
        // commit db changes if failed rollback file changes
        // files store need to detect if a backup exist.
        try {
            userLocalStore.save();
        } catch (Exception th) {
            // restore file and database from backup
            restoreState(currentVersion, oldState);
            throw new GeneralSecurityException(th);
        }

        try {
            keyDataStore.save();
        } catch (Exception th) {
            // restore file and database from backup
            restoreState(currentVersion, oldState);
            state = oldState;
            throw new GeneralSecurityException(th);
        }
    }

    private void restoreState(String currentVersion, VaultManagerState oldState) throws GeneralSecurityException {
        try {
            restore(currentVersion);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        state = oldState;
    }

    private void restore(String version) throws IOException {
        userLocalStore.restore(version);
        keyDataStore.restore(version);
    }

    @SuppressWarnings("squid:S00100")
    private String _encrypt(String keyAlias, byte[] plain) throws GeneralSecurityException {
        ClearText ct = getKey(keyAlias, null);
        if (ct == null)
            throw new GeneralSecurityException("No key for keyAlias=" + keyAlias);

        String iv = symmetricCipher.generateIV();
        String encrypted = symmetricCipher.encrypt(ct.asSecretKey(), plain, iv);
        return iv + "$" + encrypted + "$" + keyAlias + "$" + ct.version(); // iv and enc
    }

    public byte[] _decrypt(String encoded) throws GeneralSecurityException {
        String[] components = encryptedComponents(encoded);
        String alias = components[2];
        String version = components[3];
        ClearText ct = getKey(alias, version);
        if (ct == null)
            throw new GeneralSecurityException("No key for keyAlias=" + alias);
        return symmetricCipher.decrypt(ct.asSecretKey(), components[1], components[0]);
    }

    public void save() throws IOException {
        userLocalStore.save();
        keyDataStore.save();
    }

    public void close() throws IOException {
        userLocalStore.close();
        keyDataStore.close();
    }

    public void open(DualEntry dualEntry) throws GeneralSecurityException {
        if (userLocalOpened)
            return;
        try {
            userLocalStore.open();
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        userLocalOpened = true;

        boolean modified = false;

        // if no users in the vault we will create passphrase component 1 and 2.
        if (userLocalStore.getVault(USER_VAULT).size() == 0) {
            byte[] comp1 = RNG.generateSeed(16);
            byte[] comp2 = RNG.generateSeed(16);
            newUser(comp1, dualEntry.getUser1(), GROUP_1);
            newUser(comp2, dualEntry.getUser2(), GROUP_2);

            try {
                state.setPassword(getPassphrase(dualEntry));
            } catch (GeneralSecurityException ex) {
                throw new GeneralSecurityException("Bad user name or password to unlock the vault", ex);
            }

            // add passphrase
            ClearText passphrase = generatePassphrase(getPassphrase(dualEntry));
            passphraseLock(PASSPHRASE, passphrase, dualEntry);

            // add signature
            ClearText signature = generateSignature();
            passphraseLock(SIGNATURE, signature, dualEntry);

            modified = true;
        }

        if (modified) {
            try {
                userLocalStore.save();
            } catch (IOException e) {
                throw new SystemException(e);
            }
        }

        try {
            state.setPassword(getPassphrase(dualEntry));
        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException("Bad user name or password to unlock the vault", ex);
        }

        state.setSignature(passphraseUnlock(SIGNATURE, dualEntry.getUser1()));

    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void openKeyData() throws GeneralSecurityException {
        if (keyDataOpened)
            return;
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");

        // need to check file backup
        try {
            keyDataStore.open();
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        keyDataOpened = true; // need to be here instead of at the end.

        boolean modified = false;

        // check signature
        Secret keyStoreSigSecret = getData(SIGNATURE, null);
        if (keyStoreSigSecret == null) {
            initKeys();
            addData(state.getSignature());
            modified = true;
        } else {
            String signatureFormat = state.getSignature().getProperty(SIGNATURE_FORMAT);
            String dataSignatureFormat = keyStoreSigSecret.getProperty(SIGNATURE_FORMAT);
            boolean restore = false;
            if (signatureFormat != null) {
                if (!signatureFormat.equals(dataSignatureFormat)
                        || !state.getSignature().getProperty(SIGNATURE).equals(keyStoreSigSecret.getProperty(SIGNATURE))) {
                    restore = true;
                }
            } else { // legacy signature vault file
                if (dataSignatureFormat != null) { // data signature exists vs. legacy format.
                    restore = true;
                }
            }

            if (restore) {
                try {
                    logger.warn("Restoring key data store to version " + state.getSignature().version());
                    keyDataStore.restore(state.getSignature().version());
                    keyStoreSigSecret = getData(SIGNATURE, null);
                } catch (IOException e) {
                    throw new GeneralSecurityException("Property signature do not match.  Local store vault signature does not match key store signature", e);
                }
            }

            modified = initKeys();
            String[] components = encryptedComponents(keyStoreSigSecret.getSecret());
            String keyAlias = components[2];
            String keyVersion = components[3];
            ClearText keyStoreSig = keyEncryption.unseal(keyStoreSigSecret, getKey(keyAlias, keyVersion));
            if (!Arrays.equals(state.getSignature().getBytes(), keyStoreSig.getBytes())) {
                try {
                    logger.warn("Restoring key data store to version " + state.getSignature().version());
                    keyDataStore.restore(state.getSignature().version());
                } catch (IOException e) {
                    throw new GeneralSecurityException("Local store vault signature does not match key store signature", e);
                }
            }
        }

        if (modified) {
            try {
                keyDataStore.save();
            } catch (IOException e) {
                throw new SystemException(e);
            }
        }
    }

    private boolean initKeys() throws GeneralSecurityException {
        boolean modified = false;
        if (getKey(MASTER_KEY_ALIAS, null) == null) {
            addKey(generateInternalKey(MASTER_KEY_ALIAS));
            modified = true;
        }

        if (getKey(AUTHORIZATION_KEY_ALIAS, null) == null) {
            addKey(generateInternalKey(AUTHORIZATION_KEY_ALIAS));
            modified = true;
        }

        if (getKey(ASYMMETRIC_KEY_ALIAS, null) == null) {
            addKey(generateInternalKeyPair(ASYMMETRIC_KEY_ALIAS));
            modified = true;
        }
        return modified;
    }

    private ClearText getKey(String keyAlias, String version) throws GeneralSecurityException {
        openKeyData();
        if (version != null) {
            String key = keyAlias + ":" + version;
            ClearText ct = state.getCachedKeys().get(key);
            if (ct != null)
                return ct;
        }
        Secret key = keyDataStore.getVault(KEY_VAULT).getSecret(keyAlias, version);
        if (key == null)
            return null;
        ClearText ct = pwd.unseal(key, getPassphrase(key));
        state.getCachedKeys().put(ct.alias() + ":" + ct.version(), ct);
        return ct;
    }

    private void addKey(ClearText ct) throws GeneralSecurityException {
        openKeyData();
        setCreationTimeVersion(ct);
        // need to get the latest passphrase
        ClearText passphrase = getPassphrase();
        Secret secret = pwd.seal(ct, passphrase);
        keyDataStore.getVault(KEY_VAULT).addSecret(secret);
        state.getCachedKeys().put(ct.alias() + ":" + ct.version(), ct);
    }

    private void addData(ClearText ct) throws GeneralSecurityException {
        openKeyData();
        setCreationTimeVersion(ct);
        ClearText key = getKey(MASTER_KEY_ALIAS, null);
        Secret secret = keyEncryption.seal(ct.alias(), ct, key);
        keyDataStore.getVault(DATA_VAULT).addSecret(secret);
    }

    private Secret getData(String alias, String version) throws GeneralSecurityException {
        openKeyData();
        return keyDataStore.getVault(DATA_VAULT).getSecret(alias, version);
    }

    // this function is different from the other addXXX function because we don't version users.
    private void addUser(Secret secret) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");
        userLocalStore.getVault(USER_VAULT).addSecret(secret);
    }

    private Secret getUser(String alias, String version) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");
        return userLocalStore.getVault(USER_VAULT).getSecret(alias, version);
    }

    private void removeUser(String alias, String version) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");
        userLocalStore.getVault(USER_VAULT).removeSecret(alias, version);
    }

    public Set<String> listUsers() throws GeneralSecurityException {
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");
        return userLocalStore.getVault(USER_VAULT).aliases();
    }

    private void addLocal(ClearText ct, ClearText passphrase) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");
        setCreationTimeVersion(ct);
        Secret secret = pwd.seal(ct, passphrase);
        userLocalStore.getVault(LOCAL_VAULT).addSecret(secret);
    }

    private Secret getLocal(String alias, String version) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw new GeneralSecurityException("user local store is not open.  Please call open() first");
        return userLocalStore.getVault(LOCAL_VAULT).getSecret(alias, version);
    }
}
