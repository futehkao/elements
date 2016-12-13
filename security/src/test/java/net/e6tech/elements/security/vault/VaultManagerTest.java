package net.e6tech.elements.security.vault;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * Created by futeh.
 */
public class VaultManagerTest {

    DualEntry dualEntry;
    private VaultManager manager;

    @Before
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
        manager.addSecretData("secret", clearText, dualEntry);
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
        String pubEncrypted = manager.encryptPublic(data.getBytes("UTF-8"));
        String privDecrypted = new String(manager.decryptPrivate(pubEncrypted), "UTF-8");

        String token = manager.authorize(dualEntry.getUser1());
        manager.renew(token);
    }

    @Test
    public void changePassword() throws Exception {
        ClearText clearText = new ClearText();
        clearText.setBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        clearText.setProperty("test", "test");
        manager.addSecretData("secret", clearText, dualEntry);

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
