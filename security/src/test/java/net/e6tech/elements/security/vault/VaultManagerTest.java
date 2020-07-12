package net.e6tech.elements.security.vault;

import net.e6tech.elements.security.SymmetricCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by futeh.
 */
public class VaultManagerTest {

    DualEntry dualEntry;
    private VaultManager manager;

    @BeforeEach
    public void setup() throws Exception {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("/tmp"), "test-*.{vault}")) {
            for (Path entry: stream) {
                entry.toFile().delete();
            }
        }

        String tmpVaultFilename = "/tmp/test-" + System.currentTimeMillis() + ".vault";
        File file = new File(tmpVaultFilename);
        if (file.exists())
            file.delete();
        manager = new VaultManager();
        ((FileStore) manager.getUserLocalStore()).setFileName(tmpVaultFilename);
        dualEntry = new DualEntry("user1", "password1".toCharArray(), "user2", "password2".toCharArray());
        manager.open(dualEntry);
    }

    protected void reopen() throws Exception {
        String fileStoreName = ((FileStore) manager.getUserLocalStore()).getFileName();
        manager = new VaultManager();
        ((FileStore) manager.getUserLocalStore()).setFileName(fileStoreName);
        manager.open(dualEntry);
    }

    @Test
    public void basicTest() throws Exception {
        ClearText clearText = new ClearText();
        clearText.setBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        clearText.setProperty("test", "test");
        manager.addSecretData(dualEntry, "secret", clearText);
        clearText = manager.getSecretData(dualEntry.getUser1(), "secret");
        manager.newMasterKey(dualEntry);
        String str = manager.getUserLocalStore().writeString();
        System.out.println(str);
        // save
        manager.save();

        reopen();

        clearText = manager.getSecretData(dualEntry.getUser1(), "secret");
        clearText.getProperty("test");

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i ++) builder.append((char)('a' + (i % 26)));
        String data = builder.toString();
        String pubEncrypted = manager.encryptPublic(data.getBytes(StandardCharsets.UTF_8));
        String privDecrypted = new String(manager.decryptPrivate(pubEncrypted), StandardCharsets.UTF_8);
    }

    @Test
    public void genKey() throws Exception {
        SymmetricCipher cipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        String key = manager.generateKey(dualEntry);
        testEncrypt(key);
    }

    @Test
    public void importKey() throws Exception {
        SymmetricCipher cipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        byte[] plain = cipher.generateKeySpec().getEncoded();
        String iv = cipher.generateIV();
        String key = manager.importKey(dualEntry, cipher.toString(plain), iv);
        testEncrypt(key);
    }

    @Test
    public void importKeyBadPassword() throws Exception {
        SymmetricCipher cipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        byte[] plain = cipher.generateKeySpec().getEncoded();
        String iv = cipher.generateIV();
        DualEntry de = new DualEntry("user1", "password".toCharArray(), "user2", "password2".toCharArray());
        assertThrows(Exception.class, () -> manager.importKey(de, cipher.toString(plain), iv));
    }

    @Test
    public void importKeyBadUser() throws Exception {
        SymmetricCipher cipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        byte[] plain = cipher.generateKeySpec().getEncoded();
        String iv = cipher.generateIV();
        DualEntry de = new DualEntry("user3", "password".toCharArray(), "user2", "password2".toCharArray());
        assertThrows(Exception.class, () -> manager.importKey(de, cipher.toString(plain), iv));
    }

    private void testEncrypt(String key) throws Exception {
        SymmetricCipher cipher = SymmetricCipher.getInstance(SymmetricCipher.ALGORITHM_AES);
        String iv = cipher.generateIV();
        String text = "Hello World!";
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        String encrypted = manager.encrypt(dualEntry.getUser1(), key, data, iv);
        byte[] decrypted = manager.decrypt(dualEntry.getUser1(), key, encrypted, iv);
        assertTrue(new String(decrypted, StandardCharsets.UTF_8).equals(text));
    }

    @Test
    public void changePassword() throws Exception {
        ClearText clearText = new ClearText();
        clearText.setBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        clearText.setProperty("test", "test");
        manager.addSecretData(dualEntry,"secret", clearText);

        char[] oldPwd = "password1".toCharArray();
        char[] newPwd = "newpassword".toCharArray();
        manager.changePassword("user1", oldPwd, newPwd);
        manager.save();

        dualEntry.setUser1(new Credential("user1", newPwd));

        reopen();
        ClearText ct1 = manager.getSecretData(dualEntry.getUser1(), "secret");
        assertTrue(Arrays.equals(ct1.getBytes(), clearText.getBytes()));
    }

    @Test
    void changeMasterKey() throws Exception {
        String version = manager.getSignature().version();
        manager.getKeyDataStore().backup(version);
        ClearText sig = manager.getSecretData(dualEntry.getUser1(), Constants.SIGNATURE);
        manager.newMasterKey(dualEntry);
        // check signature before and after master change.  They have to be the same
        ClearText sig2 = manager.getSecretData(dualEntry.getUser1(), Constants.SIGNATURE);
        assertEquals(sig.version(), sig2.version());
        assertEquals(sig.toText(), sig2.toText());
    }

    @Test
    void changePassphrase() throws Exception {
        ClearText m1 = manager.getKey(Constants.MASTER_KEY_ALIAS, null);
        manager.changePassphrase(dualEntry);
        ClearText m2 = manager.getKey(Constants.MASTER_KEY_ALIAS, null);
        assertTrue(Arrays.equals(m1.getBytes(), m2.getBytes()));
    }

}
