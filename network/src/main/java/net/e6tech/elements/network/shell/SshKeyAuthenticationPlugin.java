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
import org.crsh.auth.AuthenticationPlugin;
import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.PropertyDescriptor;

import java.security.PublicKey;
import java.util.Arrays;

/**
 * Created by futeh.
 */
public class SshKeyAuthenticationPlugin extends CRaSHPlugin<SshKeyAuthenticationPlugin> implements AuthenticationPlugin<PublicKey> {

    private static Logger logger = Logger.getLogger();

    /** The SSH authorized key path. */
    public static final PropertyDescriptor<String> AUTHORIZED_KEY_PATH = PropertyDescriptor.create(
            "auth.sshkey.path",
            (String)null,
            "The path to the authorized key file");

    /** . */
    private SshKeyDirectory directory = new SshKeyDirectory();

    @Override
    protected Iterable<PropertyDescriptor<?>> createConfigurationCapabilities() {
        return Arrays.<PropertyDescriptor<?>>asList(AUTHORIZED_KEY_PATH);
    }

    public String getName() {
        return "sshkey";
    }

    @Override
    public SshKeyAuthenticationPlugin getImplementation() {
        return this;
    }

    public Class<PublicKey> getCredentialType() {
        return PublicKey.class;
    }

    @Override
    public void init() {
        String authorizedKeyPath = getContext().getProperty(AUTHORIZED_KEY_PATH);
        if (authorizedKeyPath != null) {
            directory.setDirectory(authorizedKeyPath);
            try {
                directory.init();
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    public boolean authenticate(String username, PublicKey credential) throws Exception {
        if (directory.contains(credential)) {
            logger.debug("Authenticated " + username + " with public key " + credential);
            return true;
        } else {
            logger.debug(username + " with public key " + credential);
            return false;
        }
    }
}
