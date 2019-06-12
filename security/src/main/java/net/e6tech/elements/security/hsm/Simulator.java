/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.security.hsm;

import net.e6tech.elements.common.logging.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Simulator {

    static Logger logger = Logger.getLogger();

    private ServerSocket serverSocket;
    private int port = 7000;
    private boolean stopped = true;
    private ExecutorService threadPool;

    public boolean isStopped() {
        return stopped;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void start() {
        if (threadPool == null) {
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(null, runnable, getClass().getSimpleName());
                thread.setName(getClass().getSimpleName() + "-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }
        Thread thread = new Thread(this::startServer);
        thread.start();
    }

    @SuppressWarnings({"squid:S134", "squid:S1141", "squid:S2589", "squid:S2189"})
    protected void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            stopped = false;
            while (!stopped) {
                final Socket socket = serverSocket.accept();
                threadPool.execute(()-> {
                    try {
                        process(socket.getInputStream(), socket.getOutputStream());
                        logger.info("{} client exited", getClass().getSimpleName());
                    } catch (Exception e) {
                        logger.trace(e.getMessage(), e);
                    }
                });
            }
        } catch (Exception th) {
            throw logger.systemException(th);
        }
    }

    public void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.suppress(e);
            } finally {
                stopped = true;
            }
        }
    }

    protected abstract void process(InputStream inputStream, OutputStream outputStream) throws IOException;
}
