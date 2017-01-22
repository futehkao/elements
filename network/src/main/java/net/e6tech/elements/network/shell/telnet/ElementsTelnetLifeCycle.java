/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.network.shell.telnet;

import net.e6tech.elements.common.logging.Logger;
import net.wimpi.telnetd.io.terminal.TerminalManager;
import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionManager;
import net.wimpi.telnetd.shell.ShellManager;
import net.wimpi.telnetd.util.StringUtil;
import org.crsh.plugin.PluginContext;
import org.crsh.telnet.term.TelnetLifeCycle;
import org.crsh.telnet.term.TermLifeCycle;
import org.crsh.vfs.Resource;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by futeh.
 */
public class ElementsTelnetLifeCycle extends TermLifeCycle {

    private final Logger log = Logger.getLogger(TelnetLifeCycle.class.getName());

    private Integer port;

    private String bindAddress;

    private List<ElementsPortListener> listeners;

    private static final ConcurrentHashMap<ConnectionManager, ElementsTelnetLifeCycle> map = new ConcurrentHashMap<>();

    private Resource config;

    static ElementsTelnetLifeCycle getLifeCycle(Connection conn) {
        return map.get(conn.getConnectionData().getManager());
    }

    public ElementsTelnetLifeCycle(PluginContext context) {
        super(context);
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public Resource getConfig() {
        return config;
    }

    public void setConfig(Resource config) {
        this.config = config;
    }

    @Override
    protected synchronized void doInit() throws Exception {
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(config.getContent()));

        //
        if (port != null) {
            log.debug("Explicit telnet port configuration with value " + port);
            props.put("std.port", port.toString());
        } else {
            log.debug( "Use default telnet port configuration " + props.getProperty("std.port"));
        }

        if (bindAddress != null) {
            props.put("std.bind_address", bindAddress);
        }

        //
        props.put("shell.simple.class", ElementsTelnetHandler.class.getName());
        ShellManager.createShellManager(props);

        //
        TerminalManager.createTerminalManager(props);

        //
        ArrayList<ElementsPortListener> listeners = new ArrayList<>();
        String[] listnames = StringUtil.split(props.getProperty("listeners"), ",");
        for (String listname : listnames) {
            ElementsPortListener listener = ElementsPortListener.createPortListener(listname, props);
            listeners.add(listener);
        }

        //
        this.listeners = listeners;

        // Start listeners
        for (ElementsPortListener listener : this.listeners) {
            listener.start();
            map.put(listener.getConnectionManager(), this);
        }
    }

    @Override
    protected synchronized void doDestroy() {
        log.info("Destroying telnet life cycle");
        if (listeners != null) {
            List<ElementsPortListener> listeners = this.listeners;
            this.listeners = null;
            for (ElementsPortListener listener : listeners) {
                try {
                    listener.stop();
                } catch (Exception ignore) {
                } finally {
                    map.remove(listener.getConnectionManager());
                }
            }
        }
    }
}
