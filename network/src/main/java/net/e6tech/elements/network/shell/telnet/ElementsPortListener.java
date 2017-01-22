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
import net.wimpi.telnetd.BootException;
import net.wimpi.telnetd.net.ConnectionManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.Properties;

/**
 * Created by futeh.
 */
public class ElementsPortListener implements Runnable {
    private static Logger logger = Logger.getLogger();
    private String name;
    private String bindAddress;
    private int port;
    private int floodProctection;
    private ServerSocket serverSocket = null;
    private Thread thread;
    private ConnectionManager connectionManager;
    private boolean stopping = false;
    private boolean available;
    private static final String logmsg = "Listening to Port {0,number,integer} with a connectivity queue size of {1,number,integer}.";

    public ElementsPortListener(String name, int port, int floodprot, String bindAddress) {
        this.name = name;
        this.available = false;
        this.port = port;
        this.floodProctection = floodprot;
        this.bindAddress = bindAddress;
    }

    public String getName() {
        return this.name;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public void setAvailable(boolean b) {
        this.available = b;
    }

    public void start() {
        logger.debug("start()");
        this.thread = new Thread(this);
        this.thread.start();
        this.available = true;
    }

    public void stop() {
        logger.debug("stop()::" + this.toString());
        this.stopping = true;
        this.available = false;
        this.connectionManager.stop();

        try {
            this.serverSocket.close();
        } catch (IOException var3) {
            logger.error("stop()", var3);
        }

        try {
            this.thread.join();
        } catch (InterruptedException var2) {
            logger.error("stop()", var2);
        }

        logger.info("stop()::Stopped " + this.toString());
    }

    public void run() {
        try {
            if (bindAddress != null) this.serverSocket = new ServerSocket(this.port, this.floodProctection, InetAddress.getByName(bindAddress));
            else this.serverSocket = new ServerSocket(this.port, this.floodProctection);
            Object[] e = new Object[]{new Integer(this.port), new Integer(this.floodProctection)};
            logger.info(MessageFormat.format("Listening to Port {0,number,integer} with a connectivity queue size of {1,number,integer}.", e));

            do {
                try {
                    Socket ex = this.serverSocket.accept();
                    if(this.available) {
                        this.connectionManager.makeConnection(ex);
                    } else {
                        ex.close();
                    }
                } catch (SocketException var3) {
                    if(this.stopping) {
                        logger.debug("run(): ServerSocket closed by stop()");
                    } else {
                        logger.error("run()", var3);
                    }
                }
            } while(!this.stopping);
        } catch (IOException var4) {
            logger.error("run()", var4);
        }

        logger.debug("run(): returning.");
    }

    public ConnectionManager getConnectionManager() {
        return this.connectionManager;
    }

    public static ElementsPortListener createPortListener(String name, Properties settings) throws BootException {
        ElementsPortListener pl = null;

        try {
            int exc = Integer.parseInt(settings.getProperty(name + ".port"));
            int floodprot = Integer.parseInt(settings.getProperty(name + ".floodprotection"));
            if((new Boolean(settings.getProperty(name + ".secure"))).booleanValue()) {
                ;
            }
            String bindAddres = settings.getProperty(name + ".bind_address");

            pl = new ElementsPortListener(name, exc, floodprot, bindAddres);
        } catch (Exception var6) {
            logger.error("createPortListener()", var6);
            throw new BootException("Failure while creating PortListener instance:\n" + var6.getMessage());
        }

        if(pl.connectionManager == null) {
            pl.connectionManager = ConnectionManager.createConnectionManager(name, settings);

            try {
                pl.connectionManager.start();
            } catch (Exception var5) {
                logger.error("createPortListener()", var5);
                throw new BootException("Failure while starting ConnectionManager watchdog thread:\n" + var5.getMessage());
            }
        }

        return pl;
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
}
