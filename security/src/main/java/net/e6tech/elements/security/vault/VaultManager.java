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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.AsymmetricCipher;
import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.RNG;
import net.e6tech.elements.security.SymmetricCipher;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import static net.e6tech.elements.security.vault.Constants.*;

/**
 * Created by futeh.
 */
public class VaultManager {
    private static Logger logger = Logger.getLogger();

    public static final String KEY_VAULT = "key-vault";
    public static final String USER_VAULT = "user-vault";
    public static final String DATA_VAULT = "data-vault";
    public static final String LOCAL_VAULT = "local-vault";

    private static final String CANNOT_FIND_USER = "Cannot find user ";
    private static final String USER = "USER ";
    private static final String IS_NOT_GUARDIAN = " is not a guardian";
    private static final String GUARDIA_HAS_BEEN_TEMPERED = " guardian property has been tempered";
    private static final GeneralSecurityException NOT_OPEN_EXCEPTION = new GeneralSecurityException("user local store is not open.  Please call open() first");
    private static final Function<Exception, GeneralSecurityException> BAD_USER_PASSWORD = ex -> new GeneralSecurityException("Bad user name or password to unlock the vault", ex);

    private SymmetricCipher symmetricCipher;
    private AsymmetricCipher asymmetricCipher;

    private VaultStore userLocalStore;     // file based user password protected
    private boolean userLocalOpened = false;
    private VaultStore keyDataStore;      // versioned secret password protected
    private boolean keyDataOpened = false;

    private PasswordProtected pwd = new PasswordProtected();
    private KeyProtected keyEncryption = new KeyProtected();

    private VaultManagerState state = new VaultManagerState();

    public VaultManager() {
        symmetricCipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        symmetricCipher.setBase64(false);
        asymmetricCipher = AsymmetricCipher.getInstance(AsymmetricCipher.ALGORITHM_RSA);
        keyDataStore = new FileStore();
        userLocalStore = keyDataStore;

        userLocalStore.manage(USER_VAULT, LOCAL_VAULT);
        keyDataStore.manage(KEY_VAULT, DATA_VAULT);
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

        ct.setBytes(encoded.getBytes(StandardCharsets.UTF_8));
        ct.setProperty(TYPE, KEY_PAIR_TYPE);
        ct.setProperty(ALGORITHM, asymmetricCipher.getAlgorithm());
        ct.setProperty(ClearText.PUBLIC_KEY_MOD, pub.getModulus().toString(16));
        ct.setProperty(ClearText.PUBLIC_KEY_EXP, pub.getPublicExponent().toString(16));
        ct.protect();
        return ct;
    }

    private ClearText generateSignature() {
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
        return pwd.unsealUserOrPassphrase(secret, state.getCurrentPassphrase());
    }

    // determining the format of the passphrase
    private ClearText passphraseForSecret(Secret secret) throws GeneralSecurityException {
        String[] components = secret.getSecret().split("\\$");
        Secret passphraseSecret;
        String keyVersion = null;
        if (components.length >= 5) {
            keyVersion = components[4];
        }
        if (keyVersion != null && keyVersion.trim().equals("0"))
            keyVersion = null;

        passphraseSecret = getLocal(PASSPHRASE, keyVersion);
        return pwd.unsealUserOrPassphrase(passphraseSecret, state.getCurrentPassphrase());
    }

    private ClearText passphraseClearText(char[] passphrase) {
        ClearText ct = new ClearText();
        ct.setBytes(new String(passphrase).getBytes(StandardCharsets.UTF_8));
        ct.alias(PASSPHRASE);
        ct.setProperty(TYPE, PASSPHRASE_TYPE);
        ct.protect();
        return ct;
    }

    private ClearText generateInternalKey(String alias) {
        SecretKey secretKey = symmetricCipher.generateKeySpec();
        ClearText ct = new ClearText();
        ct.setBytes(secretKey.getEncoded());
        ct.alias(alias);
        ct.setProperty(TYPE, KEY_TYPE);
        ct.setProperty(ALGORITHM, "AES");
        ct.protect();
        return ct;
    }

    private ZonedDateTime setCreationTimeVersion(ClearText ... clearTexts) {
        ZonedDateTime now = ZonedDateTime.now();
        String dateTime = now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        for (ClearText ct : clearTexts) {
            if (ct.version() == null) {
                ct.setProperty(CREATION_DATE_TIME, dateTime);
                ct.setProperty(CREATION_TIME, Long.toString(now.toInstant().toEpochMilli()));
                ct.version(Long.toString(now.toInstant().toEpochMilli()));
            }
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

    /**
     * Adding a public private key.  The key is treated as if it is regular data and store in the
     * data vault.  As always, data vault items are encrypted with m-key
     *
     * @param dualEntry dual entry containing authentication info for two users.
     * @param alias     alias of the key
     * @throws GeneralSecurityException general exception
     */
    public void addKeyPair(DualEntry dualEntry, String alias) throws GeneralSecurityException {
        checkAccess(dualEntry);
        ClearText ct = generateInternalKeyPair(alias);
        addData(ct);
    }

    /**
     * Adding secret to the data vault.  It will be encrypted by m-key when stored.
     *
     * @param dualEntry dual entry containing authentication info for two users.
     * @param alias     alias of the secret data
     * @param ct        clear text of the secret
     * @throws GeneralSecurityException general exception
     */
    public void addSecretData(DualEntry dualEntry, String alias, ClearText ct) throws GeneralSecurityException {
        checkAccess(dualEntry);
        ct.alias(alias);
        ct.setProperty(TYPE, SECRET_TYPE);
        ct.setProtectedProperty(TYPE, SECRET_TYPE);
        addData(ct);
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
        return generateKey(dualEntry, false);
    }

    public String generateKey(DualEntry dualEntry, boolean asymmetricKey) throws GeneralSecurityException {
        return asymmetricKey ? generateAsymmetricKey(dualEntry) : generateSymmetricKey(dualEntry);
    }

    private String generateSymmetricKey(DualEntry dualEntry) throws GeneralSecurityException {
        byte[] plain = symmetricCipher.generateKeySpec().getEncoded();
        checkAccess(dualEntry);
        return internalEncrypt(MASTER_KEY_ALIAS, null, plain);
    }

    private String generateAsymmetricKey(DualEntry dualEntry) throws GeneralSecurityException {
        byte[] plain = generateEncodedAsymmetricKey(asymmetricCipher);
        checkAccess(dualEntry);
        return internalEncrypt(MASTER_KEY_ALIAS, null, plain);
    }

    /**
     * Generate an asymmetric key pair, return encoded key components
     * @param asymmetricCipher the AsymmetricCipher
     * @return the generated encoded key pair
     * @throws GeneralSecurityException general exception
     */
    public static byte[] generateEncodedAsymmetricKey(AsymmetricCipher asymmetricCipher) throws GeneralSecurityException {
        KeyPair keyPair = asymmetricCipher.generateKeySpec();
        KeyFactory fact = asymmetricCipher.getKeyFactory();
        RSAPublicKeySpec pub = fact.getKeySpec(keyPair.getPublic(), RSAPublicKeySpec.class);
        RSAPrivateKeySpec priv = fact.getKeySpec(keyPair.getPrivate(), RSAPrivateKeySpec.class);
        String encoded = AsymmetricCipher.ALGORITHM_RSA
                + "$M=" + priv.getModulus().toString(16)         // M: Modulus
                + "$D=" + priv.getPrivateExponent().toString(16) // D: Decryption
                + "$E=" + pub.getPublicExponent().toString(16);  // E: Encryption
        return encoded.getBytes();
    }

    /**
     * Imports a plan key.  It's stored encrypted by latest master ky.
     * @param dualEntry dual entry
     * @param plainKey plain key
     * @param iv initialization vector.  If null, randomly generated
     * @return encrypted key
     */
    public String importKey(DualEntry dualEntry, String plainKey, String iv) throws GeneralSecurityException {
        return importKey(dualEntry, plainKey, iv, false);
    }

    /**
     * Imports a plan key.  It's stored encrypted by latest master ky.
     * @param dualEntry dual entry
     * @param plainKey plain key
     * @param iv initialization vector.  If null, randomly generated
     * @param version  key version.  If null, randomly generated
     * @return encrypted key
     */
    public String importKey(DualEntry dualEntry, String plainKey, String iv, String version) throws GeneralSecurityException {
        return importKey(dualEntry, plainKey, iv, false, version);
    }

    /**
     * Imports a plan key.  It's stored encrypted by latest master ky.
     * @param dualEntry dual entry
     * @param plainKey plain key
     * @param iv initialization vector.  If null, randomly generated
     * @param asymmetricKey  whether the key is asymmetric key
     * @return encrypted key
     */
    public String importKey(DualEntry dualEntry, String plainKey, String iv, boolean asymmetricKey) throws GeneralSecurityException {
        return importKey(dualEntry, plainKey, iv, asymmetricKey, null);
    }

    /**
     * Imports a plan key.  It's stored encrypted by latest master ky.
     * @param dualEntry dual entry
     * @param plainKey plain key
     * @param iv initialization vector.  If null, randomly generated
     * @param asymmetricKey  whether the key is asymmetric key
     * @param version  key version.  If null, randomly generated
     * @return encrypted key
     */
    public String importKey(DualEntry dualEntry, String plainKey, String iv, boolean asymmetricKey, String version) throws GeneralSecurityException {
        return asymmetricKey ?
                importAsymmetricKey(dualEntry, plainKey, iv, version)
                : importSymmetricKey(dualEntry, plainKey, iv, version);
    }

    /**
     *
     * @param dualEntry dual entry
     * @param plainData the plain data to be imported
     * @param iv  initialization vector.  If null, randomly generated
     * @return encrypted data
     */
    public String importData(DualEntry dualEntry, String plainData, String iv) throws GeneralSecurityException {
        return importData(dualEntry, plainData, iv, null);
    }

    /**
     *
     * @param dualEntry dual entry
     * @param version version of the master key.  If null, use the latest version.
     * @param plainData the plain data to be imported
     * @param iv  initialization vector.  If null, randomly generated
     * @return encrypted data
     */
    public String importData(DualEntry dualEntry, String plainData, String iv, String version) throws GeneralSecurityException {
        byte[] plain = symmetricCipher.toBytes(plainData);
        checkAccess(dualEntry);
        return internalEncrypt(MASTER_KEY_ALIAS, version, plain, iv);
    }

    /**
     *
     * @param dualEntry dual entry
     * @param version version of the master key.  If null, use the latest version.
     * @param plainKey the plain key to be imported
     * @param iv  initialization vector.  If null, randomly generated
     * @param version  key version.  If null, randomly generated
     * @return encrypted key
     */
    private String importAsymmetricKey(DualEntry dualEntry, String plainKey, String iv, String version) throws GeneralSecurityException {
        byte[] plain = symmetricCipher.toBytes(plainKey);
        String[] keyValuesParts = new String(plain).split("\\$");
        if (keyValuesParts.length != 4)
            throw new GeneralSecurityException("Invalid key format: expecting the key to be 4 components separated by \\$");

        // try to create private and public key
        try {
            RSAPrivateKeySpec privateKeySpec = new RSAPrivateKeySpec(new BigInteger(keyValuesParts[1].substring(2), 16), new BigInteger(keyValuesParts[2].substring(2), 16));
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(new BigInteger(keyValuesParts[1].substring(2), 16), new BigInteger(keyValuesParts[3].substring(2), 16));

            asymmetricCipher.getKeyFactory().generatePrivate(privateKeySpec);
            asymmetricCipher.getKeyFactory().generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new SystemException("Can't create private and public key", e);
        }

        checkAccess(dualEntry);

        return internalEncrypt(MASTER_KEY_ALIAS, version, plain, iv);
    }

    /**
     *
     * @param dualEntry dual entry
     * @param version version of the master key.  If null, use the latest version.
     * @param plainKey the plain key to be imported
     * @param iv  initialization vector.  If null, randomly generated
     * @param version  key version.  If null, randomly generated
     * @return encrypted key
     */
    private String importSymmetricKey(DualEntry dualEntry, String plainKey, String iv, String version) throws GeneralSecurityException {
        byte[] plain = symmetricCipher.toBytes(plainKey);
        byte[] plain2 = symmetricCipher.generateKeySpec().getEncoded();
        String iv2 = symmetricCipher.generateIV();
        if (plain2.length != plain.length)
            throw new GeneralSecurityException("Invalid key length: expecting the key to be " + plain2.length + " bytes.");
        if (iv2.length() != plain.length) {
            int ivLength = symmetricCipher.toBytes(iv2).length;
            throw new GeneralSecurityException("Invalid IV length: expecting the IV to be " + ivLength + " bytes.");
        }
        checkAccess(dualEntry);
        return internalEncrypt(MASTER_KEY_ALIAS, version, plain, iv);
    }

    // encrypt data with key. key is encrypted with master key.
    public String encrypt(Credential credential, String key, byte[] data, String iv) throws GeneralSecurityException {
        checkAccess(credential);
        byte[] keyBytes = internalDecrypt(key);
        SecretKey secretKey = symmetricCipher.getKeySpec(keyBytes);
        return symmetricCipher.encrypt(secretKey, data, iv);
    }

    // for decrypt data
    public byte[] decrypt(Credential credential, String key, String secret, String iv) throws GeneralSecurityException {
        checkAccess(credential);
        byte[] keyBytes = internalDecrypt(key);
        SecretKey secretKey = symmetricCipher.getKeySpec(keyBytes);
        return symmetricCipher.decrypt(secretKey, secret, iv);
    }

    // for decrypt keys
    public byte[] decrypt(Credential credential, String secret) throws GeneralSecurityException {
        checkAccess(credential);
        return internalDecrypt(secret);
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

    private void newUser(Credential credential, byte[] component, String group) throws GeneralSecurityException {
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
        newUser(newUser, ct1.getBytes(), ct1.getProperty(GUARDIAN));
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

    // Encrypting ClearText with dualEntry's associated password and store it in local, ie file.
    public void passphraseLock(DualEntry dualEntry, String alias, ClearText ct) throws GeneralSecurityException {
        ct.alias(alias);
        ClearText passphrase;
        if (alias.equals(PASSPHRASE)) {  // this happens when we change passphrase
            passphrase = passphraseClearText(getUserComponents(dualEntry));
            passphrase.version("0");
        } else {
            Secret secret = getLocal(PASSPHRASE, null);
            passphrase = pwd.unsealUserOrPassphrase(secret, getUserComponents(dualEntry));
        }
        addLocal(ct, passphrase);
    }

    public ClearText passphraseUnlock(Credential credential, String alias) throws GeneralSecurityException {
        checkAccess(credential);
        Secret secret = getLocal(alias, null);
        if (secret == null)
            return null;
        return pwd.unseal(secret, getPassphrase());
    }

    public void newMasterKey(DualEntry dualEntry) throws GeneralSecurityException {
        checkAccess(dualEntry);
        addKey(generateInternalKey(MASTER_KEY_ALIAS));

        Vault dataVault = keyDataStore.getVault(DATA_VAULT);
        Set<String> aliases = dataVault.aliases();
        for (String alias : aliases) {
            Set<Long> versions = dataVault.versions(alias);
            for (Long version : versions) {
                ClearText ct = getSecretData(dualEntry.getUser1(), alias, "" + version);
                if (ct != null) // could be null if a newly added data was not saved and then was removed from cache
                    addSecretData(dualEntry, alias, ct);
            }
        }
    }

    private void checkAccess(Credential credential) throws GeneralSecurityException {
        ClearText ct = getUser(credential);
        if (ct == null)
            throw new GeneralSecurityException(CANNOT_FIND_USER + credential.getUser());
        if (!(ct.getProperty(GUARDIAN).equals(GROUP_1) || ct.getProperty(GUARDIAN).equals(GROUP_2)))
            throw new GeneralSecurityException(USER + ct.getProperty(USERNAME) + IS_NOT_GUARDIAN);

        if (!ct.getProperty(GUARDIAN).equals(ct.getProtectedProperty(GUARDIAN))) {
            throw new GeneralSecurityException(USER + ct.getProperty(USERNAME) + GUARDIA_HAS_BEEN_TEMPERED);
        }
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void checkAccess(DualEntry dualEntry) throws GeneralSecurityException {
        ClearText ct1 = getUser(dualEntry.getUser1());
        ClearText ct2 = getUser(dualEntry.getUser2());

        if (ct1 == null)
            throw new GeneralSecurityException("Bad user name " + dualEntry.getUser1());
        if (ct2 == null)
            throw new GeneralSecurityException("Bad user name " + dualEntry.getUser2());

        if (ct1.getProperty(GUARDIAN) == null ||
                !(ct1.getProperty(GUARDIAN).equals(GROUP_1) || ct1.getProperty(GUARDIAN).equals(GROUP_2))) {
            throw new SystemException(USER + ct1.getProperty(USERNAME) + IS_NOT_GUARDIAN);
        }
        if (ct2.getProperty(GUARDIAN) == null ||
                !(ct2.getProperty(GUARDIAN).equals(GROUP_1) || ct2.getProperty(GUARDIAN).equals(GROUP_2))) {
            throw new GeneralSecurityException(USER + ct2.getProperty(USERNAME) + IS_NOT_GUARDIAN);
        }
        if (ct1.getProperty(GUARDIAN).equals(ct2.getProperty(GUARDIAN))) {
            throw new SystemException("User1 " + ct1.getProperty(USERNAME) + " and user 2 "
                    + ct2.getProperty(USERNAME) + " cannot be in the same group");
        }

        if (!ct1.getProperty(GUARDIAN).equals(ct1.getProtectedProperty(GUARDIAN))) {
            throw new GeneralSecurityException(USER + ct1.getProperty(USERNAME) + GUARDIA_HAS_BEEN_TEMPERED);
        }

        if (!ct2.getProperty(GUARDIAN).equals(ct2.getProtectedProperty(GUARDIAN))) {
            throw new GeneralSecurityException(USER + ct2.getProperty(USERNAME) + GUARDIA_HAS_BEEN_TEMPERED);
        }
    }

    private char[] getUserComponents(DualEntry dualEntry) throws GeneralSecurityException {
        ClearText ct1 = getUser(dualEntry.getUser1());
        if (ct1 == null)
            throw new GeneralSecurityException(CANNOT_FIND_USER + dualEntry.getUser1());
        ClearText ct2 = getUser(dualEntry.getUser2());
        if (ct2 == null)
            throw new GeneralSecurityException(CANNOT_FIND_USER + dualEntry.getUser2());

        checkAccess(dualEntry);
        byte[] comp1 = ct1.getBytes();
        byte[] comp2 = ct2.getBytes();
        return xor(comp1, comp2);
    }

    private char[] xor(byte[] comp1, byte[] comp2) {
        byte[] pwdBytes = new byte[comp1.length];
        for (int i = 0; i < comp1.length; i++)
            pwdBytes[i] = (byte) ((comp1[i] ^ comp2[i]) & 0x000000ff);
        return Hex.toString(pwdBytes).toCharArray();
    }

    public ClearText getSignature() {
        return state.getSignature();
    }

    /**
     * Change secret password.  We need to take the following steps:
     * 1. check user access.
     * 2. generate new components for user 1 and 2.
     * 3. remove all other users
     * 4. re-encrypt everything in local vault
     * 5. re-encrypt keys in the database.  We need to iterate through all version for each alias.
     *
     * @param dualEntry dual entry containing authentication info for two users.
     * @throws GeneralSecurityException general security exception
     */
    @SuppressWarnings("squid:S3776")
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

        ClearText newSignature = generateSignature();
        ClearText newPassphrase;

        try {
            newPassphrase = userLocalPassphraseChange(dualEntry, newSignature);
        } catch (Exception ex) {
            // restore file
            state = oldState;
            throw new GeneralSecurityException(ex);
        }

        // save userLocalStore
        try {
            userLocalStore.save();
        } catch (Exception ex) {
            // restore file
            state = oldState;
            try {
                userLocalStore.restore(currentVersion);
            } catch (IOException io) {
                throw new GeneralSecurityException(io);
            }
            throw new GeneralSecurityException(ex);
        }

        try {
            keyDataPassphraseChange(newSignature, newPassphrase);
        } catch (Exception ex) {
            // restore file
            state = oldState;
            try {
                userLocalStore.restore(currentVersion);
            } catch (IOException io) {
                throw new GeneralSecurityException(io);
            }
            throw new GeneralSecurityException(ex);
        }

        try {
            keyDataStore.save();
        } catch (Exception ex) {
            try {
                keyDataStore.restore(currentVersion);
            } catch (IOException io) {
                throw new GeneralSecurityException(io);
            }
            state = oldState;
            try {
                userLocalStore.restore(currentVersion);
            } catch (IOException io) {
                throw new GeneralSecurityException(io);
            }
            throw new GeneralSecurityException(ex);
        }
    }

    private ClearText userLocalPassphraseChange(DualEntry dualEntry, ClearText newSignature) throws GeneralSecurityException {
        byte[] comp1 = RNG.generateSeed(16);
        byte[] comp2 = RNG.generateSeed(16);

        // get the new passphrase clear text
        // newRandomPassphrase needs to be after addUser so that getUserComponents picks up comp1 xor comp2.
        char[] newRandomPassphrase = xor(comp1, comp2);
        ClearText newPassphrase = passphraseClearText(newRandomPassphrase);
        setCreationTimeVersion(newPassphrase, newSignature);
        try {
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
                    ClearText oldPassphrase = passphraseForSecret(secret);
                    ClearText ct = pwd.unseal(secret, oldPassphrase);
                    addLocal(ct, newPassphrase);
                }
            }

            // update user1 and user2
            ClearText ct1 = getUser(dualEntry.getUser1());
            ClearText ct2 = getUser(dualEntry.getUser2());
            if (ct1 == null || ct2 == null)
                throw new IllegalStateException(); // not possible.
            ct1.setBytes(comp1);
            ct2.setBytes(comp2);
            addUser(pwd.sealUser(ct1, dualEntry.getUser1().getPassword()));
            addUser(pwd.sealUser(ct2, dualEntry.getUser2().getPassword()));

            // Add the new passphrase encrypted with its own passphrase.
            // This has to be done before we proceed to convert database's key-vault because
            // the keys are encrypted with old passphrase and the old passphrase has been
            // just re-encrypted with newPassphrase so that newPassphrase has to be in the vault file.
            passphraseLock(dualEntry, PASSPHRASE, newPassphrase);
            // Make the newPassphrase the current passphrase.  Remember, newPassphrase is made up of user1 and user2 getBytes.
            state.setCurrentPassphrase(getUserComponents(dualEntry));

            // add new signature to local vault, but we don't chante state's signature
            // because when we re-encrypt key vault, it will check against state's signature.
            addLocal(newSignature, newPassphrase);

            // remove all other users
            aliases = listUsers();
            for (String alias : aliases) {
                if (!dualEntry.getUser1().getUser().equals(alias)
                        && !dualEntry.getUser2().getUser().equals(alias))
                    removeUser(alias, null);
            }
        } catch (Exception ex) {
            throw new GeneralSecurityException(ex);
        }
        return newPassphrase;
    }

    private void keyDataPassphraseChange(ClearText newSignature, ClearText newPassphrase) throws GeneralSecurityException {
        // go to the keyDataStore and re-encrypt it

        // re-encrypt database's key-vault, the signature, from state's signature, is still
        // the old one so that it doesn't trigger restore from the key vault
        Vault keyVault = keyDataStore.getVault(KEY_VAULT);
        Set<String> aliases = keyVault.aliases();
        for (String alias : aliases) {
            Set<Long> versions = keyVault.versions(alias);
            for (Long version : versions) {
                ClearText ct = getKey(alias, "" + version);
                Secret secret = pwd.seal(ct, newPassphrase);
                keyVault.addSecret(secret);
            }
        }

        // delete database signature and add new signature.
        keyDataStore.getVault(DATA_VAULT).removeSecret(SIGNATURE, null);
        addData(newSignature);
        state.setSignature(newSignature);
    }

    public void restore(DualEntry dualEntry, String version) throws GeneralSecurityException {
        String currentVersion = state.getSignature().version();
        try {
            userLocalStore.backup(currentVersion);
            keyDataStore.backup(currentVersion);
        } catch (IOException ex) {
            throw new GeneralSecurityException(ex);
        }

        try {
            keyDataStore.restore(version);
            userLocalStore.restore(version);
        } catch (IOException io) {
            throw new GeneralSecurityException(io);
        }

        try {
            state.setCurrentPassphrase(getUserComponents(dualEntry));
        } catch (GeneralSecurityException ex) {
            throw BAD_USER_PASSWORD.apply(ex);
        }

        state.setSignature(passphraseUnlock(dualEntry.getUser1(), SIGNATURE));
    }

    private String internalEncrypt(String keyAlias, String version, byte[] plain) throws GeneralSecurityException {
        return internalEncrypt(keyAlias, version, plain, null);
    }

    @SuppressWarnings("squid:S00100")
    private String internalEncrypt(String keyAlias, String version, byte[] plain, String iv) throws GeneralSecurityException {
        ClearText ct = getKey(keyAlias, version);
        if (ct == null)
            throw new GeneralSecurityException("No key for keyAlias=" + keyAlias);

        if (iv == null)
            iv = symmetricCipher.generateIV();
        String encrypted = symmetricCipher.encrypt(ct.asSecretKey(), plain, iv);
        return iv + "$" + encrypted + "$" + keyAlias + "$" + ct.version(); // iv and enc
    }

    public byte[] internalDecrypt(String encoded) throws GeneralSecurityException {
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
            newUser(dualEntry.getUser1(), comp1, GROUP_1);
            newUser(dualEntry.getUser2(), comp2, GROUP_2);
            char[] newRandomPassphrase = getUserComponents(dualEntry);

            try {
                state.setCurrentPassphrase(getUserComponents(dualEntry));
            } catch (GeneralSecurityException ex) {
                throw BAD_USER_PASSWORD.apply(ex);
            }

            // add passphrase
            ClearText passphrase = passphraseClearText(newRandomPassphrase);
            passphraseLock(dualEntry, PASSPHRASE, passphrase);

            // add signature
            ClearText signature = generateSignature();
            passphraseLock(dualEntry, SIGNATURE, signature);

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
            state.setCurrentPassphrase(getUserComponents(dualEntry));
        } catch (GeneralSecurityException ex) {
            throw BAD_USER_PASSWORD.apply(ex);
        }

        state.setSignature(passphraseUnlock(dualEntry.getUser1(), SIGNATURE));
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S3776"})
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
                    if (logger.isWarnEnabled())
                        logger.warn("Restoring key data store to version {}", state.getSignature().version());
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
                    if (logger.isWarnEnabled())
                        logger.warn("Restoring key data store to version {}", state.getSignature().version());
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

    public ClearText getKey(DualEntry dualEntry, String keyAlias, String version) throws GeneralSecurityException {
        checkAccess(dualEntry);
        return getKey(keyAlias, version);
    }

    // This is declared to be protected to allow a subclass to retrieve the key from a different place, e.g. an application key HSM
    protected ClearText getKey(String keyAlias, String version) throws GeneralSecurityException {
        openKeyData();
        Secret key = keyDataStore.getVault(KEY_VAULT).getSecret(keyAlias, version);
        if (key == null)
            return null;
        return pwd.unseal(key, passphraseForSecret(key));
    }

    // This is declared to be protected to allow a subclass to retrieve the key from a different place, e.g. an application key HSM
    protected void addKey(ClearText ct) throws GeneralSecurityException {
        openKeyData();
        setCreationTimeVersion(ct);
        // need to get the latest passphrase
        ClearText passphrase = getPassphrase();
        Secret secret = pwd.seal(ct, passphrase);
        keyDataStore.getVault(KEY_VAULT).addSecret(secret);
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
            throw NOT_OPEN_EXCEPTION;
        userLocalStore.getVault(USER_VAULT).addSecret(secret);
    }

    private Secret getUser(String alias, String version) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw NOT_OPEN_EXCEPTION;
        return userLocalStore.getVault(USER_VAULT).getSecret(alias, version);
    }

    private void removeUser(String alias, String version) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw NOT_OPEN_EXCEPTION;
        userLocalStore.getVault(USER_VAULT).removeSecret(alias, version);
    }

    public Set<String> listUsers() throws GeneralSecurityException {
        if (!userLocalOpened)
            throw NOT_OPEN_EXCEPTION;
        return userLocalStore.getVault(USER_VAULT).aliases();
    }

    private void addLocal(ClearText ct, ClearText passphrase) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw NOT_OPEN_EXCEPTION;
        setCreationTimeVersion(ct);
        Secret secret = pwd.seal(ct, passphrase);
        userLocalStore.getVault(LOCAL_VAULT).addSecret(secret);
    }

    private Secret getLocal(String alias, String version) throws GeneralSecurityException {
        if (!userLocalOpened)
            throw NOT_OPEN_EXCEPTION;
        return userLocalStore.getVault(LOCAL_VAULT).getSecret(alias, version);
    }
}
