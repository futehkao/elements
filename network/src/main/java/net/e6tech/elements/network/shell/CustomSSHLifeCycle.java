/*
 * This is a replication of CRaSH SSHLifeCycle with changes to pass
 * user name in the session.
 *
 * The copyright of this file shall be that of SSHLifeCycle.
 *
 */

package net.e6tech.elements.network.shell;

import net.e6tech.elements.common.logging.Logger;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.session.ServerSession;
import org.crsh.auth.AuthenticationPlugin;
import org.crsh.plugin.PluginContext;
import org.crsh.shell.ShellFactory;
import org.crsh.ssh.term.CRaSHCommandFactory;
import org.crsh.ssh.term.SSHLifeCycle;
import org.crsh.ssh.term.scp.SCPCommandFactory;
import org.crsh.ssh.term.subsystem.SubsystemFactoryPlugin;

import java.nio.charset.Charset;
import java.security.PublicKey;
import java.util.ArrayList;

/**
 * Created by futeh.
 */
public class CustomSSHLifeCycle {

    private static Logger log = Logger.getLogger();

    private final PluginContext context;

    /** . */
    private final int port;

    /** . */
    private final int idleTimeout;

    /** . */
    private final int authTimeout;

    /** . */
    private final Charset encoding;

    /** . */
    private final KeyPairProvider keyPairProvider;

    /** . */
    private final ArrayList<AuthenticationPlugin> authenticationPlugins;

    /** . */
    private SshServer server;

    /** . */
    private Integer localPort;

    public CustomSSHLifeCycle(
            PluginContext context,
            Charset encoding,
            int port,
            int idleTimeout,
            int authTimeout,
            KeyPairProvider keyPairProvider,
            ArrayList<AuthenticationPlugin> authenticationPlugins) {
        this.authenticationPlugins = authenticationPlugins;
        this.context = context;
        this.encoding = encoding;
        this.port = port;
        this.idleTimeout = idleTimeout;
        this.authTimeout = authTimeout;
        this.keyPairProvider = keyPairProvider;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public int getPort() {
        return port;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public int getAuthTimeout() {
        return authTimeout;
    }


    /**
     * Returns the local part after the ssh server has been succesfully bound or null. This is useful when
     * the port is chosen at random by the system.
     *
     * @return the local port
     */
    public Integer getLocalPort() {
        return localPort;
    }

    public KeyPairProvider getKeyPairProvider() {
        return keyPairProvider;
    }

    public void init() {
        try {
            ShellFactory factory = context.getPlugin(ShellFactory.class);

            //
            SshServer server = SshServer.setUpDefaultServer();
            server.setPort(port);

            if (this.idleTimeout > 0) {
                server.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, String.valueOf(this.idleTimeout));
            }
            if (this.authTimeout > 0) {
                server.getProperties().put(ServerFactoryManager.AUTH_TIMEOUT, String.valueOf(this.authTimeout));
            }

            server.setShellFactory(new CRaSHCommandFactory(factory, encoding));
            server.setCommandFactory(new SCPCommandFactory(context));
            server.setKeyPairProvider(keyPairProvider);

            //
            ArrayList<NamedFactory<Command>> namedFactoryList = new ArrayList<NamedFactory<Command>>(0);
            for (SubsystemFactoryPlugin plugin : context.getPlugins(SubsystemFactoryPlugin.class)) {
                namedFactoryList.add(plugin.getFactory());
            }
            server.setSubsystemFactories(namedFactoryList);

            //
            for (AuthenticationPlugin authenticationPlugin : authenticationPlugins) {
                if (server.getPasswordAuthenticator() == null && authenticationPlugin.getCredentialType().equals(String.class)) {
                    server.setPasswordAuthenticator(new PasswordAuthenticator() {
                        public boolean authenticate(String _username, String _password, ServerSession session) {
                            if (genericAuthenticate(String.class, _username, _password)) {
                                // We store username and password in session for later reuse
                                session.setAttribute(SSHLifeCycle.USERNAME, _username);
                                session.setAttribute(SSHLifeCycle.PASSWORD, _password);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
                }

                if (server.getPublickeyAuthenticator() == null && authenticationPlugin.getCredentialType().equals(PublicKey.class)) {
                    server.setPublickeyAuthenticator(new PublickeyAuthenticator() {
                        public boolean authenticate(String username, PublicKey key, ServerSession session) {
                            if (genericAuthenticate(PublicKey.class, username, key)) {
                                session.setAttribute(SSHLifeCycle.USERNAME, username);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
                }
            }

            //
            log.info("About to start CRaSSHD");
            server.start();
            localPort = server.getPort();
            log.info("CRaSSHD started on port " + localPort);

            //
            this.server = server;
        }
        catch (Throwable e) {
            log.error("Could not start CRaSSHD", e);
        }
    }

    public void destroy() {
        if (server != null) {
            try {
                server.stop();
            }
            catch (InterruptedException e) {
                log.debug("Got an interruption when stopping server", e);
            }
        }
    }

    private <T> boolean genericAuthenticate(Class<T> type, String username, T credential) {
        for (AuthenticationPlugin authenticationPlugin : authenticationPlugins) {
            if (authenticationPlugin.getCredentialType().equals(type)) {
                try {
                    log.info("Using authentication plugin " + authenticationPlugin + " to authenticate user " + username);
                    @SuppressWarnings("unchecked")
                    AuthenticationPlugin<T> authPlugin = (AuthenticationPlugin<T>) authenticationPlugin;
                    if (authPlugin.authenticate(username, credential)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.error("Exception authenticating user " + username + " in authentication plugin: " + authenticationPlugin, e);
                }
            }
        }

        return false;
    }

}
