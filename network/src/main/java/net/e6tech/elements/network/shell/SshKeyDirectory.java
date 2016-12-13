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
package net.e6tech.elements.network.shell;



import net.e6tech.elements.common.logging.Logger;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by futeh.
 */
public class SshKeyDirectory {
    private static Logger logger = Logger.getLogger();
    private String directory;
    private Set<PublicKey> authorizedKeys = Collections.emptySet();
    private WatchService watcher;
    private Path path;
    private KeyFactory rsaKeyFactory;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public Set<PublicKey> getAuthorizedKeys() {
        return authorizedKeys;
    }

    public void setAuthorizedKeys(Set<PublicKey> authorizedKeys) {
        this.authorizedKeys = authorizedKeys;
    }

    public boolean contains(PublicKey publicKey) {
        PublicKey pubKey = null;
        try {
            pubKey = normalizePublicKey(publicKey);
        } catch (GeneralSecurityException e) {
            return false;
        }
        if (pubKey == null) return false;
        poll();
        return authorizedKeys.contains(pubKey);
    }

    protected void poll() {
        WatchKey key = watcher.poll();
        while (key != null) {
            try {
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // This key is registered only
                    // for ENTRY_CREATE events,
                    // but an OVERFLOW event can
                    // occur regardless if events
                    // are lost or discarded.
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    Path child = path.resolve(filename);
                    if (Files.exists(child) && child.getFileName().toString().endsWith(".pub")) {
                        try {
                            if (child.getFileName().toString().endsWith("pub")) {
                                loadSSHPubFile(child, authorizedKeys);
                            } else if (child.getFileName().toString().endsWith("pem")) {
                                loadPEMFile(child, authorizedKeys);
                            }
                        } catch (Exception e) {
                            logger.warn("Error reading " + child, e);
                        }
                    }
                }
            } finally {
                key.reset();
                key = watcher.poll();
            }
        }
    }

    public void init() throws Exception {
        rsaKeyFactory = KeyFactory.getInstance("RSA");
        if (directory == null) throw new IllegalArgumentException("null ssh key path");
        path = Paths.get(directory);

        if (Files.exists(path)) {
            Set<PublicKey> keys = new LinkedHashSet<>();
            if (Files.isRegularFile(path)) {
                try {
                    loadSSHPubFile(path, keys);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                authorizedKeys = keys;
            } else if(Files.isDirectory(path)) {
                Files.list(path).forEach((f) -> {
                    if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".pub")) {
                        try {
                            if (f.getFileName().toString().endsWith(".pub")) {
                                loadSSHPubFile(f, keys);
                            } else if (f.getFileName().toString().endsWith(".pem")) {
                                loadPEMFile(f, keys);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                authorizedKeys = keys;
                try {
                    watcher = FileSystems.getDefault().newWatchService();

                    path.register(watcher,
                            ENTRY_CREATE,
                            ENTRY_DELETE,
                            ENTRY_MODIFY);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            throw new IllegalArgumentException(directory + " is not a valid path.");
        }
    }

    private void loadSSHPubFile(Path f, Set<PublicKey> keys) {
        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(f);
            String line;
            while ((line = reader.readLine()) != null && line.trim().length() > 0) {
                try {
                    String[] components = line.split(" ");
                    byte[] bytes = Base64.getDecoder().decode(components[1]);
                    int index = 0;
                    BigInteger exponent = null;
                    BigInteger modulus = null;
                    for (int i = 0; i < 3; i++) {
                        int length = (((int) bytes[index++]) << 24
                                | ((int) bytes[index++]) << 16
                                | ((int) bytes[index++]) << 8
                                | (int) bytes[index++]);
                        byte[] content = new byte[length];
                        System.arraycopy(bytes, index, content, 0, length);
                        index += length;
                        if (i == 1) exponent = new BigInteger(content);
                        else if (i == 2) modulus = new BigInteger(content);
                    }
                    RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
                    keys.add(normalizePublicKey(rsaKeyFactory.generatePublic(keySpec)));
                } catch (Exception ex) {
                    logger.warn("Cannot add public key in file " + f + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            logger.warn("Cannot add public key in file " + f + ": " + ex.getMessage());
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (Exception ex) {
            }
        }
    }

    private PublicKey normalizePublicKey(PublicKey publicKey) throws GeneralSecurityException {
        PublicKey pubKey = null;
        if (publicKey instanceof RSAPublicKey) {
            RSAPublicKeySpec pub = new RSAPublicKeySpec(((RSAPublicKey) publicKey).getModulus(), ((RSAPublicKey) publicKey).getPublicExponent());
            pubKey = rsaKeyFactory.generatePublic(pub);
        } else {
            throw new GeneralSecurityException("Unsupported PublicKey format " + publicKey.getClass());
        }
        return pubKey;
    }

    private void loadPEMFile(Path f, Set<PublicKey> keys) {
        PEMParser pemParser = null;
        try {
            pemParser = new PEMParser(Files.newBufferedReader(f));
            Object o = pemParser.readObject();
            KeyPair keyPair;
            if (o instanceof KeyPair) {
                keyPair = new KeyPair(((KeyPair)o).getPublic(), null);
            } else if (o instanceof PublicKey) {
                keyPair = new KeyPair((PublicKey)o, null);
            } else if (o instanceof PEMKeyPair) {
                PEMKeyPair pemKeyPair = (PEMKeyPair)o;
                keyPair = convertPemKeyPair(pemKeyPair);
            } else if (o instanceof SubjectPublicKeyInfo) {
                PEMKeyPair pemKeyPair = new PEMKeyPair((SubjectPublicKeyInfo) o, null);
                keyPair = convertPemKeyPair(pemKeyPair);
            } else {
                throw new UnsupportedOperationException(String.format("Key type %s not supported.", o.getClass().getName()));
            }

            keys.add(normalizePublicKey(keyPair.getPublic()));
        } catch (Exception ex) {
            logger.warn("Cannot add public key in file " + f + ": " + ex.getMessage());
        } finally {
            if (pemParser != null) try {pemParser.close();} catch (Exception ex) {}
        }
    }

    KeyPair convertPemKeyPair(PEMKeyPair pemKeyPair) throws PEMException {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        return new KeyPair(converter.getPublicKey(pemKeyPair.getPublicKeyInfo()), null);
    }


}
