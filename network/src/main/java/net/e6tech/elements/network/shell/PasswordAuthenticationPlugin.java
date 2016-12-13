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
import net.e6tech.elements.security.Password;
import org.crsh.auth.AuthenticationPlugin;
import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.PropertyDescriptor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by futeh.
 */
public class PasswordAuthenticationPlugin extends
        CRaSHPlugin<AuthenticationPlugin> implements
        AuthenticationPlugin<String> {

    public static final PropertyDescriptor<String> AUTHORIZED_PASSWORD_FILE = PropertyDescriptor.create(
            "auth.password.file",
            (String)null,
            "The path to the authorized key file");

    private static Logger logger = Logger.getLogger();
    private Map<String,String> passwords = new LinkedHashMap<>();
    private WatchService watcher;
    private Path path;

    @Override
    protected Iterable<PropertyDescriptor<?>> createConfigurationCapabilities() {
        return Arrays.<PropertyDescriptor<?>>asList(AUTHORIZED_PASSWORD_FILE);
    }

    public Class<String> getCredentialType() {
        return String.class;
    }

    @Override
    public AuthenticationPlugin getImplementation() {
        return this;
    }

    @Override
    public void init() {
        String passwordFile = getContext().getProperty(AUTHORIZED_PASSWORD_FILE);
        path = Paths.get(passwordFile);
        loadFile(path);
        try {
            watcher = FileSystems.getDefault().newWatchService();

            Paths.get(passwordFile).register(watcher,
                    ENTRY_CREATE,
                    ENTRY_DELETE,
                    ENTRY_MODIFY);
        } catch (Exception ex) {
            logger.warn(ex.getMessage());
        }
    }

    public String getName() {
        return "password";
    }

    public boolean hasPassword(String username) {
        poll();
        String onFile = passwords.get(username);
        return onFile != null;
    }

    public void savePassword(String username, String password) throws Exception {
        if (username == null) throw new IllegalArgumentException("user name cannot be null");
        String pwd = Password.getSaltedHash(password.toCharArray());
        passwords.put(username, pwd);
        BufferedWriter writer = null;
        try {
            writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"));
            for (Map.Entry<String, String> entry : passwords.entrySet()) {
                writer.write(entry.getKey());
                writer.write(" ");
                writer.write(entry.getValue());
                writer.newLine();
                writer.flush();
            }
        } finally {
            if (writer != null) try {
                writer.close();
            } catch (IOException e) {
            }
        }
    }

    public boolean authenticate(String username, String password)
            throws Exception {
        poll();
        if (passwords.size() == 0) return true;
        String onFile = passwords.get(username);
        if (onFile == null) return false;
        return Password.check(password.toCharArray(), onFile);
    }

    private void loadFile(Path path) {
        BufferedReader reader = null;
        Map<String, String> map = new LinkedHashMap<>();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.createFile(path);
            reader = Files.newBufferedReader(path);
            String line;
            while ((line = reader.readLine()) != null && line.trim().length() > 0) {
                try {
                    String[] components = line.split(" ");
                    map.put(components[0].trim(), components[1].trim());
                } catch (Exception ex) {
                    logger.warn("Cannot add public key in file " + path + ": " + ex.getMessage());
                }
            }
            passwords = map;
        } catch(Exception ex) {
            logger.warn("Cannot add public key in file " + path + ": " + ex.getMessage());
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (IOException e) {
            }
        }
    }

    public void poll() {
        WatchKey key = watcher.poll();
        while (key != null) {
            try {
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    } else if (kind == ENTRY_DELETE) {
                        passwords.clear();
                        continue;
                    }

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    Path file = path.resolve(filename);
                    if (Files.exists(file)) {
                        try {
                            loadFile(file);
                        } catch (Exception e) {
                            logger.warn("Error reading " + file, e);
                        }
                    }
                }
            } finally {
                key.reset();
                key = watcher.poll();
            }
        }
    }
}

