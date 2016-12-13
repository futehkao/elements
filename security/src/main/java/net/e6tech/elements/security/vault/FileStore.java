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

import com.fasterxml.jackson.core.JsonProcessingException;
import net.e6tech.elements.common.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh on 1/4/16.
 */
public class FileStore implements VaultStore {
    private static Logger logger = Logger.getLogger();

    private Map<String, VaultImpl> vaults = new LinkedHashMap<>();
    private Set<String> managedVaults = new HashSet<>();
    private String fileName;
    private boolean backup = false;

    public FileStore() {}

    public FileStore(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public VaultStore manage(String ... vaultNames) {
        if (vaultNames == null) return this;
        for (String vaultName : vaultNames) {
            VaultImpl vault = vaults.get(vaultName);
            if (vault == null) {
                vault = new VaultImpl();
                vaults.put(vaultName, vault);
            }
            managedVaults.add(vaultName);
        }
        return this;
    }

    @Override
    public VaultStore unmanage(String vaultName) {
        vaults.remove(vaultName);
        managedVaults.remove(vaultName);
        return this;
    }

    public Vault getVault(String vaultName) {
        Vault vault = vaults.get(vaultName);
        if (vault == null && managedVaults.contains(vaultName)) {
            manage(vaultName);
            return (Vault) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {Vault.class}, new VaultInvocationHandler(vaultName));
        }
        if (vault == null) return null;
        return (Vault) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {Vault.class}, new VaultInvocationHandler(vaultName));
    }

    public void backup(String version) throws IOException {
        copy(true, version);
    }

    public void restore(String version) throws IOException {
        copy(false, version);
        open();
    }

    protected void copy(boolean backup, String version) throws IOException {
        String backupFile;
        int index = fileName.lastIndexOf('.');
        if (index > 0) {
            String extension = fileName.substring(index);
            backupFile = fileName.substring(0, index) + "_" + version + extension;
        } else {
            backupFile = fileName + "_" + version;
        }

        if (backup) {
            File file = new File(fileName);
            if (!file.exists()) {
                throw new IOException("Vault file does not exist: " + fileName);
            }
            if (!Files.exists(Paths.get(backupFile))) Files.copy(Paths.get(fileName), Paths.get(backupFile));
        } else {
            File file = new File(backupFile);
            if (!file.exists()) {
                throw new IOException("Backup vault file does not exist: " + fileName);
            }
            Files.copy(Paths.get(backupFile), Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void save() throws IOException {
        if (fileName == null) throw new IOException("null fileName");

        if (!backup) {
            File file = new File(fileName);
            if (file.exists()) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss");
                String backup;
                int index = fileName.lastIndexOf('.');
                if (index > 0) {
                    String extension = fileName.substring(index);
                    backup = fileName.substring(0, index) + "-" + format.format(new Date()) + extension;
                } else {
                    backup = fileName + "-" + format.format(new Date());
                }
                Files.copy(Paths.get(fileName), Paths.get(backup));
            }
            backup = true;
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(fileName), new VaultFormat(vaults));
    }

    @Override
    public void open() throws IOException {
        if (fileName == null) throw new IOException("null fileName");

        logger.info( "Opening file vault " + fileName);

        File file = new File(fileName);
        if (!file.exists()) {
            for (String v : managedVaults) {
                vaults.put(v, new VaultImpl());

            }
            return;
        }
        VaultFormat format = mapper.readValue(new File(fileName), VaultFormat.class);
        format.checkVersion();

        for (String v : managedVaults) {
            VaultImpl impl = format.getVaults().get(v);
            if (impl != null) vaults.put(v, impl);
        }
    }

    @Override
    public void close() throws IOException {

    }

    public Secret loadSecret(String vaultName, String alias, String version) throws IOException {
        return null;
    }

    public String writeString() throws IOException {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new VaultFormat(vaults));
        } catch (JsonProcessingException e) {
            throw new IOException(e);
        }
    }

    private class VaultInvocationHandler implements InvocationHandler {
        String vaultName;

        public VaultInvocationHandler(String vaultName) {
            this.vaultName = vaultName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            VaultImpl impl = vaults.get(vaultName);
            Object ret =  method.invoke(impl, args);
            if (method.getName().equals("getSecretData") && ret == null) {
                ret = loadSecret(vaultName, (String) args[0], (String) args[1]);
            }
            return ret;
        }
    }
}
