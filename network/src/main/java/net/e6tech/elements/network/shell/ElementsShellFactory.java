/*
 * This is a replication of CRaSHShellFactory with changes to pass
 * user name in the session.
 *
 * The copyright of this file shall be that of CRaSHShellFactory.
 *
 */

package net.e6tech.elements.network.shell;

import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.PluginContext;
import org.crsh.shell.Shell;
import org.crsh.shell.ShellFactory;
import org.crsh.shell.impl.async.AsyncShell;
import org.crsh.shell.impl.command.CRaSH;

import java.security.Principal;
import java.util.Map;

/**
 * Created by futeh.
 */
public class ElementsShellFactory extends CRaSHPlugin<ShellFactory> implements ShellFactory {

    /** . */
    private CRaSH crash;

    public ElementsShellFactory() {
    }

    @Override
    public void init() {
        PluginContext context = getContext();
        crash = new CRaSH(context);
    }

    @Override
    public ShellFactory getImplementation() {
        return this;
    }

    public Shell create(Principal principal, boolean async) {
        Shell session = crash.createSession(principal);
        if (session instanceof Map) {
            ((Map) session).put("principal", principal);
        }
        if (async) {
            return new AsyncShell(getContext().getExecutor(), session);
        } else {
            return session;
        }
    }

    public Shell create(Principal principal) {
        return create(principal, true);
    }
}