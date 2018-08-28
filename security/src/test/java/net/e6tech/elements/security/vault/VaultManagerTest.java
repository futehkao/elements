package net.e6tech.elements.security.vault;

import net.e6tech.elements.security.SymmetricCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class VaultManagerTest {

    DualEntry dualEntry;
    private VaultManager manager;

    @BeforeEach
    public void setup() throws Exception {
        String tmpVaultFilename = "/tmp/test-" + System.currentTimeMillis() + ".vault";
        File file = new File(tmpVaultFilename);
        if (file.exists()) file.delete();
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


}
