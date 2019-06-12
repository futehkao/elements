/*
Copyright 2015-2019 Futeh Kao

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
package net.e6tech.elements.network.proxy;


import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Startable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketProxyServer implements Startable, Runnable {

    private static Logger logger = Logger.getLogger();

    private String remoteHost;
    private int remotePort;
    private int localPort;
    private ExecutorService threadPool;
    private volatile boolean stopped = false;

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void start() {
        if (threadPool == null) {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(group, runnable, "SocketProxyServer");
                thread.setName("SocketProxyServer-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }
        stopped = false;
        threadPool.execute(this);
    }

    public void stop() {
        stopped = true;
    }

    @SuppressWarnings("squid:S1141")
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(localPort)){
            while (!stopped) {
                try {
                    Transfer transfer = new Transfer(remoteHost, remotePort, serverSocket.accept(), threadPool);
                    threadPool.execute(transfer);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        } catch (Exception th) {
            throw logger.systemException(th);
        }
    }
}
